/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.sap.rfc;

import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerFunctionHandler;
import io.ballerina.lib.sap.ModuleUtils;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.lib.sap.parameterprocessor.ExportParameterProcessor;
import io.ballerina.lib.sap.parameterprocessor.ImportParameterProcessor;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.concurrent.StrandMetadata;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * JCo function handler that bridges inbound SAP RFC calls to the Ballerina {@code RfcService}
 * layer.
 * <p>
 * When JCo receives an RFC call from SAP, it invokes
 * {@link #handleRequest(JCoServerContext, JCoFunction)} on this handler. The handler:
 * <ol>
 *   <li>Extracts the import and table parameters from the JCo function object and packages
 *       them into an {@code RfcParameters} Ballerina record.</li>
 *   <li>Invokes the Ballerina service's {@code onCall(functionName, parameters)} method via
 *       {@link Runtime#callMethod}. This call blocks the current JCo worker thread until
 *       the Ballerina method returns its final value.</li>
 *   <li>Writes the return value back to the JCo function object's export and table parameter
 *       lists so that JCo can serialize them back to the SAP caller.</li>
 *   <li>If {@code onCall()} returns an {@code error}, throws an {@link AbapException} to
 *       signal the failure back to SAP.</li>
 * </ol>
 */
public class BallerinaRfcHandler implements JCoServerFunctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaRfcHandler.class);

    private final BObject service;
    private final Runtime runtime;

    public BallerinaRfcHandler(BObject service, Runtime runtime) {
        this.service = service;
        this.runtime = runtime;
    }

    /**
     * Entry point called by JCo for every inbound RFC call.
     * Blocks until the Ballerina {@code onCall()} method returns, then writes the response
     * back to the JCo function object.
     *
     * @param serverCtx the JCo server context for this call
     * @param function  the JCo function object carrying import/table/export parameter lists
     * @throws AbapException if {@code onCall()} returns an {@code error} or throws unexpectedly
     */
    @Override
    public void handleRequest(JCoServerContext serverCtx, JCoFunction function) throws AbapException {
        String functionName = function.getName();
        try {
            BMap<BString, Object> rfcParameters = buildRfcParameters(function);
            Object[] args = {StringUtils.fromString(functionName), rfcParameters};
            Object result = invokeOnCall(args);

            if (result instanceof BError bError) {
                if (!handleError(SAPErrorCreator.fromExecutionThrowable(
                        "onCall() returned an error for function '" + functionName + "'.", bError))) {
                    throw new AbapException("BALLERINA_ERROR", bError.getMessage());
                }
            } else if (result instanceof BMap) {
                @SuppressWarnings("unchecked")
                BMap<BString, Object> responseRecord = (BMap<BString, Object>) result;
                writeRfcResponse(function, responseRecord);
            } else if (result instanceof BXml) {
                writeXmlResponse(function, (BXml) result);
            }
            // null / nil return: leave export/table lists empty (valid for fire-and-forget RFCs)
        } catch (AbapException e) {
            throw e;
        } catch (BError e) {
            // Ballerina-level error (e.g. type mismatch in response record writing)
            BError paramError = SAPErrorCreator.createParameterError(
                    "Failed to write RFC response for function '" + functionName + "': " + e.getMessage());
            if (!handleError(paramError)) {
                throw new AbapException("BALLERINA_PARAMETER_ERROR", e.getMessage());
            }
        } catch (Throwable e) {
            BError execError = SAPErrorCreator.fromExecutionThrowable(
                    "RFC handler failed for function '" + functionName + "'.", e);
            if (!handleError(execError)) {
                throw new AbapException("BALLERINA_INTERNAL_ERROR", e.getMessage());
            }
        }
    }

    /**
     * Builds an {@code RfcParameters} Ballerina record from the JCo function's import and
     * table parameter lists.
     * <p>
     * The open record type is used for both the import parameters and individual table rows
     * because the JCo metadata — not the Ballerina type system — describes the field layout
     * at this point in the call chain.
     *
     * @param function the JCo function object
     * @return a Ballerina record with {@code importParameters} and/or {@code tableParameters} set
     */
    private static BMap<BString, Object> buildRfcParameters(JCoFunction function) {
        RecordType openRecord = TypeCreator.createRecordType(
                "RfcRecord", ModuleUtils.getModule(), 0, new HashMap<>(), null, true, 0);

        JCoParameterList importList = function.getImportParameterList();
        JCoParameterList tableList = function.getTableParameterList();

        BMap<BString, Object> rfcParameters = ValueCreator.createRecordValue(
                ModuleUtils.getModule(), "RfcParameters");

        if (importList != null) {
            BMap<BString, Object> importParams = ExportParameterProcessor.getMergedParams(
                    importList, null, openRecord, true);
            rfcParameters.put(SAPConstants.RFC_IMPORT_PARAMETERS, importParams);
        }
        if (tableList != null) {
            // getMergedParams with only the table list gives a flat BMap where each key is a
            // table parameter name and each value is a BArray of row records — this matches
            // the map<RfcRecord[]> shape expected by tableParameters.
            BMap<BString, Object> tableParams = ExportParameterProcessor.getMergedParams(
                    null, tableList, openRecord, true);
            rfcParameters.put(SAPConstants.RFC_TABLE_PARAMETERS, tableParams);
        }
        return rfcParameters;
    }

    /**
     * Writes a {@code RfcRecord} (or JSON-object) return value back to the JCo function object.
     * <p>
     * Fields whose Ballerina value is a {@link BArray} are routed to the JCo table parameter
     * list; all other fields are routed to the export parameter list.
     *
     * @param function       the JCo function object to write to
     * @param responseRecord the Ballerina record returned by {@code onCall()}
     */
    private static void writeRfcResponse(JCoFunction function, BMap<BString, Object> responseRecord) {
        JCoParameterList exportList = function.getExportParameterList();
        JCoParameterList tableList = function.getTableParameterList();

        BMap<BString, Object> scalarFields = ValueCreator.createMapValue(
                TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
        BMap<BString, Object> arrayFields = ValueCreator.createMapValue(
                TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));

        for (Map.Entry<BString, Object> entry : responseRecord.entrySet()) {
            Object value = entry.getValue();
            if (TypeUtils.getType(value).getTag() == TypeTags.ARRAY_TAG) {
                arrayFields.put(entry.getKey(), value);
            } else {
                scalarFields.put(entry.getKey(), value);
            }
        }

        if (exportList != null && !scalarFields.isEmpty()) {
            ImportParameterProcessor.setImportParams(exportList, scalarFields);
        }
        if (tableList != null && !arrayFields.isEmpty()) {
            ImportParameterProcessor.setTableParams(tableList, arrayFields);
        }
    }

    /**
     * Writes an {@code xml} return value back to the JCo function object.
     * <p>
     * Mapping RFC XML to JCo parameter lists requires knowledge of the function's metadata
     * (which element names are export parameters vs table rows). A full implementation will be
     * added in a future update when the required JCo function template lookup is integrated.
     * For now, this method logs a warning and leaves the parameter lists unchanged.
     *
     * @param function the JCo function object to write to
     * @param xmlValue the Ballerina XML value returned by {@code onCall()}
     */
    private static void writeXmlResponse(JCoFunction function, BXml xmlValue) {
        logger.warn("XML return from onCall() for function '{}' is not yet written back to JCo. "
                + "Use RfcRecord return type for fully supported response mapping.", function.getName());
    }

    /**
     * Invokes the Ballerina {@code onCall()} remote method on the attached service.
     * Uses concurrent dispatch when both the service and the method are declared {@code isolated}.
     *
     * @param args the arguments to pass: {@code {functionName, rfcParameters}}
     * @return the return value from the Ballerina method (may be a {@link BError} or {@code null})
     */
    private Object invokeOnCall(Object... args) {
        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_CALL);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, Map.of());
        return runtime.callMethod(service, SAPConstants.ON_CALL, metadata, args);
    }

    /**
     * Dispatches an error to the Ballerina {@code onError} remote method on the attached service.
     * <p>
     * If {@code onError} exists and returns {@code nil}, the error is considered handled and
     * this method returns {@code true} — the caller should not throw an {@code AbapException}.
     * If {@code onError} does not exist, or returns an error itself, this method returns
     * {@code false} — the caller should propagate the error back to SAP.
     *
     * @param error the Ballerina error to deliver to the service
     * @return {@code true} if the error was handled by {@code onError}; {@code false} otherwise
     */
    private boolean handleError(BError error) {
        MethodType onErrorMethod = null;
        MethodType[] methods = ((ObjectType) TypeUtils.getImpliedType(service.getOriginalType()))
                .getMethods();
        for (MethodType method : methods) {
            if (SAPConstants.ON_ERROR.equals(method.getName())) {
                onErrorMethod = method;
                break;
            }
        }
        if (onErrorMethod == null) {
            return false;
        }
        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, Map.of());
        try {
            Object result = runtime.callMethod(service, SAPConstants.ON_ERROR, metadata,
                    new Object[]{error, true});
            if (result instanceof BError onErrorResult) {
                logger.error("onError handler returned an error", onErrorResult);
                return false;
            }
            return true;
        } catch (Throwable thr) {
            logger.error("onError handler threw an unexpected error; suppressing to avoid re-entry.", thr);
            return false;
        }
    }
}

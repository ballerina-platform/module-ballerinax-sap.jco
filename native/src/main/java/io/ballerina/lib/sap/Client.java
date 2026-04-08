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

package io.ballerina.lib.sap;

import com.sap.conn.idoc.IDocDocumentList;
import com.sap.conn.idoc.IDocException;
import com.sap.conn.idoc.IDocFactory;
import com.sap.conn.idoc.IDocRepository;
import com.sap.conn.idoc.IDocXMLProcessor;
import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRepository;
import io.ballerina.lib.sap.dataproviders.SAPDestinationDataProvider;
import io.ballerina.lib.sap.parameterprocessor.ExportParameterProcessor;
import io.ballerina.lib.sap.parameterprocessor.ImportParameterProcessor;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native function implementations for the Ballerina SAP JCo {@code Client} object.
 * Each public static method in this class corresponds to a Ballerina extern function.
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    /**
     * Initializes a JCo RFC destination and pings the SAP system to verify connectivity.
     * On success the {@link JCoDestination} is stored as native data on the Ballerina client object
     * so that subsequent calls ({@link #execute} / {@link #sendIDoc}) can retrieve it without
     * a destination-manager lookup.
     *
     * @param client            the Ballerina {@code Client} object being initialized
     * @param destinationConfig a Ballerina record ({@code DestinationConfig} or advanced map) holding
     *                          JCo connection properties
     * @param destinationId     a unique name used to register the destination with the JCo framework
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object initializeClient(BObject client, BMap<BString, Object> destinationConfig,
                                          BString destinationId) {
        try {
            SAPDestinationDataProvider.registerIfAbsent();
            SAPDestinationDataProvider dp = SAPDestinationDataProvider.getInstance();
            dp.addDestinationConfig(destinationConfig, destinationId);
            JCoDestination destination = JCoDestinationManager.getDestination(destinationId.toString());
            destination.ping();
            logger.debug("JCo Client initialized");
            client.addNativeData(SAPConstants.RFC_DESTINATION, destination);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Exception e) {
            logger.error("Client initialization failed.");
            return SAPErrorCreator.createConfigError("Client initialization failed.", e);
        }
    }

    /**
     * Executes a Remote Function Call (RFC) on the SAP system.
     * <p>
     * Input parameters are routed by category: {@code importParameters} go to the JCo import
     * parameter list; {@code tableParameters} go to the JCo table parameter list. The response
     * merges both the export parameter list and the table parameter list into the return type,
     * which may be {@code xml}, {@code json}, or a Ballerina record type.
     *
     * @param client       the Ballerina {@code Client} object holding the JCo destination
     * @param functionName name of the RFC function module to call (must not be empty)
     * @param parameters   a Ballerina {@code RfcParameters} record with optional
     *                     {@code importParameters} and {@code tableParameters} sections
     * @param returnType   the expected Ballerina type of the RFC response
     * @return the merged RFC response, or a Ballerina {@code Error} on failure
     */
    @SuppressWarnings("unchecked")
    public static Object execute(BObject client, BString functionName,
                                 BMap<BString, Object> parameters, BTypedesc returnType) {
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            JCoRepository repository = destination.getRepository();
            if (functionName.toString().isEmpty()) {
                return SAPErrorCreator.createParameterError("Function name is empty.");
            }
            JCoFunction function = repository.getFunction(functionName.toString());
            if (function == null) {
                return SAPErrorCreator.createParameterError(
                        "RFC function '" + functionName + "' not found in SAP.");
            }

            // Route each input category to the correct JCo parameter list.
            BMap<BString, Object> importParameters = (BMap<BString, Object>)
                    parameters.get(StringUtils.fromString("importParameters"));
            BMap<BString, Object> tableParameters = (BMap<BString, Object>)
                    parameters.get(StringUtils.fromString("tableParameters"));

            if (importParameters != null) {
                JCoParameterList importList = function.getImportParameterList();
                if (importList == null) {
                    return SAPErrorCreator.createParameterError("RFC function '" + functionName
                            + "' has no import parameters but importParameters were provided.");
                }
                ImportParameterProcessor.setImportParams(importList, importParameters);
            }
            if (tableParameters != null) {
                JCoParameterList tableList = function.getTableParameterList();
                if (tableList == null) {
                    return SAPErrorCreator.createParameterError("RFC function '" + functionName
                            + "' has no table parameters but tableParameters were provided.");
                }
                ImportParameterProcessor.setTableParams(tableList, tableParameters);
            }

            function.execute(destination);

            // Merge export params and table params into a single response.
            JCoParameterList exportList = function.getExportParameterList();
            JCoParameterList tableOutputList = function.getTableParameterList();

            int tag = returnType.getDescribingType().getTag();
            if (tag == TypeTags.XML_TAG) {
                return buildXmlResponse(exportList, tableOutputList);
            } else if (tag == TypeTags.JSON_TAG) {
                return buildJsonResponse(exportList, tableOutputList);
            } else if (tag == TypeTags.RECORD_TYPE_TAG) {
                RecordType outputRecordType = (RecordType) returnType.getDescribingType();
                boolean isRestFieldsAllowed = outputRecordType.getRestFieldType() != null;
                return ExportParameterProcessor.getMergedParams(
                        exportList, tableOutputList, outputRecordType, isRestFieldsAllowed);
            } else {
                return SAPErrorCreator.createParameterError(
                        "Unsupported return type: " + returnType.getDescribingType().getName());
            }
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            logger.error("RFC execution failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Throwable e) {
            logger.error("Unexpected error during RFC execution.");
            return SAPErrorCreator.fromThrowable("RFC execution failed.", e);
        }
    }

    private static Object buildXmlResponse(JCoParameterList exportList, JCoParameterList tableList) {
        if (exportList == null && tableList == null) {
            return ValueCreator.createXmlValue("<result/>");
        }
        if (tableList == null) {
            return ValueCreator.createXmlValue(exportList.toXML());
        }
        if (exportList == null) {
            return ValueCreator.createXmlValue(tableList.toXML());
        }
        return ValueCreator.createXmlValue("<result>" + exportList.toXML() + tableList.toXML() + "</result>");
    }

    private static Object buildJsonResponse(JCoParameterList exportList, JCoParameterList tableList) {
        if (exportList == null && tableList == null) {
            return JsonUtils.parse("{}");
        }
        if (tableList == null) {
            return JsonUtils.parse(exportList.toJSON());
        }
        if (exportList == null) {
            return JsonUtils.parse(tableList.toJSON());
        }
        String exportJson = exportList.toJSON().trim();
        String tableJson = tableList.toJSON().trim();
        String merged;
        if (exportJson.equals("{}")) {
            merged = tableJson;
        } else if (tableJson.equals("{}")) {
            merged = exportJson;
        } else {
            merged = exportJson.substring(0, exportJson.lastIndexOf('}'))
                    + "," + tableJson.substring(tableJson.indexOf('{') + 1);
        }
        return JsonUtils.parse(merged);
    }

    /**
     * Sends an IDoc to SAP using the Transactional RFC (tRFC) protocol.
     * <p>
     * A TID (Transaction ID) is created on the destination, the IDoc XML is parsed and sent
     * via {@link com.sap.conn.idoc.jco.JCoIDoc#send}, and the TID is then confirmed so that
     * SAP does not re-deliver the document on a subsequent connection.
     *
     * @param client   the Ballerina {@code Client} object holding the JCo destination
     * @param iDoc     the IDoc payload as an XML value
     * @param iDocType a single-character IDoc version identifier (e.g. {@code '3'} for IDoc type 3)
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object sendIDoc(BObject client, BXml iDoc, BString iDocType) {
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            String iDocXML = iDoc.toString();
            String iDocTypeStr = iDocType.toString();
            if (iDocTypeStr.length() != 1) {
                return SAPErrorCreator.createParameterError(
                        "iDocType must be a single character, got: \"" + iDocTypeStr + "\"");
            }
            char version = iDocTypeStr.charAt(0);

            String tid = destination.createTID();
            logger.debug("TID created: {}", tid);

            IDocFactory iDocFactory = JCoIDoc.getIDocFactory();
            IDocRepository iDocRepository = JCoIDoc.getIDocRepository(destination);
            IDocXMLProcessor processor = iDocFactory.getIDocXMLProcessor();
            IDocDocumentList iDocList = processor.parse(iDocRepository, iDocXML);

            JCoIDoc.send(iDocList, version, destination, tid);
            destination.confirmTID(tid);
            logger.debug("IDoc sent successfully with TID: {}", tid);
            return null;
        } catch (JCoException e) {
            logger.error("IDoc send failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (IDocException e) {
            logger.error("IDoc send failed.");
            return SAPErrorCreator.fromIDocException(e);
        }
    }

}

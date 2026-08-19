/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.sap;

import com.sap.conn.jco.JCo;
import com.sap.conn.jco.JCoBackgroundUnitAttributes;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoFunctionUnit;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRepository;
import com.sap.conn.jco.JCoUnitIdentifier;
import io.ballerina.lib.sap.parameterprocessor.ImportParameterProcessor;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native implementations of the transactional RFC operations on the Ballerina SAP JCo
 * {@code Client}: tRFC and qRFC function calls, and bgRFC units of work.
 * <p>
 * All three protocols give the caller a guarantee that a plain {@code execute} cannot: the call
 * is applied exactly once, even if the caller retries because it never learned the outcome of an
 * earlier attempt. The guarantee is carried by an identifier — a transaction ID (TID) for
 * tRFC/qRFC, a unit ID for bgRFC — which SAP remembers until the caller confirms it. Each
 * operation therefore returns or accepts that identifier rather than hiding it.
 */
public final class Transactions {

    private static final Logger logger = LoggerFactory.getLogger(Transactions.class);

    private Transactions() {
    }

    /**
     * Creates a transaction ID on the SAP system.
     *
     * @param client the Ballerina {@code Client} object
     * @return the TID as a Ballerina string, or a Ballerina {@code Error} on failure
     */
    public static Object createTid(BObject client) {
        try {
            JCoDestination destination = destinationOf(client);
            return StringUtils.fromString(destination.createTID());
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.fromJCoException(e);
        }
    }

    /**
     * Confirms a transaction ID so that the SAP system can discard its record of it.
     *
     * @param client the Ballerina {@code Client} object
     * @param tid    the TID to confirm
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object confirmTid(BObject client, BString tid) {
        String tidStr = tid.getValue();
        try {
            validateTid(tidStr);
            destinationOf(client).confirmTID(tidStr);
            return null;
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.createTransactionError(
                    "Failed to confirm TID '" + tidStr + "'.", e, tidStr, null);
        }
    }

    /**
     * Executes a function module as a transactional RFC (tRFC).
     *
     * @param client       the Ballerina {@code Client} object
     * @param functionName name of the RFC-enabled function module
     * @param parameters   the {@code RfcParameters} record holding import and table parameters
     * @param tid          a caller-supplied TID, or {@code null} to create one
     * @param autoConfirm  whether to confirm the TID after a successful send
     * @return the TID the call was sent under, or a Ballerina {@code Error} on failure
     */
    public static Object sendTRfc(BObject client, BString functionName, BMap<BString, Object> parameters,
                                  Object tid, boolean autoConfirm) {
        return send(client, functionName, parameters, tid, null, autoConfirm);
    }

    /**
     * Executes a function module as a queued RFC (qRFC) on the given inbound queue.
     *
     * @param client       the Ballerina {@code Client} object
     * @param functionName name of the RFC-enabled function module
     * @param queueName    the SAP inbound queue that serialises the calls
     * @param parameters   the {@code RfcParameters} record holding import and table parameters
     * @param tid          a caller-supplied TID, or {@code null} to create one
     * @param autoConfirm  whether to confirm the TID after a successful send
     * @return the TID the call was sent under, or a Ballerina {@code Error} on failure
     */
    public static Object sendQRfc(BObject client, BString functionName, BString queueName,
                                  BMap<BString, Object> parameters, Object tid, boolean autoConfirm) {
        String queue = queueName.getValue();
        if (queue.isEmpty()) {
            return SAPErrorCreator.createParameterError("Queue name is empty.");
        }
        return send(client, functionName, parameters, tid, queue, autoConfirm);
    }

    private static Object send(BObject client, BString functionName, BMap<BString, Object> parameters,
                               Object tid, String queueName, boolean autoConfirm) {
        String tidStr = null;
        try {
            if (tid instanceof BString) {
                validateTid(((BString) tid).getValue());
            }
            JCoDestination destination = destinationOf(client);
            JCoFunction function = prepareFunction(destination, functionName, parameters);

            tidStr = (tid instanceof BString) ? ((BString) tid).getValue() : destination.createTID();
            if (queueName == null) {
                function.execute(destination, tidStr);
            } else {
                function.execute(destination, tidStr, queueName);
            }
            if (autoConfirm) {
                confirmQuietly(destination, tidStr);
            }
            return StringUtils.fromString(tidStr);
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            String protocol = queueName == null ? "tRFC" : "qRFC";
            return SAPErrorCreator.createTransactionError(
                    protocol + " call to '" + functionName + "' failed.", e, tidStr, null);
        }
    }

    // A confirmation failure does not undo the delivery: the call is already recorded in SAP, and
    // retrying it would not create a duplicate. Failing the operation here would push the caller
    // into re-sending something that already succeeded, so this is logged rather than surfaced.
    private static void confirmQuietly(JCoDestination destination, String tid) {
        try {
            destination.confirmTID(tid);
        } catch (JCoException e) {
            logger.warn("Call was delivered, but confirming TID {} failed. SAP will discard the TID "
                    + "record on its own schedule. Cause: {}", tid, e.getMessage());
        }
    }

    /**
     * Commits one or more function calls to SAP as a single bgRFC unit of work.
     *
     * @param client        the Ballerina {@code Client} object
     * @param functionCalls the {@code FunctionCall} records making up the unit
     * @param unitConfig    the {@code BgRfcUnitConfig} record
     * @return a {@code BgRfcUnitInfo} record, or a Ballerina {@code Error} on failure
     */
    @SuppressWarnings("unchecked")
    public static Object sendBgRfcUnit(BObject client, BArray functionCalls,
                                       BMap<BString, Object> unitConfig) {
        String unitId = null;
        try {
            if (functionCalls.size() == 0) {
                return SAPErrorCreator.createParameterError(
                        "A bgRFC unit must contain at least one function call.");
            }
            JCoDestination destination = destinationOf(client);

            Object configuredId = unitConfig.get(SAPConstants.BGRFC_UNIT_ID);
            if (configuredId != null) {
                validateUnitId(configuredId.toString());
            }
            JCoBackgroundUnitAttributes attributes = buildAttributes(unitConfig);
            JCoFunctionUnit unit = (configuredId == null)
                    ? JCo.createFunctionUnit(attributes)
                    : JCo.createFunctionUnit(configuredId.toString(), attributes);

            BArray queueNames = (BArray) unitConfig.get(SAPConstants.BGRFC_QUEUE_NAMES);
            if (queueNames != null) {
                for (int i = 0; i < queueNames.size(); i++) {
                    String queue = queueNames.getBString(i).getValue();
                    if (!unit.addQueueName(queue)) {
                        return SAPErrorCreator.createParameterError(
                                "Queue name '" + queue + "' was rejected by JCo.");
                    }
                }
            }

            for (int i = 0; i < functionCalls.size(); i++) {
                BMap<BString, Object> call = (BMap<BString, Object>) functionCalls.get(i);
                BString name = (BString) call.get(SAPConstants.BGRFC_FUNCTION_NAME);
                BMap<BString, Object> params = (BMap<BString, Object>)
                        call.get(SAPConstants.BGRFC_PARAMETERS);
                unit.addFunction(prepareFunction(destination, name, params));
            }

            JCoUnitIdentifier identifier = unit.getIdentifier();
            unitId = identifier.getID();
            unit.commit(destination);
            return createUnitInfo(unitId, identifier.getType());
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.createTransactionError("bgRFC unit commit failed.", e, null, unitId);
        }
    }

    /**
     * Reads the processing state of a bgRFC unit from the SAP system.
     *
     * @param client   the Ballerina {@code Client} object
     * @param unitInfo the {@code BgRfcUnitInfo} record identifying the unit
     * @return the state name as a Ballerina string, or a Ballerina {@code Error} on failure
     */
    public static Object getBgRfcUnitState(BObject client, BMap<BString, Object> unitInfo) {
        String unitId = unitField(unitInfo, SAPConstants.BGRFC_UNIT_ID);
        try {
            JCoUnitIdentifier identifier = toIdentifier(unitInfo);
            return StringUtils.fromString(destinationOf(client).getFunctionUnitState(identifier).name());
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.createTransactionError(
                    "Failed to read the state of bgRFC unit '" + unitId + "'.", e, null, unitId);
        }
    }

    /**
     * Confirms a processed bgRFC unit so that SAP can delete its status record.
     *
     * @param client   the Ballerina {@code Client} object
     * @param unitInfo the {@code BgRfcUnitInfo} record identifying the unit
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object confirmBgRfcUnit(BObject client, BMap<BString, Object> unitInfo) {
        String unitId = unitField(unitInfo, SAPConstants.BGRFC_UNIT_ID);
        try {
            destinationOf(client).confirmFunctionUnit(toIdentifier(unitInfo));
            return null;
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.createTransactionError(
                    "Failed to confirm bgRFC unit '" + unitId + "'.", e, null, unitId);
        }
    }

    /** Looks the function up and binds its import and table parameters, mirroring {@code execute}. */
    @SuppressWarnings("unchecked")
    private static JCoFunction prepareFunction(JCoDestination destination, BString functionName,
                                               BMap<BString, Object> parameters) throws JCoException {
        String name = functionName.getValue();
        if (name.isEmpty()) {
            throw SAPErrorCreator.createParameterError("Function name is empty.");
        }
        JCoRepository repository = destination.getRepository();
        JCoFunction function = repository.getFunction(name);
        if (function == null) {
            throw SAPErrorCreator.createParameterError("RFC function '" + name + "' not found in SAP.");
        }
        if (parameters == null) {
            return function;
        }

        BMap<BString, Object> importParameters =
                (BMap<BString, Object>) parameters.get(SAPConstants.RFC_IMPORT_PARAMETERS);
        BMap<BString, Object> tableParameters =
                (BMap<BString, Object>) parameters.get(SAPConstants.RFC_TABLE_PARAMETERS);

        if (importParameters != null) {
            JCoParameterList importList = function.getImportParameterList();
            if (importList == null) {
                throw SAPErrorCreator.createParameterError("RFC function '" + name
                        + "' has no import parameters but importParameters were provided.");
            }
            ImportParameterProcessor.setImportParams(importList, importParameters);
        }
        if (tableParameters != null) {
            JCoParameterList tableList = function.getTableParameterList();
            if (tableList == null) {
                throw SAPErrorCreator.createParameterError("RFC function '" + name
                        + "' has no table parameters but tableParameters were provided.");
            }
            ImportParameterProcessor.setTableParams(tableList, tableParameters);
        }
        return function;
    }

    private static BMap<BString, Object> createUnitInfo(String unitId, JCoUnitIdentifier.Type type) {
        BMap<BString, Object> info = ValueCreator.createRecordValue(
                ModuleUtils.getModule(), SAPConstants.BGRFC_UNIT_INFO_RECORD);
        info.put(SAPConstants.BGRFC_UNIT_ID, StringUtils.fromString(unitId));
        info.put(SAPConstants.BGRFC_UNIT_TYPE, StringUtils.fromString(
                type == JCoUnitIdentifier.Type.TYPE_Q ? SAPConstants.BGRFC_TYPE_Q : SAPConstants.BGRFC_TYPE_T));
        return info;
    }

    private static JCoUnitIdentifier toIdentifier(BMap<BString, Object> unitInfo) {
        String unitId = unitField(unitInfo, SAPConstants.BGRFC_UNIT_ID);
        validateUnitId(unitId);
        JCoUnitIdentifier.Type type =
                SAPConstants.BGRFC_TYPE_Q.equals(unitField(unitInfo, SAPConstants.BGRFC_UNIT_TYPE))
                        ? JCoUnitIdentifier.Type.TYPE_Q
                        : JCoUnitIdentifier.Type.TYPE_T;
        return JCo.createUnitIdentifier(unitId, type);
    }

    private static JCoBackgroundUnitAttributes buildAttributes(BMap<BString, Object> unitConfig) {
        JCoBackgroundUnitAttributes attributes = JCo.createBackgroundUnitAttributes();
        attributes.setLock(booleanConfig(unitConfig, SAPConstants.BGRFC_LOCK));
        attributes.setUnitHistory(booleanConfig(unitConfig, SAPConstants.BGRFC_UNIT_HISTORY));
        attributes.setKernelTrace(booleanConfig(unitConfig, SAPConstants.BGRFC_KERNEL_TRACE));
        attributes.setCommitCheckOn(booleanConfig(unitConfig, SAPConstants.BGRFC_COMMIT_CHECK));
        Object programName = unitConfig.get(SAPConstants.BGRFC_PROGRAM_NAME);
        if (programName != null) {
            attributes.setProgramName(programName.toString());
        }
        Object transactionCode = unitConfig.get(SAPConstants.BGRFC_TRANSACTION_CODE);
        if (transactionCode != null) {
            attributes.setTransactionCode(transactionCode.toString());
        }
        return attributes;
    }

    private static boolean booleanConfig(BMap<BString, Object> config, BString key) {
        Object value = config.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String unitField(BMap<BString, Object> unitInfo, BString field) {
        Object value = unitInfo.get(field);
        return value == null ? "" : value.toString();
    }

    private static JCoDestination destinationOf(BObject client) {
        JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
        if (destination == null) {
            throw SAPErrorCreator.createConfigError("Client is closed or not initialized.");
        }
        return destination;
    }

    // JCo splits a TID positionally into four fixed-width character fields, so a shorter value
    // raises a raw StringIndexOutOfBoundsException and a longer one is silently truncated —
    // which would break exactly-once, because SAP would record a different identifier than the
    // caller believes it used. The content is deliberately not restricted: SAP stores the TID
    // components as CHAR, so an application may derive a TID from its own idempotency key.
    private static void validateTid(String tid) {
        if (tid == null || tid.length() != SAPConstants.TID_LENGTH) {
            throw SAPErrorCreator.createParameterError("Invalid transaction ID '" + tid
                    + "'. A TID must be exactly " + SAPConstants.TID_LENGTH + " characters long.");
        }
    }

    // Unit IDs, unlike TIDs, really are 16 bytes in hexadecimal — JCo documents that and rejects
    // bad values in createUnitIdentifier. JCo.createFunctionUnit however accepts any length, so a
    // malformed ID would otherwise surface only later when the unit is queried.
    private static void validateUnitId(String unitId) {
        if (!isHexOfLength(unitId, SAPConstants.UNIT_ID_LENGTH)) {
            throw SAPErrorCreator.createParameterError("Invalid bgRFC unit ID '" + unitId
                    + "'. A unit ID must be a " + SAPConstants.UNIT_ID_LENGTH
                    + "-character hexadecimal string.");
        }
    }

    private static boolean isHexOfLength(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}

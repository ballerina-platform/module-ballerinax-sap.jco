/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import com.sap.conn.jco.JCo;
import com.sap.conn.jco.JCoBackgroundUnitAttributes;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoFunctionUnit;
import com.sap.conn.jco.JCoRepository;
import com.sap.conn.jco.JCoUnitIdentifier;
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
 * Implements transactional RFC protocols on top of JCo: tRFC (exactly-once), qRFC (exactly-once
 * in order) and bgRFC units of type T and Q.
 */
public final class Transactions {

    private static final Logger logger = LoggerFactory.getLogger(Transactions.class);

    private Transactions() {
    }

    public static Object createTid(BObject client) {
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            return StringUtils.fromString(destination.createTID());
        } catch (JCoException e) {
            return SAPErrorCreator.fromJCoException(e);
        }
    }

    public static Object confirmTid(BObject client, BString tid) {
        try {
            validateTid(tid.toString());
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            destination.confirmTID(tid.toString());
            return null;
        } catch (BError e) {
            return e;
        } catch (JCoException e) {
            return SAPErrorCreator.transactionError("Failed to confirm TID '" + tid + "': " + e.getMessage(),
                    e, tid.toString(), null);
        }
    }

    public static Object sendTRfc(BObject client, BString functionName, BMap<BString, Object> importParams,
                                  Object tid, boolean autoConfirm) {
        return sendTransactional(client, functionName, importParams, tid, null, autoConfirm);
    }

    public static Object sendQRfc(BObject client, BString functionName, BString queueName,
                                  BMap<BString, Object> importParams, Object tid, boolean autoConfirm) {
        String queue = queueName.toString();
        if (queue.isEmpty()) {
            return SAPErrorCreator.fromBError("Queue name is empty.", null);
        }
        return sendTransactional(client, functionName, importParams, tid, queue, autoConfirm);
    }

    private static Object sendTransactional(BObject client, BString functionName,
                                            BMap<BString, Object> importParams, Object tid, String queueName,
                                            boolean autoConfirm) {
        String tidStr = null;
        try {
            if (tid != null) {
                validateTid(tid.toString());
            }
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            JCoFunction function = lookupFunction(destination, functionName);
            boolean setNull = (boolean) client.getNativeData(SAPConstants.SET_NULL);
            ParameterProcessor.setParameters(function, importParams, setNull);

            tidStr = tid == null ? destination.createTID() : tid.toString();
            if (queueName == null) {
                function.execute(destination, tidStr);
            } else {
                function.execute(destination, tidStr, queueName);
            }
            if (autoConfirm) {
                confirmQuietly(destination, tidStr);
            }
            return StringUtils.fromString(tidStr);
        } catch (JCoException e) {
            String protocol = queueName == null ? "tRFC" : "qRFC";
            return SAPErrorCreator.transactionError(protocol + " execution failed for function '" + functionName +
                    "': " + e.getMessage(), e, tidStr, null);
        } catch (BError e) {
            return e;
        } catch (Exception e) {
            return SAPErrorCreator.createError("Failed to send transactional function call.", e);
        }
    }

    // A failure to confirm does not undo the delivery: the LUW is already recorded in SAP and
    // retrying the same TID would not create duplicates. Hence this is logged, not surfaced.
    private static void confirmQuietly(JCoDestination destination, String tid) {
        try {
            destination.confirmTID(tid);
        } catch (JCoException e) {
            logger.warn("Function call was delivered, but confirming TID {} failed. SAP will clean up the "
                    + "TID record eventually. Cause: {}", tid, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Object sendBgRfcUnit(BObject client, BArray functionCalls, BMap<BString, Object> unitConfig) {
        String unitIdStr = null;
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            boolean setNull = (boolean) client.getNativeData(SAPConstants.SET_NULL);
            if (functionCalls.size() == 0) {
                return SAPErrorCreator.fromBError("A bgRFC unit must contain at least one function call.", null);
            }

            JCoBackgroundUnitAttributes attributes = buildAttributes(unitConfig);
            Object configuredId = unitConfig.get(StringUtils.fromString(SAPConstants.UNIT_ID));
            if (configuredId != null) {
                validateUnitId(configuredId.toString());
            }
            JCoFunctionUnit unit = configuredId == null ? JCo.createFunctionUnit(attributes)
                    : JCo.createFunctionUnit(configuredId.toString(), attributes);

            BArray queueNames = (BArray) unitConfig.get(StringUtils.fromString(SAPConstants.QUEUE_NAMES));
            if (queueNames != null) {
                for (int i = 0; i < queueNames.size(); i++) {
                    unit.addQueueName(queueNames.getBString(i).toString());
                }
            }

            for (int i = 0; i < functionCalls.size(); i++) {
                BMap<BString, Object> call = (BMap<BString, Object>) functionCalls.get(i);
                BString name = (BString) call.get(StringUtils.fromString(SAPConstants.FUNCTION_NAME));
                BMap<BString, Object> params =
                        (BMap<BString, Object>) call.get(StringUtils.fromString(SAPConstants.IMPORT_PARAMS));
                JCoFunction function = lookupFunction(destination, name);
                if (params != null) {
                    ParameterProcessor.setParameters(function, params, setNull);
                }
                unit.addFunction(function);
            }

            JCoUnitIdentifier identifier = unit.getIdentifier();
            unitIdStr = identifier.getID();
            unit.commit(destination);

            BMap<BString, Object> unitInfo = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    SAPConstants.BGRFC_UNIT_INFO_RECORD);
            unitInfo.put(StringUtils.fromString(SAPConstants.UNIT_ID_FIELD), StringUtils.fromString(unitIdStr));
            unitInfo.put(StringUtils.fromString(SAPConstants.UNIT_TYPE_FIELD),
                    StringUtils.fromString(identifier.getType() == JCoUnitIdentifier.Type.TYPE_Q
                            ? SAPConstants.Q : SAPConstants.T));
            return unitInfo;
        } catch (JCoException e) {
            return SAPErrorCreator.transactionError("bgRFC unit commit failed: " + e.getMessage(), e,
                    null, unitIdStr);
        } catch (BError e) {
            return e;
        } catch (Exception e) {
            return SAPErrorCreator.createError("Failed to send bgRFC unit.", e);
        }
    }

    public static Object getBgRfcUnitState(BObject client, BMap<BString, Object> unitInfo) {
        String unitId = unitField(unitInfo, SAPConstants.UNIT_ID_FIELD);
        try {
            validateUnitId(unitId);
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            JCoUnitIdentifier identifier = JCo.createUnitIdentifier(unitId,
                    toUnitType(unitField(unitInfo, SAPConstants.UNIT_TYPE_FIELD)));
            return StringUtils.fromString(destination.getFunctionUnitState(identifier).name());
        } catch (JCoException e) {
            return SAPErrorCreator.transactionError("Failed to get bgRFC unit state for unit '" + unitId +
                    "': " + e.getMessage(), e, null, unitId);
        } catch (BError e) {
            return e;
        } catch (Exception e) {
            return SAPErrorCreator.createError("Failed to get bgRFC unit state.", e);
        }
    }

    public static Object confirmBgRfcUnit(BObject client, BMap<BString, Object> unitInfo) {
        String unitId = unitField(unitInfo, SAPConstants.UNIT_ID_FIELD);
        try {
            validateUnitId(unitId);
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            JCoUnitIdentifier identifier = JCo.createUnitIdentifier(unitId,
                    toUnitType(unitField(unitInfo, SAPConstants.UNIT_TYPE_FIELD)));
            destination.confirmFunctionUnit(identifier);
            return null;
        } catch (JCoException e) {
            return SAPErrorCreator.transactionError("Failed to confirm bgRFC unit '" + unitId + "': " +
                    e.getMessage(), e, null, unitId);
        } catch (BError e) {
            return e;
        } catch (Exception e) {
            return SAPErrorCreator.createError("Failed to confirm bgRFC unit.", e);
        }
    }

    // A TID is split positionally by JCo into four fixed-width character fields, so anything
    // shorter than 24 characters raises a raw StringIndexOutOfBoundsException, and anything
    // longer is silently truncated - which would break exactly-once, because SAP would then
    // record a different identifier than the caller believes it used. The content itself is
    // not restricted: SAP stores the TID components as CHAR, so an application is free to
    // derive a TID from its own idempotency key.
    private static void validateTid(String tid) {
        if (tid == null || tid.length() != SAPConstants.TID_LENGTH) {
            throw SAPErrorCreator.fromBError("Invalid transaction ID '" + tid + "'. A TID must be exactly " +
                    SAPConstants.TID_LENGTH + " characters long, as returned by createTid().", null);
        }
    }

    // Unit IDs, unlike TIDs, are genuinely 16 bytes in hexadecimal - JCo documents that and
    // rejects bad values in createUnitIdentifier. JCo.createFunctionUnit however accepts any
    // length without complaint, so a wrong ID would only surface later when the unit is
    // queried; validating here keeps that failure at the call that caused it.
    private static void validateUnitId(String unitId) {
        if (!isHexOfLength(unitId, SAPConstants.UNIT_ID_LENGTH)) {
            throw SAPErrorCreator.fromBError("Invalid bgRFC unit ID '" + unitId + "'. A unit ID must be a " +
                    SAPConstants.UNIT_ID_LENGTH + "-character hexadecimal string.", null);
        }
    }

    private static boolean isHexOfLength(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static String unitField(BMap<BString, Object> unitInfo, String field) {
        Object value = unitInfo.get(StringUtils.fromString(field));
        return value == null ? "" : value.toString();
    }

    private static JCoFunction lookupFunction(JCoDestination destination, BString functionName)
            throws JCoException {
        String name = functionName.toString();
        if (name.isEmpty()) {
            throw SAPErrorCreator.fromBError("Function name is empty", null);
        }
        JCoRepository repository = destination.getRepository();
        JCoFunction function = repository.getFunction(name);
        if (function == null) {
            throw SAPErrorCreator.fromBError("RFC function '" + name + "' not found in SAP.", null);
        }
        return function;
    }

    private static JCoUnitIdentifier.Type toUnitType(String unitType) {
        return SAPConstants.Q.equals(unitType) ? JCoUnitIdentifier.Type.TYPE_Q
                : JCoUnitIdentifier.Type.TYPE_T;
    }

    private static JCoBackgroundUnitAttributes buildAttributes(BMap<BString, Object> unitConfig) {
        JCoBackgroundUnitAttributes attributes = JCo.createBackgroundUnitAttributes();
        attributes.setLock(getBooleanConfig(unitConfig, SAPConstants.LOCK));
        attributes.setUnitHistory(getBooleanConfig(unitConfig, SAPConstants.UNIT_HISTORY));
        attributes.setKernelTrace(getBooleanConfig(unitConfig, SAPConstants.KERNEL_TRACE));
        attributes.setCommitCheckOn(getBooleanConfig(unitConfig, SAPConstants.COMMIT_CHECK));
        Object programName = unitConfig.get(StringUtils.fromString(SAPConstants.PROGRAM_NAME));
        if (programName != null) {
            attributes.setProgramName(programName.toString());
        }
        Object transactionCode = unitConfig.get(StringUtils.fromString(SAPConstants.TRANSACTION_CODE));
        if (transactionCode != null) {
            attributes.setTransactionCode(transactionCode.toString());
        }
        return attributes;
    }

    private static boolean getBooleanConfig(BMap<BString, Object> config, String key) {
        Object value = config.get(StringUtils.fromString(key));
        return value instanceof Boolean && (Boolean) value;
    }
}

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
import com.sap.conn.jco.JCoStructure;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.StructureType;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static Object initializeClient(BObject client, BMap<BString, Object> jcoDestinationConfig,
                                          BString destinationId, boolean setImportParamNull) {
        try {
            BallerinaDestinationDataProvider dp = new BallerinaDestinationDataProvider();
            com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(dp);
            dp.addDestination(jcoDestinationConfig, destinationId);
            JCoDestination destination = JCoDestinationManager.getDestination(destinationId.toString());
            destination.ping();
            logger.debug("JCo Client initialized");
            client.addNativeData(SAPConstants.RFC_DESTINATION, destination);
            client.addNativeData(SAPConstants.SET_NULL, setImportParamNull);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Exception e) {
            logger.error("Client initialization failed.");
            return SAPErrorCreator.createError("Client initialization failed.", e);
        }
    }

    public static Object execute(BObject client, BString functionName,
                                  BMap<BString, Object> inputParams, BTypedesc outputParamType) {
            try {
                StructureType outputParamsStructType = (StructureType) outputParamType.getDescribingType();

                JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
                JCoRepository repository = destination.getRepository();
                if (functionName.toString().isEmpty()) {
                    return SAPErrorCreator.fromBError("Function name is empty", null);
                }
                JCoFunction function = repository.getFunction(functionName.toString());
                if (function == null) {
                    return SAPErrorCreator.fromBError("RFC function '" + functionName + "' not found in SAP."
                            , null);
                }

                JCoParameterList importParams = function.getImportParameterList();
                boolean setNull = (boolean) client.getNativeData(SAPConstants.SET_NULL);
                setImportParams(importParams, inputParams, setNull);

                function.execute(destination);

                JCoParameterList exportParams = function.getExportParameterList();
                JCoStructure exportStructure = exportParams.getStructure(SAPConstants.RETURN);

                // This checks if the "TYPE" field value is not "S" (Success), "I" (Information), "W" (Warning),
                // or an empty string. If the "TYPE" is any other value, it implies an erroneous response.
                if (!exportStructure.getString(SAPConstants.TYPE).isEmpty() &&
                        !exportStructure.getString(SAPConstants.TYPE).equals(SAPConstants.S)) {
                    return SAPErrorCreator.fromBError(exportStructure.getString(SAPConstants.MESSAGE), null);
                }
                if (outputParamType.getType().getTag() == TypeTags.XML_TAG) {
                    return ValueCreator.createXmlValue(exportStructure.toXML());
                } else if (outputParamType.getType().getTag() == TypeTags.JSON_TAG) {
                    return JsonUtils.parse(exportStructure.toJSON());
                } else {
                return populateOutputMap(exportStructure, outputParamsStructType);
                }
            } catch (Throwable e) {
                logger.error("JCoException occurred. Error: " + e.getMessage());
                return SAPErrorCreator.fromJCoException(e);
            }
    }

    public static Object sendIDoc(BObject client, BXml iDoc, BString iDocType) {
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            String iDocXML = iDoc.toString();
            char version = iDocType.toString().charAt(0);

            String tid = destination.createTID();
            logger.debug("TID created: " + tid);

            IDocFactory iDocFactory = JCoIDoc.getIDocFactory();
            IDocRepository iDocRepository = JCoIDoc.getIDocRepository(destination);
            IDocXMLProcessor processor = iDocFactory.getIDocXMLProcessor();
            IDocDocumentList iDocList = processor.parse(iDocRepository, iDocXML);

            JCoIDoc.send(iDocList, version, destination, tid);
            destination.confirmTID(tid);
            logger.debug("IDoc sent successfully with TID: " + tid);
            return null;
        } catch (JCoException e) {
            logger.error("JCoException occurred");
            return SAPErrorCreator.fromJCoException(e);
        } catch (IDocException e) {
            logger.error("IDocException occurred");
            return SAPErrorCreator.fromIDocException(e);
        }
    }

    private static BMap<BString, Object> populateOutputMap(JCoStructure exportStructure,
                                                           StructureType outputParamType) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue((RecordType) outputParamType);
        for (int i = 0; i < exportStructure.getMetaData().getFieldCount(); i++) {
            String fieldName = exportStructure.getMetaData().getName(i);
            if (outputParamType.getFields().containsKey(fieldName)) {
                int type = outputParamType.getFields().get(fieldName).getFieldType().getTag();
                switch (type) {
                    case TypeTags.STRING_TAG:
                        String value = exportStructure.getString(i);
                        outputMap.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                        break;
                    case TypeTags.INT_TAG:
                        int intValue = exportStructure.getInt(i);
                        outputMap.put(StringUtils.fromString(fieldName), intValue);
                        break;
                    case TypeTags.FLOAT_TAG:
                        double doubleValue = exportStructure.getDouble(i);
                        outputMap.put(StringUtils.fromString(fieldName), doubleValue);
                        break;
                    case TypeTags.DECIMAL_TAG:
                        BigDecimal decimalValue = exportStructure.getBigDecimal(i);
                        outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                        break;
                    case TypeTags.ARRAY_TAG:
                        byte[] byteArray = exportStructure.getByteArray(i);
                        outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                        break;
                    case TypeTags.RECORD_TYPE_TAG:
                        if (SAPConstants.DATE.equals(
                                outputParamType.getFields().get(fieldName).getFieldType().getName())) {
                            RecordType recordType = (RecordType) outputParamType.getFields().get(fieldName)
                                    .getFieldType();
                            outputMap.put(StringUtils.fromString(fieldName), createDateRecord(exportStructure.getDate(i)
                                    , recordType));
                            break;
                        }
                        BMap<BString, Object> nestedRecord = populateOutputMap(exportStructure.getStructure(i),
                                (StructureType) outputParamType.getFields().get(fieldName).getFieldType());
                        outputMap.put(StringUtils.fromString(fieldName), nestedRecord);
                        break;
                    default:
                        throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                                fieldName + ". Unsupported type " + type, null);
                }
            }
        }
        return outputMap;
    }

    private static BMap<BString, Object> createDateRecord(Date date, RecordType fieldType) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        BMap<BString, Object> dateRecord = ValueCreator.createRecordValue(fieldType);
        dateRecord.put(StringUtils.fromString("year"), calendar.get(Calendar.YEAR));
        dateRecord.put(StringUtils.fromString("month"), calendar.get(Calendar.MONTH) + 1);
        dateRecord.put(StringUtils.fromString("day"), calendar.get(Calendar.DAY_OF_MONTH));
        dateRecord.put(StringUtils.fromString("hour"), calendar.get(Calendar.HOUR_OF_DAY));
        dateRecord.put(StringUtils.fromString("minute"), calendar.get(Calendar.MINUTE));
        dateRecord.put(StringUtils.fromString("second"), calendar.get(Calendar.SECOND));
        return dateRecord;
    }

    private static void setImportParams(JCoParameterList jcoParamList, BMap<BString, Object> inputParams,
                                        boolean setImportParamNull) {
        inputParams.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            String key = entry.getKey().toString();

            if (value == null && setImportParamNull) {
                jcoParamList.setValue(key, Optional.empty());
                return;
            }
            int type = resolveType(value);
            switch (type) {
                case TypeTags.STRING_TAG:
                    jcoParamList.setValue(key, value.toString());
                    break;
                case TypeTags.INT_TAG:
                    jcoParamList.setValue(key, Integer.parseInt(value.toString()));
                    break;
                case TypeTags.FLOAT_TAG:
                    jcoParamList.setValue(key, Double.parseDouble(value.toString()));
                    break;
                case TypeTags.DECIMAL_TAG:
                    jcoParamList.setValue(key, new BigDecimal(value.toString()));
                    break;
                case TypeTags.ARRAY_TAG:
                    if (isByteArray(value)) {
                        jcoParamList.setValue(key, ValueCreator.createArrayValue((byte[]) value));
                    }
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    handleRecordType(jcoParamList, key, value);
                    break;
                default:
                    throwUnsupportedUnionTypeError(entry.getKey(), TypeUtils.getType(value).getName());
            }
        });
    }

    private static int resolveType(Object value) {
        int type = TypeUtils.getType(value).getTag();
        if (type == TypeTags.UNION_TAG) {
            UnionType unionType = (UnionType) TypeUtils.getType(value);
            if (unionType.getMemberTypes().size() == 2) {
                if (unionType.getMemberTypes().get(0).getTag() == TypeTags.NULL_TAG) {
                    type = unionType.getMemberTypes().get(1).getTag();
                } else if (unionType.getMemberTypes().get(1).getTag() == TypeTags.NULL_TAG) {
                    type = unionType.getMemberTypes().get(0).getTag();
                } else {
                    throwUnsupportedUnionTypeError(value, TypeUtils.getType(value).getName());
                }
            } else {
                throwUnsupportedUnionTypeError(value, TypeUtils.getType(value).getName());
            }
        }
        return type;
    }

    private static boolean isByteArray(Object value) {
        return TypeUtils.getReferredType(((ArrayType) TypeUtils.getType(value)).getElementType()).getTag() ==
                TypeTags.BYTE_TAG;
    }

    @SuppressWarnings("unchecked")
    private static void handleRecordType(JCoParameterList jcoParamList, String key, Object value) {
        if (SAPConstants.DATE.equals(TypeUtils.getType(value).getName())) {
            BMap<BString, Object> dateMap = (BMap<BString, Object>) value;
            Date dateValue = extractDate(dateMap);
            if (dateValue != null) {
                jcoParamList.setValue(key, dateValue);
            } else {
                throw SAPErrorCreator.fromBError("Invalid date record: year, month, and day must be provided.", null);
            }
        } else {
            JCoStructure structure = jcoParamList.getStructure(key);
            BMap<BString, Object> record = (BMap<BString, Object>) value;
            populateStructure(structure, record);
        }
    }

    private static Date extractDate(BMap<BString, Object> dateMap) {
        Object yearObj = dateMap.get(StringUtils.fromString("year"));
        Object monthObj = dateMap.get(StringUtils.fromString("month"));
        Object dayObj = dateMap.get(StringUtils.fromString("day"));
        Object hourObj = dateMap.get(StringUtils.fromString("hour"));
        Object minuteObj = dateMap.get(StringUtils.fromString("minute"));
        Object secondObj = dateMap.get(StringUtils.fromString("second"));

        if (yearObj != null && monthObj != null && dayObj != null) {
            int year = Integer.parseInt(yearObj.toString());
            int month = Integer.parseInt(monthObj.toString());
            int day = Integer.parseInt(dayObj.toString());

            int hour = (hourObj != null) ? Integer.parseInt(hourObj.toString()) : 0;
            int minute = (minuteObj != null) ? Integer.parseInt(minuteObj.toString()) : 0;
            int second = (secondObj != null) ? Integer.parseInt(secondObj.toString()) : 0;

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month - 1, day, hour, minute, second);
            return calendar.getTime();
        }
        return null;
    }

    private static void populateStructure(JCoStructure structure, BMap<BString, Object> record) {
        record.entrySet().forEach(entry -> {
            int type = resolveType(entry.getValue());
            String key = entry.getKey().toString();

            switch (type) {
                case TypeTags.STRING_TAG:
                    structure.setValue(key, entry.getValue().toString());
                    break;
                case TypeTags.INT_TAG:
                    structure.setValue(key, Integer.parseInt(entry.getValue().toString()));
                    break;
                case TypeTags.FLOAT_TAG:
                    structure.setValue(key, Double.parseDouble(entry.getValue().toString()));
                    break;
                case TypeTags.DECIMAL_TAG:
                    structure.setValue(key, new BigDecimal(entry.getValue().toString()));
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    handleNestedRecordType(structure, key, entry.getValue());
                    break;
                default:
                    throwUnsupportedUnionTypeError(entry.getKey(), TypeUtils.getType(entry).getName());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void handleNestedRecordType(JCoStructure structure, String key, Object value) {
        if (SAPConstants.DATE.equals(TypeUtils.getType(value).getName())) {
            BMap<BString, Object> dateMap = (BMap<BString, Object>) value;
            Date dateValue = extractDate(dateMap);
            if (dateValue != null) {
                structure.setValue(key, dateValue);
            } else {
                throw SAPErrorCreator.fromBError("Invalid date record: year, month, and day must be provided.", null);
            }
        } else {
            JCoStructure nestedStructure = structure.getStructure(key);
            BMap<BString, Object> nestedRecord = (BMap<BString, Object>) value;
            populateStructure(nestedStructure, nestedRecord);
        }
    }

    private static void throwUnsupportedUnionTypeError(Object key, String type) {
        throw SAPErrorCreator.fromBError("Error while processing destination properties: " +
                key.toString() + ". Unsupported union type '" + type + "'. Supported types " +
                "are: string, int, float, decimal and nullable supported types.", null);
    }

}

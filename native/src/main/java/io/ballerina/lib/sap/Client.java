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
import com.sap.conn.jco.JCoTable;
import com.sap.conn.jco.ext.Environment;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.StructureType;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.api.values.BXml;
import io.ballerina.stdlib.time.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static Object initializeClient(BObject client, BMap<BString, Object> jcoDestinationConfig,
                                          BString destinationId) {
        try {
            BallerinaDestinationDataProvider dp = new BallerinaDestinationDataProvider();
            Environment.registerDestinationDataProvider(dp);
            dp.addDestination(jcoDestinationConfig, destinationId);
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
                setImportParams(importParams, inputParams);

                function.execute(destination);

                JCoParameterList exportParams = function.getExportParameterList();

                if (outputParamType.getType().getTag() == TypeTags.XML_TAG) {
                    return ValueCreator.createXmlValue(exportParams.toXML());
                } else if (outputParamType.getType().getTag() == TypeTags.JSON_TAG) {
                    return JsonUtils.parse(exportParams.toJSON());
                } else if (outputParamType.getType().getTag() == TypeTags.RECORD_TYPE_TAG) {
                    return getExportParams(exportParams, (RecordType) outputParamsStructType);
                } else {
                    throw SAPErrorCreator.fromBError("Unsupported output parameter type: " +
                            outputParamType.getType().getName(), null);
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
            logger.error("JCoException occurred");
            return SAPErrorCreator.fromJCoException(e);
        } catch (IDocException e) {
            logger.error("IDocException occurred");
            return SAPErrorCreator.fromIDocException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setImportParams(JCoParameterList jcoParamList, BMap<BString, Object> inputParams) {
        inputParams.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            String key = entry.getKey().toString();
            int type = TypeUtils.getType(value).getTag();
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
                case TypeTags.BYTE_ARRAY_TAG:
                    jcoParamList.setValue(key, ValueCreator.createArrayValue((byte[]) value));
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    if (SAPConstants.DATE.equals(TypeUtils.getType(value).getName()) ||
                            SAPConstants.TIME_OF_DAY.equals(TypeUtils.getType(value).getName())) {
                        BMap<BString, Object> dateMap = (BMap<BString, Object>) value;
                        Date dateValue = extractDate(dateMap);
                        if (dateValue != null) {
                            jcoParamList.setValue(key, dateValue);
                        } else {
                            throw SAPErrorCreator.fromBError("Invalid date record: year, month, " +
                                    "and day must be provided.", null);
                        }
                    } else {
                        JCoStructure structure = jcoParamList.getStructure(key);
                        BMap<BString, Object> record = (BMap<BString, Object>) value;
                        createStructure(structure, record);
                        jcoParamList.setValue(key, structure);
                    }
                    break;
                case TypeTags.ARRAY_TAG:
                    JCoTable table = jcoParamList.getTable(key);
                    createTable(table, (BArray) value);
                    jcoParamList.setValue(key, table);
                    break;
                default:
                    throwUnsupportedUnionTypeError(entry.getKey(), TypeUtils.getType(value).getName());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void createTable(JCoTable table, BArray inputParams) {
        for (int i = 0; i < inputParams.size(); i++) {
            table.appendRow();
            BMap<BString, Object> value = (BMap<BString, Object>) inputParams.get(i);
            value.entrySet().forEach(entry -> {
                Object fieldValue = entry.getValue();
                String fieldName = entry.getKey().toString();
                int type = TypeUtils.getType(fieldValue).getTag();
                switch (type) {
                    case TypeTags.STRING_TAG:
                        table.setValue(fieldName, fieldValue.toString());
                        break;
                    case TypeTags.INT_TAG:
                        table.setValue(fieldName, Integer.parseInt(fieldValue.toString()));
                        break;
                    case TypeTags.FLOAT_TAG:
                        table.setValue(fieldName, Double.parseDouble(fieldValue.toString()));
                        break;
                    case TypeTags.DECIMAL_TAG:
                        table.setValue(fieldName, new BigDecimal(fieldValue.toString()));
                        break;
                    case TypeTags.BYTE_ARRAY_TAG:
                        table.setValue(fieldName, ValueCreator.createArrayValue((byte[]) fieldValue));
                        break;
                    case TypeTags.RECORD_TYPE_TAG:
                        if (SAPConstants.DATE.equals(TypeUtils.getType(fieldValue).getName()) ||
                                SAPConstants.TIME_OF_DAY.equals(TypeUtils.getType(fieldValue).getName())) {
                            BMap<BString, Object> dateMap = (BMap<BString, Object>) fieldValue;
                            Date dateValue = extractDate(dateMap);
                            if (dateValue != null) {
                                table.setValue(fieldName, dateValue);
                            } else {
                                throw SAPErrorCreator.fromBError("Invalid date record: year, month, " +
                                        "and day must be provided.", null);
                            }
                        } else {
                            JCoStructure structure = table.getStructure(fieldName);
                            BMap<BString, Object> record = (BMap<BString, Object>) fieldValue;
                            createStructure(structure, record);
                            table.setValue(fieldName, structure);
                        }
                        break;
                    case TypeTags.ARRAY_TAG:
                        JCoTable nestedTable = table.getTable(fieldName);
                        createTable(nestedTable, (BArray) fieldValue);
                        table.setValue(fieldName, nestedTable);
                        break;
                    default:
                        throwUnsupportedUnionTypeError(entry.getKey(), TypeUtils.getType(fieldValue).getName());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void createStructure(JCoStructure structure, BMap<BString, Object> inputParams) {
        inputParams.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            String key = entry.getKey().toString();
            int type = TypeUtils.getType(value).getTag();
            switch (type) {
                case TypeTags.STRING_TAG:
                    structure.setValue(key, value.toString());
                    break;
                case TypeTags.INT_TAG:
                    structure.setValue(key, Integer.parseInt(value.toString()));
                    break;
                case TypeTags.FLOAT_TAG:
                    structure.setValue(key, Double.parseDouble(value.toString()));
                    break;
                case TypeTags.DECIMAL_TAG:
                    structure.setValue(key, new BigDecimal(value.toString()));
                    break;
                case TypeTags.BYTE_ARRAY_TAG:
                    structure.setValue(key, ValueCreator.createArrayValue((byte[]) value));
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    if (SAPConstants.DATE.equals(TypeUtils.getType(value).getName()) ||
                            SAPConstants.TIME_OF_DAY.equals(TypeUtils.getType(value).getName())) {
                        BMap<BString, Object> dateMap = (BMap<BString, Object>) value;
                        Date dateValue = extractDate(dateMap);
                        if (dateValue != null) {
                            structure.setValue(key, dateValue);
                        } else {
                            throw SAPErrorCreator.fromBError("Invalid date record: year, month, " +
                                    "and day must be provided.", null);
                        }
                    } else {
                        JCoStructure nestedStructure = structure.getStructure(key);
                        BMap<BString, Object> record = (BMap<BString, Object>) value;
                        createStructure(structure, record);
                        structure.setValue(key, nestedStructure);
                    }
                    break;
                case TypeTags.ARRAY_TAG:
                    JCoTable table = structure.getTable(key);
                    createTable(table, (BArray) value);
                    structure.setValue(key, table);
                    break;
                default:
                    throwUnsupportedUnionTypeError(entry.getKey(), TypeUtils.getType(value).getName());
            }
        });
    }

    private static BArray populateRecordArray(JCoTable table, RecordType outputRecordType) {
        BArray recordArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(outputRecordType));
        for (int i = 0; i < table.getNumRows(); i++) {
            table.setRow(i);
            BMap<BString, Object> record = ValueCreator.createRecordValue(outputRecordType);
            for (int j = 0; j < table.getMetaData().getFieldCount(); j++) {
                String fieldName = table.getMetaData().getName(j);
                String type = table.getMetaData().getClassNameOfField(j);
                RecordType nestedRecordType;
                switch (type) {
                    case SAPConstants.JCO_STRING:
                        String value = table.getString(j);
                        record.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                        break;
                    case SAPConstants.JCO_INTEGER:
                        int intValue = table.getInt(j);
                        record.put(StringUtils.fromString(fieldName), intValue);
                        break;
                    case SAPConstants.JCO_DOUBLE:
                        double doubleValue = table.getDouble(j);
                        record.put(StringUtils.fromString(fieldName), doubleValue);
                        break;
                    case SAPConstants.JCO_BIG_DECIMAL:
                        BigDecimal decimalValue = table.getBigDecimal(j);
                        record.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                        break;
                    case SAPConstants.JCO_LONG:
                        long longValue = table.getLong(j);
                        record.put(StringUtils.fromString(fieldName), longValue);
                        break;
                    case SAPConstants.JCO_OBJECT:
                        Object objectValue = table.getValue(j);
                        record.put(StringUtils.fromString(fieldName), objectValue.toString());
                        break;
                    case SAPConstants.JCO_BYTE_ARRAY:
                        byte[] byteArray = table.getByteArray(j);
                        record.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                        break;
                    case SAPConstants.JCO_DATE:
                        record.put(StringUtils.fromString(fieldName), createDateRecord(
                                table.getMetaData().getTypeAsString(j), table.getDate(j)));
                        break;
                    case SAPConstants.JCO_STRUCTURE:
                        if (outputRecordType.getFields().containsKey(fieldName)) {
                            nestedRecordType = (RecordType) outputRecordType.getFields().get(fieldName).getFieldType();
                        } else {
                            nestedRecordType = setFields(table.getStructure(j));
                        }
                        BMap<BString, Object> nestedRecord = populateRecord(table.getStructure(j), nestedRecordType);
                        record.put(StringUtils.fromString(fieldName), nestedRecord);
                        break;
                    case SAPConstants.JCO_TABLE:
                        if (outputRecordType.getFields().containsKey(fieldName)) {
                            nestedRecordType = (RecordType) outputRecordType.getFields().get(fieldName).getFieldType();
                        } else {
                            nestedRecordType = (RecordType) setTableFields(table.getTable(j)).getElementType();
                        }
                        BArray nestedRecordArray = populateRecordArray(table.getTable(j), nestedRecordType);
                        record.put(StringUtils.fromString(fieldName), nestedRecordArray);
                        break;
                    default:
                        throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                                fieldName + ". Unsupported type " + type, null);
                }
            }
            recordArray.append(record);
        }
        return null;
    }

    private static RecordType setFields(JCoStructure structure) {
        Map<String, Field> fields = new HashMap<>();
        for (int i = 0; i < structure.getMetaData().getFieldCount(); i++) {
            String fieldName = structure.getMetaData().getName(i);
            String type = structure.getMetaData().getClassNameOfField(i);
            switch (type) {
                case SAPConstants.JCO_STRING:
                case SAPConstants.JCO_OBJECT:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_STRING, fieldName, 0));
                    break;
                case SAPConstants.JCO_INTEGER:
                case SAPConstants.JCO_LONG:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_INT, fieldName, 0));
                    break;
                case SAPConstants.JCO_DOUBLE:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_FLOAT, fieldName, 0));
                    break;
                case SAPConstants.JCO_BIG_DECIMAL:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_DECIMAL, fieldName, 0));
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_BYTE, fieldName, 0));
                    break;
                case SAPConstants.JCO_DATE:
                    if (SAPConstants.JCO_DATE_TYPE_DATE.equals(structure.getMetaData().getTypeAsString(i))) {
                        fields.put(fieldName, TypeCreator.createField(
                                TypeCreator.createRecordType(
                                        SAPConstants.DATE,
                                        io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                                        0, true, 0),
                                fieldName, 0));
                    } else if (SAPConstants.JCO_DATE_TYPE_TIME.equals(structure.getMetaData().getTypeAsString(i))) {
                        fields.put(fieldName, TypeCreator.createField(
                                TypeCreator.createRecordType(
                                        SAPConstants.TIME_OF_DAY,
                                        io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                                        0, true, 0),
                                fieldName, 0));
                    } else {
                        throw SAPErrorCreator.fromBError("Unsupported date type " + type, null);
                    }
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    fields.put(fieldName, TypeCreator.createField(setFields(structure.getStructure(i)), fieldName, 0));
                    break;
                case SAPConstants.JCO_TABLE:
                    fields.put(fieldName, TypeCreator.createField(setTableFields(structure.getTable(i)), fieldName, 0));
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type, null);

            }
        }
        return TypeCreator.createRecordType(structure.getMetaData().getName(), ModuleUtils.getModule(), 0, fields,
                null, true, 0);
    }

    private static ArrayType setTableFields(JCoTable table) {
        Map<String, Field> tableElementType = new HashMap<>();
        for (int j = 0; j < table.getNumRows(); j++) {
            String tableFieldName = table.getMetaData().getName(j);
            String tableFieldType = table.getMetaData().getClassNameOfField(j);
            switch (tableFieldType) {
                case SAPConstants.JCO_STRING:
                case SAPConstants.JCO_OBJECT:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_STRING,
                            tableFieldName, 0));
                    break;
                case SAPConstants.JCO_INTEGER:
                case SAPConstants.JCO_LONG:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_INT,
                            tableFieldName, 0));
                    break;
                case SAPConstants.JCO_DOUBLE:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_FLOAT,
                            tableFieldName, 0));
                    break;
                case SAPConstants.JCO_BIG_DECIMAL:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_DECIMAL,
                            tableFieldName, 0));
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_BYTE,
                            tableFieldName, 0));
                    break;
                case SAPConstants.JCO_DATE:
                    if (SAPConstants.JCO_DATE_TYPE_DATE.equals(table.getMetaData()
                            .getTypeAsString(j))) {
                        tableElementType.put(tableFieldName, TypeCreator.createField(
                                TypeCreator.createRecordType(
                                        SAPConstants.DATE,
                                        io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                                        0, true, 0),
                                tableFieldName, 0));
                    } else if (SAPConstants.JCO_DATE_TYPE_TIME.equals(table.getMetaData()
                            .getTypeAsString(j))) {
                        tableElementType.put(tableFieldName, TypeCreator.createField(
                                TypeCreator.createRecordType(
                                        SAPConstants.TIME_OF_DAY,
                                        io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                                        0, true, 0),
                                tableFieldName, 0));
                    } else {
                        throw SAPErrorCreator.fromBError("Unsupported date type.", null);
                    }
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    tableElementType.put(tableFieldName, TypeCreator.createField(setFields(
                            table.getStructure(j)), tableFieldName, 0));
                    break;
                case SAPConstants.JCO_TABLE:
                    tableElementType.put(tableFieldName, TypeCreator.createField(setTableFields(
                            table.getTable(j)), tableFieldName, 0));
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            tableFieldName + ". Unsupported type " + tableFieldType, null);
            }
        }
        return TypeCreator.createArrayType(TypeCreator.createArrayType(TypeCreator.createRecordType(
                table.getMetaData().getName(), ModuleUtils.getModule(), 0,
                tableElementType, null, true, 0)));
    }

    private static BMap<BString, Object> getExportParams(JCoParameterList exportList,
                                                         RecordType outputParamType) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue(outputParamType);
        for (int i = 0; i < exportList.getMetaData().getFieldCount(); i++) {
            String fieldName = exportList.getMetaData().getName(i);
            String type = exportList.getMetaData().getClassNameOfField(i);
            switch (type) {
                case SAPConstants.JCO_STRING:
                    String value = exportList.getString(i);
                    outputMap.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                    break;
                case SAPConstants.JCO_INTEGER:
                    int intValue = exportList.getInt(i);
                    outputMap.put(StringUtils.fromString(fieldName), intValue);
                    break;
                case SAPConstants.JCO_DOUBLE:
                    double doubleValue = exportList.getDouble(i);
                    outputMap.put(StringUtils.fromString(fieldName), doubleValue);
                    break;
                case SAPConstants.JCO_BIG_DECIMAL:
                    BigDecimal decimalValue = exportList.getBigDecimal(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                    break;
                case SAPConstants.JCO_LONG:
                    long longValue = exportList.getLong(i);
                    outputMap.put(StringUtils.fromString(fieldName), longValue);
                    break;
                case SAPConstants.JCO_OBJECT:
                    Object objectValue = exportList.getValue(i);
                    outputMap.put(StringUtils.fromString(fieldName), objectValue.toString());
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    byte[] byteArray = exportList.getByteArray(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                    break;
                case SAPConstants.JCO_DATE:
                    outputMap.put(StringUtils.fromString(fieldName), createDateRecord(
                            exportList.getMetaData().getTypeAsString(i), exportList.getDate(i)));
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    RecordType nestedRecordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        nestedRecordType = (RecordType) outputParamType.getFields().get(fieldName).getFieldType();
                    } else {
                        // this need to be checked
                        nestedRecordType = TypeCreator.createRecordType(exportList.getMetaData().getName(i),
                                ModuleUtils.getModule(), 0, true, 0);
                    }
                    BMap<BString, Object> nestedRecord = populateRecord(exportList.getStructure(i), nestedRecordType);
                    outputMap.put(StringUtils.fromString(fieldName), nestedRecord);
                    break;
                case SAPConstants.JCO_TABLE:
                    RecordType recordType;
                    BArray nestedRecordArray;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        // this need to be checked
                        recordType = (RecordType) outputParamType.getFields().get(fieldName).getFieldType();
                        nestedRecordArray = populateRecordArray(exportList.getTable(i), recordType);
                        outputMap.put(StringUtils.fromString(fieldName), nestedRecordArray);
                    } else {
                        // this need to be checked
                        recordType = TypeCreator.createRecordType(fieldName,
                                ModuleUtils.getModule(), 0, true, 0); // this need to be changed
                        nestedRecordArray = populateRecordArray(exportList.getTable(i), recordType);
                    }
                    outputMap.put(StringUtils.fromString(fieldName), nestedRecordArray);
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type, null);
            }
        }
        return outputMap;
    }

    private static BMap<BString, Object> populateRecord(JCoStructure exportStructure,
                                                        RecordType outputParamType) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue(outputParamType);
        for (int i = 0; i < exportStructure.getMetaData().getFieldCount(); i++) {
            String fieldName = exportStructure.getMetaData().getName(i);
            String type = exportStructure.getMetaData().getClassNameOfField(i);
            switch (type) {
                case SAPConstants.JCO_STRING:
                    String value = exportStructure.getString(i);
                    outputMap.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                    break;
                case SAPConstants.JCO_INTEGER:
                    int intValue = exportStructure.getInt(i);
                    outputMap.put(StringUtils.fromString(fieldName), intValue);
                    break;
                case SAPConstants.JCO_DOUBLE:
                    double doubleValue = exportStructure.getDouble(i);
                    outputMap.put(StringUtils.fromString(fieldName), doubleValue);
                    break;
                case SAPConstants.JCO_BIG_DECIMAL:
                    BigDecimal decimalValue = exportStructure.getBigDecimal(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                    break;
                case SAPConstants.JCO_LONG:
                    long longValue = exportStructure.getLong(i);
                    outputMap.put(StringUtils.fromString(fieldName), longValue);
                    break;
                case SAPConstants.JCO_OBJECT:
                    Object objectValue = exportStructure.getValue(i);
                    outputMap.put(StringUtils.fromString(fieldName), objectValue.toString());
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    byte[] byteArray = exportStructure.getByteArray(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                    break;
                case SAPConstants.JCO_DATE:
                    outputMap.put(StringUtils.fromString(fieldName), createDateRecord(
                            exportStructure.getMetaData().getTypeAsString(i), exportStructure.getDate(i)));
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    RecordType nestedRecordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        nestedRecordType = (RecordType) outputParamType.getFields().get(fieldName).getFieldType();
                    } else {
                        // this need to be checked
                        nestedRecordType = TypeCreator.createRecordType(exportStructure.getMetaData().getName(i),
                                ModuleUtils.getModule(), 0, true, 0);
                    }
                    BMap<BString, Object> nestedRecord = populateRecord(
                            exportStructure.getStructure(i), nestedRecordType);
                    outputMap.put(StringUtils.fromString(fieldName), nestedRecord);
                    break;
                case SAPConstants.JCO_TABLE:
                    RecordType recordType;
                    BArray nestedRecordArray;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        // this need to be checked
                        recordType = (RecordType) outputParamType.getFields().get(fieldName).getFieldType();
                        nestedRecordArray = populateRecordArray(exportStructure.getTable(i), recordType);
                        outputMap.put(StringUtils.fromString(fieldName), nestedRecordArray);
                    } else {
                        // this need to be checked
                        recordType = TypeCreator.createRecordType(fieldName,
                                ModuleUtils.getModule(), 0, true, 0);
                        nestedRecordArray = populateRecordArray(exportStructure.getTable(i), recordType);
                    }
                    outputMap.put(StringUtils.fromString(fieldName), nestedRecordArray);
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type, null);
            }
        }
        return outputMap;
    }

    private static BMap<BString, Object> createDateRecord(String type, Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (SAPConstants.JCO_DATE_TYPE_DATE.equals(type)) {
            BMap<BString, Object> dateMap = ValueCreator.createRecordValue(
                    io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                    Constants.DATE_RECORD);
            dateMap.put(StringUtils.fromString(Constants
                    .DATE_RECORD_YEAR), calendar.get(Calendar.YEAR));
            dateMap.put(StringUtils.fromString(Constants
                    .DATE_RECORD_MONTH), calendar.get(Calendar.MONTH) + 1);
            dateMap.put(StringUtils.fromString(Constants
                    .DATE_RECORD_DAY), calendar.get(Calendar.DATE));
            return dateMap;
        } else if (SAPConstants.JCO_DATE_TYPE_TIME.equals(type)) {
            BMap<BString, Object> timeMap = ValueCreator.createRecordValue(
                    io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                    Constants.TIME_OF_DAY_RECORD);
            timeMap.put(StringUtils.fromString(Constants
                    .TIME_OF_DAY_RECORD_HOUR), calendar.get(Calendar.HOUR_OF_DAY));
            timeMap.put(StringUtils.fromString(Constants
                    .TIME_OF_DAY_RECORD_MINUTE), calendar.get(Calendar.MINUTE));
            timeMap.put(StringUtils.fromString(Constants
                    .TIME_OF_DAY_RECORD_SECOND), calendar.get(Calendar.SECOND));
            return timeMap;
        } else {
            throw SAPErrorCreator.fromBError("Unsupported date type " + type, null);
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
            int type = TypeUtils.getType(entry.getValue()).getTag();
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
                case TypeTags.BYTE_ARRAY_TAG:
                    structure.setValue(key, ((byte[]) entry.getValue()));
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
                throw SAPErrorCreator.fromBError("Invalid date record: year, month, " +
                        "and day must be provided.", null);
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

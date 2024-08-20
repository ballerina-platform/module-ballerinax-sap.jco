// Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package io.ballerina.lib.sap.parameterprocessor;

import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;
import io.ballerina.lib.sap.ModuleUtils;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.time.util.Constants;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ExportParameterProcessor {

    public static BMap<BString, Object> getExportParams(JCoParameterList exportList, RecordType outputParamType,
                                                        boolean isRestFieldsAllowed) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue(outputParamType);
        for (int i = 0; i < exportList.getMetaData().getFieldCount(); i++) {
            String fieldName = exportList.getMetaData().getName(i);
            String type = exportList.getMetaData().getClassNameOfField(i);
            if (!isRestFieldsAllowed && !outputParamType.getFields().containsKey(fieldName)) {
                continue;
            }
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
                        try {
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                    outputParamType.getFields().get(fieldName).getFieldType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.fromBError("Error while retrieving output structure " +
                                    "parameter for field: " +
                                    fieldName + ". Unsupported type " + TypeUtils.getReferredType(
                                            outputParamType.getFields().get(fieldName).getFieldType()).toString(),
                                    null);
                        }
                    } else {
                        try {
                            nestedRecordType = setFields(exportList.getStructure(i));
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.fromBError("Error while retrieving output anonymous " +
                                    "structure parameter for field: " + fieldName + ". Unsupported type "
                                    + setFields(exportList.getStructure(i)), null);
                        }
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecord(exportList.getStructure(i), nestedRecordType, isRestFieldsAllowed));
                    break;
                case SAPConstants.JCO_TABLE:
                    RecordType recordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        try {
                            recordType = (RecordType) TypeUtils.getReferredType(
                                    ((ArrayType) outputParamType.getFields().get(fieldName).getFieldType())
                                            .getElementType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.fromBError("Error while retrieving output table " +
                                    "parameter for field: " +
                                            fieldName + ". Unsupported type "
                                            + TypeUtils.getReferredType(((ArrayType) outputParamType.getFields()
                                            .get(fieldName).getFieldType()).getElementType()).toString(),
                                    null);
                        }
                    } else {
                        try {
                            recordType = (RecordType) TypeUtils.getReferredType(
                                    ((ArrayType) setTableFields(exportList.getTable(i)).getElementType())
                                            .getElementType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.fromBError("Error while retrieving output anonymous " +
                                    "table parameter for field: " +
                                    fieldName + ". Unsupported type "
                                    + setTableFields(exportList.getTable(i)).getElementType().toString(), null);
                        }
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecordArray(exportList.getTable(i), recordType, isRestFieldsAllowed));
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type, null);
            }
        }
        return outputMap;
    }

    private static BMap<BString, Object> populateRecord(JCoStructure exportStructure, RecordType outputParamType,
                                                        boolean isRestFieldsAllowed) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue(outputParamType);
        for (int i = 0; i < exportStructure.getMetaData().getFieldCount(); i++) {
            String fieldName = exportStructure.getMetaData().getName(i);
            String type = exportStructure.getMetaData().getClassNameOfField(i);
            if (!isRestFieldsAllowed && !outputParamType.getFields().containsKey(fieldName)) {
                continue;
            }
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
                        nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                outputParamType.getFields().get(fieldName).getFieldType());
                    } else {
                        nestedRecordType = setFields(exportStructure.getStructure(i));
                    }
                    BMap<BString, Object> nestedRecord = populateRecord(
                            exportStructure.getStructure(i), nestedRecordType, isRestFieldsAllowed);
                    outputMap.put(StringUtils.fromString(fieldName), nestedRecord);
                    break;
                case SAPConstants.JCO_TABLE:
                    RecordType recordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        recordType = (RecordType) TypeUtils
                                .getReferredType(((ArrayType) outputParamType.getFields().get(fieldName).getFieldType())
                                        .getElementType());
                    } else {
                        recordType = (RecordType) TypeUtils.getReferredType(
                                ((ArrayType) setTableFields(exportStructure.getTable(i)).getElementType())
                                        .getElementType());
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecordArray(exportStructure.getTable(i), recordType, isRestFieldsAllowed));
                    break;
                default:
                    throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type, null);
            }
        }
        return outputMap;
    }

    private static BArray populateRecordArray(JCoTable table, RecordType outputRecordType,
                                              boolean isRestFieldsAllowed) {
        BArray recordArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(outputRecordType));
        for (int i = 0; i < table.getNumRows(); i++) {
            table.setRow(i);
            BMap<BString, Object> record = ValueCreator.createRecordValue(outputRecordType);
            for (int j = 0; j < table.getMetaData().getFieldCount(); j++) {
                String fieldName = table.getMetaData().getName(j);
                String type = table.getMetaData().getClassNameOfField(j);
                if (!isRestFieldsAllowed && !outputRecordType.getFields().containsKey(fieldName)) {
                    continue;
                }
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
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                    outputRecordType.getFields().get(fieldName).getFieldType());
                        } else {
                            nestedRecordType = setFields(table.getStructure(j));
                        }
                        record.put(StringUtils.fromString(fieldName), populateRecord(table.getStructure(j),
                                nestedRecordType, isRestFieldsAllowed));
                        break;
                    case SAPConstants.JCO_TABLE:
                        if (outputRecordType.getFields().containsKey(fieldName)) {
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                    ((ArrayType) outputRecordType.getFields().get(fieldName).getFieldType())
                                            .getElementType());
                        } else {
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                    ((ArrayType) setTableFields(table.getTable(i)).getElementType()).getElementType());
                        }
                        BArray nestedRecordArray = populateRecordArray(table.getTable(j), nestedRecordType,
                                isRestFieldsAllowed);
                        record.put(StringUtils.fromString(fieldName), nestedRecordArray);
                        break;
                    default:
                        throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                                fieldName + ". Unsupported type " + type, null);
                }
            }
            recordArray.append(record);
        }
        return recordArray;
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
        for (int j = 0; j < table.getNumColumns(); j++) {
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

    private static BMap<BString, Object> createDateRecord(String type, Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (SAPConstants.JCO_DATE_TYPE_DATE.equals(type)) {
            BMap<BString, Object> dateMap = ValueCreator.createRecordValue(
                    io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                    Constants.DATE_RECORD);
            dateMap.put(StringUtils.fromString(Constants.DATE_RECORD_YEAR), calendar.get(Calendar.YEAR));
            dateMap.put(StringUtils.fromString(Constants.DATE_RECORD_MONTH), calendar.get(Calendar.MONTH) + 1);
            dateMap.put(StringUtils.fromString(Constants.DATE_RECORD_DAY), calendar.get(Calendar.DATE));
            return dateMap;
        } else if (SAPConstants.JCO_DATE_TYPE_TIME.equals(type)) {
            BMap<BString, Object> timeMap = ValueCreator.createRecordValue(
                    io.ballerina.stdlib.time.util.ModuleUtils.getModule(),
                    Constants.TIME_OF_DAY_RECORD);
            timeMap.put(StringUtils.fromString(Constants.TIME_OF_DAY_RECORD_HOUR), calendar.get(Calendar.HOUR_OF_DAY));
            timeMap.put(StringUtils.fromString(Constants.TIME_OF_DAY_RECORD_MINUTE), calendar.get(Calendar.MINUTE));
            timeMap.put(StringUtils.fromString(Constants.TIME_OF_DAY_RECORD_SECOND), calendar.get(Calendar.SECOND));
            return timeMap;
        } else {
            throw SAPErrorCreator.fromBError("Unsupported date type " + type, null);
        }
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

    private static void throwUnsupportedUnionTypeError(Object key, String type) {
        throw SAPErrorCreator.fromBError("Error while processing destination properties: " +
                key.toString() + ". Unsupported union type '" + type + "'. Supported types " +
                "are: string, int, float, decimal and nullable supported types.", null);
    }
}

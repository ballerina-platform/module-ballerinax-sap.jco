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
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.PredefinedTypes;
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

/**
 * Converts the JCo export parameter list returned by a Remote Function Call into a Ballerina
 * record value. Nested JCo structures map to nested Ballerina records; JCo tables map to
 * Ballerina arrays of records. When the caller supplies a concrete {@link RecordType}, its
 * declared fields drive the conversion; when no type information is available (i.e. the record
 * allows rest fields), the JCo metadata is used to infer the field types at runtime.
 * <p>
 * Supported JCo-to-Ballerina type mappings:
 * <ul>
 *   <li>{@code java.lang.String} → {@code string}</li>
 *   <li>{@code java.lang.Integer} → {@code int}</li>
 *   <li>{@code java.lang.Double} → {@code float}</li>
 *   <li>{@code java.lang.Long} → {@code int}</li>
 *   <li>{@code java.lang.Object} → {@code string} (via {@link Object#toString()})</li>
 *   <li>{@code java.math.BigDecimal} → {@code decimal}</li>
 *   <li>{@code byte[]} → {@code byte[]}</li>
 *   <li>{@code java.util.Date} (DATE) → {@code time:Date}</li>
 *   <li>{@code java.util.Date} (TIME) → {@code time:TimeOfDay}</li>
 *   <li>{@code com.sap.conn.jco.JCoStructure} → nested record</li>
 *   <li>{@code com.sap.conn.jco.JCoTable} → array of records</li>
 * </ul>
 */
public class ExportParameterProcessor {

    /**
     * Creates a Ballerina record populated from both the JCo export parameter list and the JCo
     * table parameter list. Both lists are processed sequentially using {@link #populateFromParamList}.
     * <p>
     * <b>Precedence:</b> When both {@code exportList} and {@code tableList} contain the same field name,
     * the value from {@code tableList} will overwrite the value from {@code exportList} because both
     * are merged sequentially into the same {@code outputMap}. Callers should avoid name collisions
     * or handle them accordingly.
     *
     * @param exportList          the JCo export parameter list, or {@code null}
     * @param tableList           the JCo table parameter list, or {@code null}
     * @param outputParamType     the target Ballerina {@link RecordType}
     * @param isRestFieldsAllowed when {@code true}, fields not declared in {@code outputParamType}
     *                            are included using runtime-inferred types
     * @return a Ballerina record populated with values from both parameter lists
     * @throws BError if a JCo field type is not supported or a type cast fails
     */
    public static BMap<BString, Object> getMergedParams(JCoParameterList exportList, JCoParameterList tableList,
                                                         RecordType outputParamType, boolean isRestFieldsAllowed) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue(outputParamType);
        if (exportList != null) {
            populateFromParamList(exportList, outputMap, outputParamType, isRestFieldsAllowed);
        }
        if (tableList != null) {
            populateFromParamList(tableList, outputMap, outputParamType, isRestFieldsAllowed);
        }
        return outputMap;
    }

    private static void populateFromParamList(JCoParameterList paramList, BMap<BString, Object> outputMap,
                                              RecordType outputParamType, boolean isRestFieldsAllowed) {
        var meta = paramList.getMetaData();
        for (int i = 0; i < meta.getFieldCount(); i++) {
            String fieldName = meta.getName(i);
            String type = meta.getClassNameOfField(i);
            if (!isRestFieldsAllowed && !outputParamType.getFields().containsKey(fieldName)) {
                continue;
            }
            switch (type) {
                case SAPConstants.JCO_STRING:
                    String value = paramList.getString(i);
                    outputMap.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                    break;
                case SAPConstants.JCO_INTEGER:
                    int intValue = paramList.getInt(i);
                    outputMap.put(StringUtils.fromString(fieldName), intValue);
                    break;
                case SAPConstants.JCO_DOUBLE:
                    double doubleValue = paramList.getDouble(i);
                    outputMap.put(StringUtils.fromString(fieldName), doubleValue);
                    break;
                case SAPConstants.JCO_LONG:
                    long longValue = paramList.getLong(i);
                    outputMap.put(StringUtils.fromString(fieldName), longValue);
                    break;
                case SAPConstants.JCO_BOOLEAN:
                    boolean boolValue = (boolean) paramList.getValue(i);
                    outputMap.put(StringUtils.fromString(fieldName), boolValue);
                    break;
                case SAPConstants.JCO_OBJECT:
                    Object objectValue = paramList.getValue(i);
                    if (objectValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), objectValue.toString());
                    break;
                case SAPConstants.JCO_BIG_DECIMAL:
                    BigDecimal decimalValue = paramList.getBigDecimal(i);
                    if (decimalValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    byte[] byteArray = paramList.getByteArray(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                    break;
                case SAPConstants.JCO_DATE:
                    Date dateValue = paramList.getDate(i);
                    if (dateValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), createDateRecord(
                            meta.getTypeAsString(i), dateValue));
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    RecordType nestedRecordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        try {
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(
                                    outputParamType.getFields().get(fieldName).getFieldType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.createParameterError("Error while retrieving output structure " +
                                    "parameter for field: " +
                                    fieldName + ". Unsupported type " + TypeUtils.getReferredType(
                                    outputParamType.getFields().get(fieldName).getFieldType()).toString());
                        }
                    } else {
                        try {
                            nestedRecordType = setFields(paramList.getStructure(i));
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.createParameterError("Error while retrieving output anonymous " +
                                    "structure parameter for field: " + fieldName + ". Unsupported type "
                                    + setFields(paramList.getStructure(i)));
                        }
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecord(paramList.getStructure(i), nestedRecordType, isRestFieldsAllowed));
                    break;
                case SAPConstants.JCO_TABLE:
                    RecordType recordType;
                    if (outputParamType.getFields().containsKey(fieldName)) {
                        try {
                            recordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                    outputParamType.getFields().get(fieldName).getFieldType()).getElementType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.createParameterError("Error while retrieving output table " +
                                    "parameter for field: " +
                                    fieldName + ". Unsupported type " + TypeUtils.getReferredType(((ArrayType)
                                            outputParamType.getFields().get(fieldName).getFieldType()).getElementType())
                                    .toString());
                        }
                    } else {
                        try {
                            recordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                    setTableFields(paramList.getTable(i)).getElementType()).getElementType());
                        } catch (ClassCastException e) {
                            throw SAPErrorCreator.createParameterError("Error while retrieving output anonymous " +
                                    "table parameter for field: " +
                                    fieldName + ". Unsupported type " + setTableFields(paramList.getTable(i)).
                                    getElementType().toString());
                        }
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecordArray(paramList.getTable(i), recordType, isRestFieldsAllowed));
                    break;
                default:
                    throw SAPErrorCreator.createParameterError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type);
            }
        }
    }

    private static BMap<BString, Object> populateRecord(JCoStructure exportStructure, RecordType outputParamType,
                                                        boolean isRestFieldsAllowed) {
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
                    if (decimalValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                    break;
                case SAPConstants.JCO_LONG:
                    long longValue = exportStructure.getLong(i);
                    outputMap.put(StringUtils.fromString(fieldName), longValue);
                    break;
                case SAPConstants.JCO_BOOLEAN:
                    boolean structBoolValue = (boolean) exportStructure.getValue(i);
                    outputMap.put(StringUtils.fromString(fieldName), structBoolValue);
                    break;
                case SAPConstants.JCO_OBJECT:
                    Object objectValue = exportStructure.getValue(i);
                    if (objectValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), objectValue.toString());
                    break;
                case SAPConstants.JCO_BYTE_ARRAY:
                    byte[] byteArray = exportStructure.getByteArray(i);
                    outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                    break;
                case SAPConstants.JCO_DATE:
                    Date structDateValue = exportStructure.getDate(i);
                    if (structDateValue == null) {
                        break;
                    }
                    outputMap.put(StringUtils.fromString(fieldName), createDateRecord(
                            exportStructure.getMetaData().getTypeAsString(i), structDateValue));
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
                        recordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                outputParamType.getFields().get(fieldName).getFieldType()).getElementType());
                    } else {
                        recordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                setTableFields(exportStructure.getTable(i)).getElementType()).getElementType());
                    }
                    outputMap.put(StringUtils.fromString(fieldName),
                            populateRecordArray(exportStructure.getTable(i), recordType, isRestFieldsAllowed));
                    break;
                default:
                    throw SAPErrorCreator.createParameterError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type);
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
                RecordType nestedRecordType;
                if (!isRestFieldsAllowed && !outputRecordType.getFields().containsKey(fieldName)) {
                    continue;
                }
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
                        if (decimalValue == null) {
                            break;
                        }
                        record.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                        break;
                    case SAPConstants.JCO_LONG:
                        long longValue = table.getLong(j);
                        record.put(StringUtils.fromString(fieldName), longValue);
                        break;
                    case SAPConstants.JCO_BOOLEAN:
                        boolean tableBoolValue = (boolean) table.getValue(j);
                        record.put(StringUtils.fromString(fieldName), tableBoolValue);
                        break;
                    case SAPConstants.JCO_OBJECT:
                        Object objectValue = table.getValue(j);
                        if (objectValue == null) {
                            break;
                        }
                        record.put(StringUtils.fromString(fieldName), objectValue.toString());
                        break;
                    case SAPConstants.JCO_BYTE_ARRAY:
                        byte[] byteArray = table.getByteArray(j);
                        record.put(StringUtils.fromString(fieldName), ValueCreator.createArrayValue(byteArray));
                        break;
                    case SAPConstants.JCO_DATE:
                        Date tableDateValue = table.getDate(j);
                        if (tableDateValue == null) {
                            break;
                        }
                        record.put(StringUtils.fromString(fieldName), createDateRecord(
                                table.getMetaData().getTypeAsString(j), tableDateValue));
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
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                    outputRecordType.getFields().get(fieldName).getFieldType()).getElementType());
                        } else {
                            nestedRecordType = (RecordType) TypeUtils.getReferredType(((ArrayType)
                                    setTableFields(table.getTable(j)).getElementType()).getElementType());
                        }
                        BArray nestedRecordArray = populateRecordArray(table.getTable(j), nestedRecordType,
                                isRestFieldsAllowed);
                        record.put(StringUtils.fromString(fieldName), nestedRecordArray);
                        break;
                    default:
                        throw SAPErrorCreator.createParameterError("Error while retrieving output parameter for " + 
                            "field: " + fieldName + ". Unsupported type " + type);
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
                case SAPConstants.JCO_BOOLEAN:
                    fields.put(fieldName, TypeCreator.createField(PredefinedTypes.TYPE_BOOLEAN, fieldName, 0));
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
                        throw SAPErrorCreator.createParameterError("Unsupported date type: " + type);
                    }
                    break;
                case SAPConstants.JCO_STRUCTURE:
                    fields.put(fieldName, TypeCreator.createField(setFields(structure.getStructure(i)), fieldName, 0));
                    break;
                case SAPConstants.JCO_TABLE:
                    fields.put(fieldName, TypeCreator.createField(setTableFields(structure.getTable(i)), fieldName, 0));
                    break;
                default:
                    throw SAPErrorCreator.createParameterError("Error while retrieving output parameter for field: " +
                            fieldName + ". Unsupported type " + type);

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
                case SAPConstants.JCO_BOOLEAN:
                    tableElementType.put(tableFieldName, TypeCreator.createField(PredefinedTypes.TYPE_BOOLEAN,
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
                        throw SAPErrorCreator.createParameterError("Unsupported date type.");
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
                    throw SAPErrorCreator.createParameterError("Error while retrieving output parameter for field: " +
                            tableFieldName + ". Unsupported type " + tableFieldType);
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
            throw SAPErrorCreator.createParameterError("Unsupported date type: " + type);
        }
    }

}

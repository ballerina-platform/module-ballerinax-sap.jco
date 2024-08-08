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
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

public class ImportParameterProcessor {

    @SuppressWarnings("unchecked")
    public static void setImportParams(JCoParameterList jcoParamList, BMap<BString, Object> inputParams) {
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

    private static Date extractDate(BMap<BString, Object> dateMap) {
        Object yearObj = dateMap.get(StringUtils.fromString("year"));
        Object monthObj = dateMap.get(StringUtils.fromString("month"));
        Object dayObj = dateMap.get(StringUtils.fromString("day"));
        Object hourObj = dateMap.get(StringUtils.fromString("hour"));
        Object minuteObj = dateMap.get(StringUtils.fromString("minute"));
        Object secondObj = dateMap.get(StringUtils.fromString("second"));

        int year = (yearObj != null) ? Integer.parseInt(yearObj.toString()) : 1970;
        int month = (monthObj != null) ? Integer.parseInt(monthObj.toString()) : 1;
        int day = (dayObj != null) ? Integer.parseInt(dayObj.toString()) : 1;

        int hour = (hourObj != null) ? Integer.parseInt(hourObj.toString()) : 0;
        int minute = (minuteObj != null) ? Integer.parseInt(minuteObj.toString()) : 0;
        int second = (secondObj != null) ? Integer.parseInt(secondObj.toString()) : 0;

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTime();
    }

    private static void throwUnsupportedUnionTypeError(Object key, String type) {
        throw SAPErrorCreator.fromBError("Error while processing destination properties: " +
                key.toString() + ". Unsupported union type '" + type + "'. Supported types " +
                "are: string, int, float, decimal and nullable supported types.", null);
    }

}

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

import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRecord;
import com.sap.conn.jco.JCoTable;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

/**
 * Populates JCo parameter lists (import, changing and table parameters) from Ballerina values.
 * Supports scalar fields, nested structures, tables (arrays of records) and {@code time:Date} records.
 */
public final class ParameterProcessor {

    private ParameterProcessor() {
    }

    /**
     * Sets the given Ballerina parameter map onto the function, routing each entry to the
     * import, changing or table parameter list it belongs to.
     */
    public static void setParameters(JCoFunction function, BMap<BString, Object> params, boolean setNullValues) {
        JCoParameterList[] lists = {function.getImportParameterList(), function.getChangingParameterList(),
                function.getTableParameterList()};
        params.entrySet().forEach(entry -> {
            String name = entry.getKey().toString();
            JCoParameterList target = null;
            for (JCoParameterList list : lists) {
                if (list != null && list.getListMetaData().hasField(name)) {
                    target = list;
                    break;
                }
            }
            if (target == null) {
                throw SAPErrorCreator.fromBError("Parameter '" + name + "' is not defined as an import, changing " +
                        "or table parameter of function '" + function.getName() + "'.", null);
            }
            Object value = entry.getValue();
            if (value == null) {
                if (setNullValues) {
                    target.setValue(name, (Object) null);
                }
                return;
            }
            setField(target, name, value);
        });
    }

    private static void setField(JCoRecord record, String name, Object value) {
        int tag = resolveTypeTag(value);
        switch (tag) {
            case TypeTags.STRING_TAG:
                record.setValue(name, value.toString());
                break;
            case TypeTags.INT_TAG:
            case TypeTags.BYTE_TAG:
                record.setValue(name, Long.parseLong(value.toString()));
                break;
            case TypeTags.FLOAT_TAG:
                record.setValue(name, Double.parseDouble(value.toString()));
                break;
            case TypeTags.DECIMAL_TAG:
                record.setValue(name, new BigDecimal(value.toString()));
                break;
            case TypeTags.BYTE_ARRAY_TAG:
            case TypeTags.ARRAY_TAG:
            case TypeTags.TUPLE_TAG:
                setArrayField(record, name, (BArray) value);
                break;
            case TypeTags.RECORD_TYPE_TAG:
            case TypeTags.MAP_TAG:
                setMappingField(record, name, value);
                break;
            default:
                throw SAPErrorCreator.fromBError("Unsupported value for parameter '" + name + "'. Supported " +
                        "types are: string, int, float, decimal, byte[], records (structures), arrays of " +
                        "records (tables) and time:Date.", null);
        }
    }

    private static void setArrayField(JCoRecord record, String name, BArray array) {
        // Byte-array detection goes through the type (tuples have no uniform element type).
        Type type = TypeUtils.getImpliedType(TypeUtils.getType(array));
        if (type.getTag() == TypeTags.ARRAY_TAG
                && TypeUtils.getImpliedType(((ArrayType) type).getElementType()).getTag() == TypeTags.BYTE_TAG) {
            record.setValue(name, array.getBytes());
            return;
        }
        JCoTable table = record.getTable(name);
        populateTable(table, array, name);
    }

    @SuppressWarnings("unchecked")
    private static void setMappingField(JCoRecord record, String name, Object value) {
        BMap<BString, Object> mapping = (BMap<BString, Object>) value;
        if (isDateRecord(value)) {
            record.setValue(name, extractDate(mapping, name));
        } else {
            populateRecord(record.getStructure(name), mapping);
        }
    }

    @SuppressWarnings("unchecked")
    private static void populateTable(JCoTable table, BArray rows, String name) {
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row == null) {
                throw SAPErrorCreator.fromBError("Row " + i + " of table parameter '" + name +
                        "' is nil. Table rows must be non-nil.", null);
            }
            table.appendRow();
            if (row instanceof BMap) {
                populateRecord(table, (BMap<BString, Object>) row);
            } else {
                // Unstructured line type (single unnamed column), e.g. a table of CHAR lines.
                setScalarByIndex(table, row, name);
            }
        }
    }

    private static void setScalarByIndex(JCoTable table, Object value, String name) {
        int tag = resolveTypeTag(value);
        switch (tag) {
            case TypeTags.STRING_TAG:
                table.setValue(0, value.toString());
                break;
            case TypeTags.INT_TAG:
            case TypeTags.BYTE_TAG:
                table.setValue(0, Long.parseLong(value.toString()));
                break;
            case TypeTags.FLOAT_TAG:
                table.setValue(0, Double.parseDouble(value.toString()));
                break;
            case TypeTags.DECIMAL_TAG:
                table.setValue(0, new BigDecimal(value.toString()));
                break;
            default:
                throw SAPErrorCreator.fromBError("Unsupported row value in table parameter '" + name +
                        "'. Rows of unstructured tables must be string, int, float or decimal values.", null);
        }
    }

    private static void populateRecord(JCoRecord jcoRecord, BMap<BString, Object> mapping) {
        mapping.entrySet().forEach(entry -> {
            String fieldName = entry.getKey().toString();
            Object value = entry.getValue();
            if (value == null) {
                return;
            }
            setField(jcoRecord, fieldName, value);
        });
    }

    private static boolean isDateRecord(Object value) {
        Type type = TypeUtils.getImpliedType(TypeUtils.getType(value));
        if (SAPConstants.DATE.equals(type.getName())) {
            return true;
        }
        // Readonly/cloned time:Date values can lose the type name; fall back to shape. SAP
        // structure fields are uppercase, so lowercase year/month/day cannot collide with them.
        if (value instanceof BMap) {
            BMap<?, ?> mapping = (BMap<?, ?>) value;
            return mapping.containsKey(StringUtils.fromString("year"))
                    && mapping.containsKey(StringUtils.fromString("month"))
                    && mapping.containsKey(StringUtils.fromString("day"));
        }
        return false;
    }

    private static Date extractDate(BMap<BString, Object> dateMap, String name) {
        Object yearObj = dateMap.get(StringUtils.fromString("year"));
        Object monthObj = dateMap.get(StringUtils.fromString("month"));
        Object dayObj = dateMap.get(StringUtils.fromString("day"));
        Object hourObj = dateMap.get(StringUtils.fromString("hour"));
        Object minuteObj = dateMap.get(StringUtils.fromString("minute"));
        Object secondObj = dateMap.get(StringUtils.fromString("second"));

        if (yearObj == null || monthObj == null || dayObj == null) {
            throw SAPErrorCreator.fromBError("Invalid date value for parameter '" + name + "': year, month, and " +
                    "day must be provided.", null);
        }
        int year = Integer.parseInt(yearObj.toString());
        int month = Integer.parseInt(monthObj.toString());
        int day = Integer.parseInt(dayObj.toString());
        int hour = (hourObj != null) ? Integer.parseInt(hourObj.toString()) : 0;
        int minute = (minuteObj != null) ? Integer.parseInt(minuteObj.toString()) : 0;
        // time:Date seconds are decimal; truncate fractions for the DATE/TIME conversion.
        int second = (secondObj != null) ? new BigDecimal(secondObj.toString()).intValue() : 0;

        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTime();
    }

    private static int resolveTypeTag(Object value) {
        Type type = TypeUtils.getImpliedType(TypeUtils.getType(value));
        int tag = type.getTag();
        if (tag == TypeTags.UNION_TAG) {
            UnionType unionType = (UnionType) type;
            if (unionType.getMemberTypes().size() == 2) {
                int first = unionType.getMemberTypes().get(0).getTag();
                int second = unionType.getMemberTypes().get(1).getTag();
                if (first == TypeTags.NULL_TAG) {
                    return second;
                }
                if (second == TypeTags.NULL_TAG) {
                    return first;
                }
            }
            throw SAPErrorCreator.fromBError("Unsupported union type '" + type.getName() + "'. Only nullable " +
                    "variants of the supported types are allowed.", null);
        }
        return tag;
    }
}

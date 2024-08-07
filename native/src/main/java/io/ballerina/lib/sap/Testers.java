package io.ballerina.lib.sap;

import com.google.gson.Gson;
import com.sap.conn.jco.JCoAbapObject;
import com.sap.conn.jco.JCoField;
import com.sap.conn.jco.JCoFieldIterator;
import com.sap.conn.jco.JCoListMetaData;
import com.sap.conn.jco.JCoMetaData;
import com.sap.conn.jco.JCoParameterFieldIterator;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRecord;
import com.sap.conn.jco.JCoRecordFieldIterator;
import com.sap.conn.jco.JCoRecordMetaData;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;


import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class Testers {

    public static JCoTable createTable() {
        return new JCoTable() {
            private int currentRow = -1;
            private final Map<Integer, Map<String, Object>> rows = new HashMap<>();

            @Override
            public JCoRecordMetaData getRecordMetaData() {
                return null;
            }

            @Override
            public void ensureBufferCapacity(int i) {
            }

            @Override
            public void trimToRows() {
            }

            @Override
            public boolean isEmpty() {
                return rows.isEmpty();
            }

            @Override
            public boolean isFirstRow() {
                return currentRow == 0;
            }

            @Override
            public boolean isLastRow() {
                return currentRow == rows.size() - 1;
            }

            @Override
            public int getNumRows() {
                return rows.size();
            }

            @Override
            public int getNumColumns() {
                return rows.isEmpty() ? 0 : rows.get(0).size();
            }

            @Override
            public void clear() {
                rows.clear();
                currentRow = -1;
            }

            @Override
            public void deleteAllRows() {
                clear();
            }

            @Override
            public void firstRow() {
                if (!rows.isEmpty()) {
                    currentRow = 0;
                }
            }

            @Override
            public void lastRow() {
                if (!rows.isEmpty()) {
                    currentRow = rows.size() - 1;
                }
            }

            @Override
            public boolean nextRow() {
                if (currentRow < rows.size() - 1) {
                    currentRow++;
                    return true;
                }
                return false;
            }

            @Override
            public boolean previousRow() {
                if (currentRow > 0) {
                    currentRow--;
                    return true;
                }
                return false;
            }

            @Override
            public int getRow() {
                return currentRow;
            }

            @Override
            public void setRow(int i) {
                if (i >= 0 && i < rows.size()) {
                    currentRow = i;
                }
            }

            @Override
            public void appendRow() {
                currentRow = rows.size();
                rows.put(currentRow, new HashMap<>());
            }

            @Override
            public void appendRows(int i) {
                for (int j = 0; j < i; j++) {
                    appendRow();
                }
            }

            @Override
            public void insertRow(int i) {
                // Simplified for demonstration purposes
            }

            @Override
            public void deleteRow() {
                if (currentRow >= 0 && currentRow < rows.size()) {
                    rows.remove(currentRow);
                    if (currentRow >= rows.size()) {
                        currentRow = rows.size() - 1;
                    }
                }
            }

            @Override
            public void deleteRow(int i) {
                rows.remove(i);
                if (currentRow >= rows.size()) {
                    currentRow = rows.size() - 1;
                }
            }

            @Override
            public JCoRecordFieldIterator getRecordFieldIterator() {
                return null;
            }

            @Override
            public String getString() {
                return "Dummy String";
            }

            @Override
            public void setString(String s) {
            }

            @Override
            public JCoMetaData getMetaData() {
                return null;
            }

            @Override
            public int copyFrom(JCoRecord jCoRecord) {
                return 0;
            }

            @Override
            public int getFieldCount() {
                return rows.isEmpty() ? 0 : rows.get(0).size();
            }

            @Override
            public JCoField getField(int i) {
                return null;
            }

            @Override
            public JCoField getField(String s) {
                return null;
            }

            @Override
            public Object getValue(int i) {
                return null;
            }

            @Override
            public Object getValue(String s) {
                return rows.get(currentRow).get(s);
            }

            @Override
            public String getString(int i) {
                return "Dummy String";
            }

            @Override
            public String getString(String s) {
                Object value = rows.get(currentRow).get(s);
                return value != null ? value.toString() : "Dummy String";
            }

            @Override
            public char getChar(int i) {
                return 'D';
            }

            @Override
            public char getChar(String s) {
                return 'D';
            }

            @Override
            public char[] getCharArray(int i) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public char[] getCharArray(String s) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public byte getByte(int i) {
                return 1;
            }

            @Override
            public byte getByte(String s) {
                return 1;
            }

            @Override
            public byte[] getByteArray(int i) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public byte[] getByteArray(String s) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public short getShort(int i) {
                return 1;
            }

            @Override
            public short getShort(String s) {
                return 1;
            }

            @Override
            public int getInt(int i) {
                return 1;
            }

            @Override
            public int getInt(String s) {
                return 1;
            }

            @Override
            public long getLong(int i) {
                return 1L;
            }

            @Override
            public long getLong(String s) {
                return 1L;
            }

            @Override
            public float getFloat(int i) {
                return 1.0f;
            }

            @Override
            public float getFloat(String s) {
                return 1.0f;
            }

            @Override
            public double getDouble(int i) {
                return 1.0;
            }

            @Override
            public double getDouble(String s) {
                return 1.0;
            }

            @Override
            public BigInteger getBigInteger(int i) {
                return BigInteger.ONE;
            }

            @Override
            public BigInteger getBigInteger(String s) {
                return BigInteger.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(int i) {
                return BigDecimal.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(String s) {
                return BigDecimal.ONE;
            }

            @Override
            public Date getDate(int i) {
                return new Date();
            }

            @Override
            public Date getDate(String s) {
                return new Date();
            }

            @Override
            public Date getTime(int i) {
                return new Date();
            }

            @Override
            public Date getTime(String s) {
                return new Date();
            }

            @Override
            public InputStream getBinaryStream(int i) {
                return null;
            }

            @Override
            public InputStream getBinaryStream(String s) {
                return null;
            }

            @Override
            public Reader getCharacterStream(int i) {
                return null;
            }

            @Override
            public Reader getCharacterStream(String s) {
                return null;
            }

            @Override
            public JCoStructure getStructure(int i) {
                return createStructure();
            }

            @Override
            public JCoStructure getStructure(String s) {
                return createStructure();
            }

            @Override
            public JCoTable getTable(int i) {
                return createTable();
            }

            @Override
            public JCoTable getTable(String s) {
                return createTable();
            }

            @Override
            public JCoAbapObject getAbapObject(int i) {
                return null;
            }

            @Override
            public JCoAbapObject getAbapObject(String s) {
                return null;
            }

            @Override
            public String getClassNameOfValue(String s) {
                return "java.lang.String";
            }

            @Override
            public void setValue(int i, String s) {
                rows.get(currentRow).put(String.valueOf(i), s);
            }

            @Override
            public void setValue(String s, String s1) {
                rows.get(currentRow).put(s, s1);
            }

            @Override
            public void setValue(int i, char c) {
                rows.get(currentRow).put(String.valueOf(i), c);
            }

            @Override
            public void setValue(String s, char c) {
                rows.get(currentRow).put(s, c);
            }

            @Override
            public void setValue(int i, char[] chars) {
                rows.get(currentRow).put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars) {
                rows.get(currentRow).put(s, chars);
            }

            @Override
            public void setValue(int i, char[] chars, int i1, int i2) {
                rows.get(currentRow).put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars, int i, int i1) {
                rows.get(currentRow).put(s, chars);
            }

            @Override
            public void setValue(int i, Date date) {
                rows.get(currentRow).put(String.valueOf(i), date);
            }

            @Override
            public void setValue(String s, Date date) {
                rows.get(currentRow).put(s, date);
            }

            @Override
            public void setValue(int i, short i1) {
                rows.get(currentRow).put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, short i) {
                rows.get(currentRow).put(s, i);
            }

            @Override
            public void setValue(int i, int i1) {
                rows.get(currentRow).put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, int i) {
                rows.get(currentRow).put(s, i);
            }

            @Override
            public void setValue(int i, long l) {
                rows.get(currentRow).put(String.valueOf(i), l);
            }

            @Override
            public void setValue(String s, long l) {
                rows.get(currentRow).put(s, l);
            }

            @Override
            public void setValue(int i, float v) {
                rows.get(currentRow).put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, float v) {
                rows.get(currentRow).put(s, v);
            }

            @Override
            public void setValue(int i, double v) {
                rows.get(currentRow).put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, double v) {
                rows.get(currentRow).put(s, v);
            }

            @Override
            public void setValue(int i, byte b) {
                rows.get(currentRow).put(String.valueOf(i), b);
            }

            @Override
            public void setValue(String s, byte b) {
                rows.get(currentRow).put(s, b);
            }

            @Override
            public void setValue(int i, byte[] bytes) {
                rows.get(currentRow).put(String.valueOf(i), bytes);
            }

            @Override
            public void setValue(String s, byte[] bytes) {
                rows.get(currentRow).put(s, bytes);
            }

            @Override
            public void setValue(int i, BigDecimal bigDecimal) {
                rows.get(currentRow).put(String.valueOf(i), bigDecimal);
            }

            @Override
            public void setValue(String s, BigDecimal bigDecimal) {
                rows.get(currentRow).put(s, bigDecimal);
            }

            @Override
            public void setValue(int i, BigInteger bigInteger) {
                rows.get(currentRow).put(String.valueOf(i), bigInteger);
            }

            @Override
            public void setValue(String s, BigInteger bigInteger) {
                rows.get(currentRow).put(s, bigInteger);
            }

            @Override
            public void setValue(int i, JCoStructure jCoStructure) {
                rows.get(currentRow).put(String.valueOf(i), jCoStructure);
            }

            @Override
            public void setValue(String s, JCoStructure jCoStructure) {
                rows.get(currentRow).put(s, jCoStructure.toJSON());
            }

            @Override
            public void setValue(int i, JCoTable jCoTable) {
                rows.get(currentRow).put(String.valueOf(i), jCoTable);
            }

            @Override
            public void setValue(String s, JCoTable jCoTable) {
                rows.get(currentRow).put(s, jCoTable.toJSON());
            }

            @Override
            public void setValue(int i, JCoAbapObject jCoAbapObject) {
                rows.get(currentRow).put(String.valueOf(i), jCoAbapObject);
            }

            @Override
            public void setValue(String s, JCoAbapObject jCoAbapObject) {
                rows.get(currentRow).put(s, jCoAbapObject);
            }

            @Override
            public void setValue(int i, Object o) {
                rows.get(currentRow).put(String.valueOf(i), o);
            }

            @Override
            public void setValue(String s, Object o) {
                rows.get(currentRow).put(s, o);
            }

            @Override
            public boolean isInitialized(int i) {
                return rows.get(currentRow).containsKey(String.valueOf(i));
            }

            @Override
            public boolean isInitialized(String s) {
                return rows.get(currentRow).containsKey(s);
            }

            @Override
            public String toXML(int i) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML(String s) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML() {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toJSON() {
                Gson gson = new Gson();
                return gson.toJson(rows);
            }

            @Override
            public void toJSON(Writer writer) throws IOException {
                writer.write("{\"dummy\": \"value\"}");
            }

            @Override
            public void fromJSON(Reader reader) {
            }

            @Override
            public void fromJSON(String s) {
            }

            @Override
            public Writer write(int i, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Writer write(String s, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Iterator<JCoField> iterator() {
                return null;
            }

            @Override
            public JCoFieldIterator getFieldIterator() {
                return null;
            }

            @Override
            public Object clone() {
                try {
                    return super.clone();
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static JCoStructure createStructure() {
        return new JCoStructure() {
            private final Map<String, Object> fields = new HashMap<>();

            @Override
            public JCoRecordMetaData getRecordMetaData() {
                return null;
            }

            @Override
            public JCoRecordFieldIterator getRecordFieldIterator() {
                return null;
            }

            @Override
            public String getString() {
                return "Dummy String";
            }

            @Override
            public void setString(String s) {
            }

            @Override
            public JCoMetaData getMetaData() {
                return null;
            }

            @Override
            public void clear() {
                fields.clear();
            }

            @Override
            public int copyFrom(JCoRecord jCoRecord) {
                return 0;
            }

            @Override
            public int getFieldCount() {
                return fields.size();
            }

            @Override
            public JCoField getField(int i) {
                return null;
            }

            @Override
            public JCoField getField(String s) {
                return null;
            }

            @Override
            public Object getValue(int i) {
                return null;
            }

            @Override
            public Object getValue(String s) {
                return fields.get(s);
            }

            @Override
            public String getString(int i) {
                return "Dummy String";
            }

            @Override
            public String getString(String s) {
                Object value = fields.get(s);
                return value != null ? value.toString() : "Dummy String";
            }

            @Override
            public char getChar(int i) {
                return 'D';
            }

            @Override
            public char getChar(String s) {
                return 'D';
            }

            @Override
            public char[] getCharArray(int i) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public char[] getCharArray(String s) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public byte getByte(int i) {
                return 1;
            }

            @Override
            public byte getByte(String s) {
                return 1;
            }

            @Override
            public byte[] getByteArray(int i) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public byte[] getByteArray(String s) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public short getShort(int i) {
                return 1;
            }

            @Override
            public short getShort(String s) {
                return 1;
            }

            @Override
            public int getInt(int i) {
                return 1;
            }

            @Override
            public int getInt(String s) {
                return 1;
            }

            @Override
            public long getLong(int i) {
                return 1L;
            }

            @Override
            public long getLong(String s) {
                return 1L;
            }

            @Override
            public float getFloat(int i) {
                return 1.0f;
            }

            @Override
            public float getFloat(String s) {
                return 1.0f;
            }

            @Override
            public double getDouble(int i) {
                return 1.0;
            }

            @Override
            public double getDouble(String s) {
                return 1.0;
            }

            @Override
            public BigInteger getBigInteger(int i) {
                return BigInteger.ONE;
            }

            @Override
            public BigInteger getBigInteger(String s) {
                return BigInteger.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(int i) {
                return BigDecimal.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(String s) {
                return BigDecimal.ONE;
            }

            @Override
            public Date getDate(int i) {
                return new Date();
            }

            @Override
            public Date getDate(String s) {
                return new Date();
            }

            @Override
            public Date getTime(int i) {
                return new Date();
            }

            @Override
            public Date getTime(String s) {
                return new Date();
            }

            @Override
            public InputStream getBinaryStream(int i) {
                return null;
            }

            @Override
            public InputStream getBinaryStream(String s) {
                return null;
            }

            @Override
            public Reader getCharacterStream(int i) {
                return null;
            }

            @Override
            public Reader getCharacterStream(String s) {
                return null;
            }

            @Override
            public JCoStructure getStructure(int i) {
                return createStructure();
            }

            @Override
            public JCoStructure getStructure(String s) {
                return createStructure();
            }

            @Override
            public JCoTable getTable(int i) {
                return createTable();
            }

            @Override
            public JCoTable getTable(String s) {
                return createTable();
            }

            @Override
            public JCoAbapObject getAbapObject(int i) {
                return null;
            }

            @Override
            public JCoAbapObject getAbapObject(String s) {
                return null;
            }

            @Override
            public String getClassNameOfValue(String s) {
                return "java.lang.String";
            }

            @Override
            public void setValue(int i, String s) {
                fields.put(String.valueOf(i), s);
            }

            @Override
            public void setValue(String s, String s1) {
                fields.put(s, s1);
            }

            @Override
            public void setValue(int i, char c) {
                fields.put(String.valueOf(i), c);
            }

            @Override
            public void setValue(String s, char c) {
                fields.put(s, c);
            }

            @Override
            public void setValue(int i, char[] chars) {
                fields.put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars) {
                fields.put(s, chars);
            }

            @Override
            public void setValue(int i, char[] chars, int i1, int i2) {
                fields.put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars, int i, int i1) {
                fields.put(s, chars);
            }

            @Override
            public void setValue(int i, Date date) {
                fields.put(String.valueOf(i), date);
            }

            @Override
            public void setValue(String s, Date date) {
                fields.put(s, date);
            }

            @Override
            public void setValue(int i, short i1) {
                fields.put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, short i) {
                fields.put(s, i);
            }

            @Override
            public void setValue(int i, int i1) {
                fields.put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, int i) {
                fields.put(s, i);
            }

            @Override
            public void setValue(int i, long l) {
                fields.put(String.valueOf(i), l);
            }

            @Override
            public void setValue(String s, long l) {
                fields.put(s, l);
            }

            @Override
            public void setValue(int i, float v) {
                fields.put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, float v) {
                fields.put(s, v);
            }

            @Override
            public void setValue(int i, double v) {
                fields.put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, double v) {
                fields.put(s, v);
            }

            @Override
            public void setValue(int i, byte b) {
                fields.put(String.valueOf(i), b);
            }

            @Override
            public void setValue(String s, byte b) {
                fields.put(s, b);
            }

            @Override
            public void setValue(int i, byte[] bytes) {
                fields.put(String.valueOf(i), bytes);
            }

            @Override
            public void setValue(String s, byte[] bytes) {
                fields.put(s, bytes);
            }

            @Override
            public void setValue(int i, BigDecimal bigDecimal) {
                fields.put(String.valueOf(i), bigDecimal);
            }

            @Override
            public void setValue(String s, BigDecimal bigDecimal) {
                fields.put(s, bigDecimal);
            }

            @Override
            public void setValue(int i, BigInteger bigInteger) {
                fields.put(String.valueOf(i), bigInteger);
            }

            @Override
            public void setValue(String s, BigInteger bigInteger) {
                fields.put(s, bigInteger);
            }

            @Override
            public void setValue(int i, JCoStructure jCoStructure) {
                fields.put(String.valueOf(i), jCoStructure);
            }

            @Override
            public void setValue(String s, JCoStructure jCoStructure) {
                fields.put(s, jCoStructure.toJSON());
            }

            @Override
            public void setValue(int i, JCoTable jCoTable) {
                fields.put(String.valueOf(i), jCoTable);
            }

            @Override
            public void setValue(String s, JCoTable jCoTable) {
                fields.put(s, jCoTable.toJSON());
            }

            @Override
            public void setValue(int i, JCoAbapObject jCoAbapObject) {
                fields.put(String.valueOf(i), jCoAbapObject);
            }

            @Override
            public void setValue(String s, JCoAbapObject jCoAbapObject) {
                fields.put(s, jCoAbapObject);
            }

            @Override
            public void setValue(int i, Object o) {
                fields.put(String.valueOf(i), o);
            }

            @Override
            public void setValue(String s, Object o) {
                fields.put(s, o);
            }

            @Override
            public boolean isInitialized(int i) {
                return fields.containsKey(String.valueOf(i));
            }

            @Override
            public boolean isInitialized(String s) {
                return fields.containsKey(s);
            }

            @Override
            public String toXML(int i) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML(String s) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML() {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toJSON() {
                Gson gson = new Gson();
                return gson.toJson(fields);
            }

            @Override
            public void toJSON(Writer writer) throws IOException {
                writer.write("{\"dummy\": \"value\"}");
            }

            @Override
            public void fromJSON(Reader reader) {
            }

            @Override
            public void fromJSON(String s) {
            }

            @Override
            public Writer write(int i, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Writer write(String s, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Iterator<JCoField> iterator() {
                return null;
            }

            @Override
            public JCoFieldIterator getFieldIterator() {
                return null;
            }

            @Override
            public Object clone() {
                try {
                    return super.clone();
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static JCoParameterList createList() {
        return new JCoParameterList() {
            private final Map<String, Object> fields = new HashMap<>();

            @Override
            public JCoListMetaData getListMetaData() {
                return null;
            }

            @Override
            public JCoParameterFieldIterator getParameterFieldIterator() {
                return null;
            }

            @Override
            public boolean isActive(int i) {
                return false;
            }

            @Override
            public boolean isActive(String s) {
                return false;
            }

            @Override
            public void setActive(int i, boolean b) {
            }

            @Override
            public void setActive(String s, boolean b) {
            }

            @Override
            public JCoMetaData getMetaData() {
                return null;
            }

            @Override
            public void clear() {
                fields.clear();
            }

            @Override
            public int copyFrom(JCoRecord jCoRecord) {
                return 0;
            }

            @Override
            public int getFieldCount() {
                return fields.size();
            }

            @Override
            public JCoField getField(int i) {
                return null;
            }

            @Override
            public JCoField getField(String s) {
                return null;
            }

            @Override
            public Object getValue(int i) {
                return null;
            }

            @Override
            public Object getValue(String s) {
                return fields.get(s);
            }

            @Override
            public String getString(int i) {
                return "Dummy String";
            }

            @Override
            public String getString(String s) {
                Object value = fields.get(s);
                return value != null ? value.toString() : "Dummy String";
            }

            @Override
            public char getChar(int i) {
                return 'D';
            }

            @Override
            public char getChar(String s) {
                return 'D';
            }

            @Override
            public char[] getCharArray(int i) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public char[] getCharArray(String s) {
                return new char[]{'D', 'u', 'm', 'm', 'y'};
            }

            @Override
            public byte getByte(int i) {
                return 1;
            }

            @Override
            public byte getByte(String s) {
                return 1;
            }

            @Override
            public byte[] getByteArray(int i) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public byte[] getByteArray(String s) {
                return new byte[]{1, 2, 3};
            }

            @Override
            public short getShort(int i) {
                return 1;
            }

            @Override
            public short getShort(String s) {
                return 1;
            }

            @Override
            public int getInt(int i) {
                return 1;
            }

            @Override
            public int getInt(String s) {
                return 1;
            }

            @Override
            public long getLong(int i) {
                return 1L;
            }

            @Override
            public long getLong(String s) {
                return 1L;
            }

            @Override
            public float getFloat(int i) {
                return 1.0f;
            }

            @Override
            public float getFloat(String s) {
                return 1.0f;
            }

            @Override
            public double getDouble(int i) {
                return 1.0;
            }

            @Override
            public double getDouble(String s) {
                return 1.0;
            }

            @Override
            public BigInteger getBigInteger(int i) {
                return BigInteger.ONE;
            }

            @Override
            public BigInteger getBigInteger(String s) {
                return BigInteger.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(int i) {
                return BigDecimal.ONE;
            }

            @Override
            public BigDecimal getBigDecimal(String s) {
                return BigDecimal.ONE;
            }

            @Override
            public Date getDate(int i) {
                return new Date();
            }

            @Override
            public Date getDate(String s) {
                return new Date();
            }

            @Override
            public Date getTime(int i) {
                return new Date();
            }

            @Override
            public Date getTime(String s) {
                return new Date();
            }

            @Override
            public InputStream getBinaryStream(int i) {
                return null;
            }

            @Override
            public InputStream getBinaryStream(String s) {
                return null;
            }

            @Override
            public Reader getCharacterStream(int i) {
                return null;
            }

            @Override
            public Reader getCharacterStream(String s) {
                return null;
            }

            @Override
            public JCoStructure getStructure(int i) {
                return createStructure();
            }

            @Override
            public JCoStructure getStructure(String s) {
                return createStructure();
            }

            @Override
            public JCoTable getTable(int i) {
                return createTable();
            }

            @Override
            public JCoTable getTable(String s) {
                return createTable();
            }

            @Override
            public JCoAbapObject getAbapObject(int i) {
                return null;
            }

            @Override
            public JCoAbapObject getAbapObject(String s) {
                return null;
            }

            @Override
            public String getClassNameOfValue(String s) {
                return "java.lang.String";
            }

            @Override
            public void setValue(int i, String s) {
                fields.put(String.valueOf(i), s);
            }

            @Override
            public void setValue(String s, String s1) {
                fields.put(s, s1);
            }

            @Override
            public void setValue(int i, char c) {
                fields.put(String.valueOf(i), c);
            }

            @Override
            public void setValue(String s, char c) {
                fields.put(s, c);
            }

            @Override
            public void setValue(int i, char[] chars) {
                fields.put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars) {
                fields.put(s, chars);
            }

            @Override
            public void setValue(int i, char[] chars, int i1, int i2) {
                fields.put(String.valueOf(i), chars);
            }

            @Override
            public void setValue(String s, char[] chars, int i, int i1) {
                fields.put(s, chars);
            }

            @Override
            public void setValue(int i, Date date) {
                fields.put(String.valueOf(i), date);
            }

            @Override
            public void setValue(String s, Date date) {
                fields.put(s, date);
            }

            @Override
            public void setValue(int i, short i1) {
                fields.put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, short i) {
                fields.put(s, i);
            }

            @Override
            public void setValue(int i, int i1) {
                fields.put(String.valueOf(i), i1);
            }

            @Override
            public void setValue(String s, int i) {
                fields.put(s, i);
            }

            @Override
            public void setValue(int i, long l) {
                fields.put(String.valueOf(i), l);
            }

            @Override
            public void setValue(String s, long l) {
                fields.put(s, l);
            }

            @Override
            public void setValue(int i, float v) {
                fields.put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, float v) {
                fields.put(s, v);
            }

            @Override
            public void setValue(int i, double v) {
                fields.put(String.valueOf(i), v);
            }

            @Override
            public void setValue(String s, double v) {
                fields.put(s, v);
            }

            @Override
            public void setValue(int i, byte b) {
                fields.put(String.valueOf(i), b);
            }

            @Override
            public void setValue(String s, byte b) {
                fields.put(s, b);
            }

            @Override
            public void setValue(int i, byte[] bytes) {
                fields.put(String.valueOf(i), bytes);
            }

            @Override
            public void setValue(String s, byte[] bytes) {
                fields.put(s, bytes);
            }

            @Override
            public void setValue(int i, BigDecimal bigDecimal) {
                fields.put(String.valueOf(i), bigDecimal);
            }

            @Override
            public void setValue(String s, BigDecimal bigDecimal) {
                fields.put(s, bigDecimal);
            }

            @Override
            public void setValue(int i, BigInteger bigInteger) {
                fields.put(String.valueOf(i), bigInteger);
            }

            @Override
            public void setValue(String s, BigInteger bigInteger) {
                fields.put(s, bigInteger);
            }

            @Override
            public void setValue(int i, JCoStructure jCoStructure) {
                fields.put(String.valueOf(i), jCoStructure);
            }

            @Override
            public void setValue(String s, JCoStructure jCoStructure) {
                fields.put(s, jCoStructure.toJSON());
            }

            @Override
            public void setValue(int i, JCoTable jCoTable) {
                fields.put(String.valueOf(i), jCoTable);
            }

            @Override
            public void setValue(String s, JCoTable jCoTable) {
                fields.put(s, jCoTable.toJSON());
            }

            @Override
            public void setValue(int i, JCoAbapObject jCoAbapObject) {
                fields.put(String.valueOf(i), jCoAbapObject);
            }

            @Override
            public void setValue(String s, JCoAbapObject jCoAbapObject) {
                fields.put(s, jCoAbapObject);
            }

            @Override
            public void setValue(int i, Object o) {
                fields.put(String.valueOf(i), o);
            }

            @Override
            public void setValue(String s, Object o) {
                fields.put(s, o);
            }

            @Override
            public boolean isInitialized(int i) {
                return fields.containsKey(String.valueOf(i));
            }

            @Override
            public boolean isInitialized(String s) {
                return fields.containsKey(s);
            }

            @Override
            public String toXML(int i) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML(String s) {
                return "<dummyXML></dummyXML>";
            }

            @Override
            public String toXML() {
                Gson gson = new Gson();
                return "<dummyXML>" + gson.toJson(fields) + "</dummyXML>";
            }

            @Override
            public String toJSON() {
                Gson gson = new Gson();
                return gson.toJson(fields);
            }

            @Override
            public void toJSON(Writer writer) throws IOException {
                writer.write("{\"dummy\": \"value\"}");
            }

            @Override
            public void fromJSON(Reader reader) {
            }

            @Override
            public void fromJSON(String s) {
            }

            @Override
            public Writer write(int i, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Writer write(String s, Writer writer) throws IOException {
                return null;
            }

            @Override
            public Iterator<JCoField> iterator() {
                return null;
            }

            @Override
            public JCoFieldIterator getFieldIterator() {
                return null;
            }

            @Override
            public Object clone() {
                try {
                    return super.clone();
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}

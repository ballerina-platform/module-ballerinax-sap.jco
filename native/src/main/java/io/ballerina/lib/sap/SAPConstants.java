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

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

public class SAPConstants {

    // Constants for SAP Client
    public static final String RFC_DESTINATION = "RFC_DESTINATION";
    public static final String RFC_DESTINATION_ID = "RFC_DESTINATION_ID";
    public static final String DATE = "Date";
    public static final String TIME_OF_DAY = "TimeOfDay";

    // Config Types
    public static final String JCO_SERVER_PREFIX = "jco.server.";

    // Constants for SAP Listener
    public static final String JCO_SERVER = "JCO_SERVER";
    public static final String ON_RECEIVE = "onReceive";
    public static final String ON_ERROR = "onError";
    public static final String ON_CALL = "onCall";
    public static final String IS_STARTED = "isStarted";

    // RfcParameters field names
    public static final BString RFC_IMPORT_PARAMETERS = StringUtils.fromString("importParameters");
    public static final BString RFC_TABLE_PARAMETERS = StringUtils.fromString("tableParameters");


    // Class names
    public static final String JCO_STRING = "java.lang.String";
    public static final String JCO_DATE = "java.util.Date";
    public static final String JCO_BIG_DECIMAL = "java.math.BigDecimal";
    public static final String JCO_BYTE_ARRAY = "byte[]";
    public static final String JCO_OBJECT = "java.lang.Object";
    public static final String JCO_DOUBLE = "java.lang.Double";
    public static final String JCO_INTEGER = "java.lang.Integer";
    public static final String JCO_STRUCTURE = "com.sap.conn.jco.JCoStructure";
    public static final String JCO_TABLE = "com.sap.conn.jco.JCoTable";
    public static final String JCO_LONG = "java.lang.Long";
    public static final String JCO_BOOLEAN = "java.lang.Boolean";

    // Sub date types
    public static final String JCO_DATE_TYPE_DATE = "DATE";
    public static final String JCO_DATE_TYPE_TIME = "TIME";

    // Ballerina error type names — must match the type declarations in types.bal exactly
    public static final String CONNECTION_ERROR_TYPE        = "ConnectionError";
    public static final String LOGON_ERROR_TYPE             = "LogonError";
    public static final String RESOURCE_ERROR_TYPE          = "ResourceError";
    public static final String SYSTEM_ERROR_TYPE            = "SystemError";
    public static final String ABAP_APPLICATION_ERROR_TYPE  = "AbapApplicationError";
    public static final String JCO_ERROR_TYPE               = "JCoError";
    public static final String IDOC_ERROR_TYPE              = "IDocError";
    public static final String PARAMETER_ERROR_TYPE         = "ParameterError";
    public static final String CONFIGURATION_ERROR_TYPE     = "ConfigurationError";
    public static final String EXECUTION_ERROR_TYPE         = "ExecutionError";

    // JCoErrorDetail field keys
    public static final BString DETAIL_ERROR_GROUP      = StringUtils.fromString("errorGroup");
    public static final BString DETAIL_KEY              = StringUtils.fromString("key");

    // AbapApplicationErrorDetail field keys (reserved for future use when the JCo version
    // exposes individual ABAP message part accessors on JCoException)
    public static final BString DETAIL_ABAP_MSG_CLASS   = StringUtils.fromString("abapMsgClass");
    public static final BString DETAIL_ABAP_MSG_TYPE    = StringUtils.fromString("abapMsgType");
    public static final BString DETAIL_ABAP_MSG_NUMBER  = StringUtils.fromString("abapMsgNumber");
    public static final BString DETAIL_ABAP_MSG_V1      = StringUtils.fromString("abapMsgV1");
    public static final BString DETAIL_ABAP_MSG_V2      = StringUtils.fromString("abapMsgV2");
    public static final BString DETAIL_ABAP_MSG_V3      = StringUtils.fromString("abapMsgV3");
    public static final BString DETAIL_ABAP_MSG_V4      = StringUtils.fromString("abapMsgV4");
}

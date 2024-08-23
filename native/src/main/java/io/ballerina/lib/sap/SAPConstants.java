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
    public static final String DATE = "Date";
    public static final String TIME_OF_DAY = "TimeOfDay";

    // Server Configs
    public static final BString JCO_GWHOST = StringUtils.fromString("gwhost");
    public static final BString JCO_GWSERV = StringUtils.fromString("gwserv");
    public static final BString JCO_PROGID = StringUtils.fromString("progid");
    public static final BString ADVANCED_CONFIGS = StringUtils.fromString("advancedConfigs");


    // Destination Configs
    public static final BString JCO_CLIENT = StringUtils.fromString("jcoClient");
    public static final BString JCO_USER = StringUtils.fromString("user");
    public static final BString JCO_PASSWD = StringUtils.fromString("passwd");
    public static final BString JCO_LANG = StringUtils.fromString("lang");
    public static final BString JCO_ASHOST = StringUtils.fromString("ashost");
    public static final BString JCO_SYSNR = StringUtils.fromString("sysnr");
    public static final BString JCO_GROUP = StringUtils.fromString("group");


    // Constants for SAP Listener
    public static final String JCO_SERVER = "JCO_SERVER";
    public static final String ON_RECEIVE = "onReceive";
    public static final String ON_ERROR = "onError";
    public static final String IS_SERVICE_ATTACHED = "isServiceAttached";


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

    // Sub date types
    public static final String JCO_DATE_TYPE_DATE = "DATE";
    public static final String JCO_DATE_TYPE_TIME = "TIME";
}

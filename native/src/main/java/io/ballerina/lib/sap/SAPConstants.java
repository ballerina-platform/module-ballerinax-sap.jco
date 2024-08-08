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

import com.sap.conn.jco.ext.DestinationDataProvider;

import java.util.Map;

public class SAPConstants {

    // Constants for SAP Client
    public static final String RETURN = "RETURN";
    public static final String RFC_DESTINATION = "RFC_DESTINATION";
    public static final String TYPE = "TYPE";
    public static final String MESSAGE = "MESSAGE";
    public static final String S = "S";
    public static final String SET_NULL = "setImportParamNull";
    public static final String DATE = "Date";
    public static final String TIME_OF_DAY = "TimeOfDay";

    // Constants for SAP Destination Provider Configurations
    public static final Map<String, String> CONFIG_KEYS = Map.<String, String>ofEntries(
            Map.entry("jcoClient", DestinationDataProvider.JCO_CLIENT),
            Map.entry("user", DestinationDataProvider.JCO_USER),
            Map.entry("password", DestinationDataProvider.JCO_PASSWD),
            Map.entry("language", DestinationDataProvider.JCO_LANG),
            Map.entry("host", DestinationDataProvider.JCO_ASHOST),
            Map.entry("systemNumber", DestinationDataProvider.JCO_SYSNR),
            Map.entry("group", DestinationDataProvider.JCO_GROUP),
            Map.entry("authType", DestinationDataProvider.JCO_AUTH_TYPE),
            Map.entry("codePage", DestinationDataProvider.JCO_CODEPAGE),
            Map.entry("aliasUser", DestinationDataProvider.JCO_ALIAS_USER),
            Map.entry("pcs", DestinationDataProvider.JCO_PCS),
            Map.entry("msHost", DestinationDataProvider.JCO_MSHOST),
            Map.entry("msServer", DestinationDataProvider.JCO_MSSERV),
            Map.entry("r3Name", DestinationDataProvider.JCO_R3NAME),
            Map.entry("sticky", DestinationDataProvider.JCO_STICKY_ASHOST),
            Map.entry("sapRouter", DestinationDataProvider.JCO_SAPROUTER),
            Map.entry("mySapSso2", DestinationDataProvider.JCO_MYSAPSSO2),
            Map.entry("getSso2", DestinationDataProvider.JCO_GETSSO2),
            Map.entry("x509Cert", DestinationDataProvider.JCO_X509CERT),
            Map.entry("oidcBearerToken", DestinationDataProvider.JCO_OIDC_BEARER_TOKEN),
            Map.entry("extIdData", DestinationDataProvider.JCO_EXTID_DATA),
            Map.entry("extIdType", DestinationDataProvider.JCO_EXTID_TYPE),
            Map.entry("lCheck", DestinationDataProvider.JCO_LCHECK),
            Map.entry("useBasXml", DestinationDataProvider.JCO_USE_BASXML),
            Map.entry("network", DestinationDataProvider.JCO_CLIENT),
            Map.entry("serializationFormat", DestinationDataProvider.JCO_SERIALIZATION_FORMAT),
            Map.entry("delta", DestinationDataProvider.JCO_DELTA),
            Map.entry("sncMode", DestinationDataProvider.JCO_SNC_MODE),
            Map.entry("sncSso", DestinationDataProvider.JCO_SNC_SSO),
            Map.entry("sncPartnerName", DestinationDataProvider.JCO_SNC_PARTNERNAME),
            Map.entry("sncQop", DestinationDataProvider.JCO_SNC_QOP),
            Map.entry("sncMyName", DestinationDataProvider.JCO_SNC_MYNAME),
            Map.entry("peakLimit", DestinationDataProvider.JCO_PEAK_LIMIT),
            Map.entry("poolCapacity", DestinationDataProvider.JCO_POOL_CAPACITY),
            Map.entry("expirationTime", DestinationDataProvider.JCO_EXPIRATION_TIME),
            Map.entry("expirationCheckPeriod", DestinationDataProvider.JCO_EXPIRATION_PERIOD),
            Map.entry("maxGetClientTime", DestinationDataProvider.JCO_MAX_GET_TIME),
            Map.entry("poolCheckConnection", DestinationDataProvider.JCO_POOL_CHECK_CONNECTION),
            Map.entry("repositoryDestination", DestinationDataProvider.JCO_REPOSITORY_DEST),
            Map.entry("repositoryUser", DestinationDataProvider.JCO_REPOSITORY_USER),
            Map.entry("repositoryPassword", DestinationDataProvider.JCO_REPOSITORY_PASSWD),
            Map.entry("repositorySncMode", DestinationDataProvider.JCO_REPOSITORY_SNC),
            Map.entry("repositoryCheckInterval", DestinationDataProvider.JCO_REPOSITORY_CHECK_INTERVAL),
            Map.entry("trace", DestinationDataProvider.JCO_TRACE),
            Map.entry("gwHost", DestinationDataProvider.JCO_GWHOST),
            Map.entry("gwServ", DestinationDataProvider.JCO_GWSERV),
            Map.entry("tpHost", DestinationDataProvider.JCO_TPHOST),
            Map.entry("tpName", DestinationDataProvider.JCO_TPNAME),
            Map.entry("wsHost", DestinationDataProvider.JCO_WSHOST),
            Map.entry("wsPort", DestinationDataProvider.JCO_WSPORT),
            Map.entry("useTls", DestinationDataProvider.JCO_USE_TLS),
            Map.entry("tlsTrustAll", DestinationDataProvider.JCO_TLS_TRUST_ALL),
            Map.entry("tlsP12File", DestinationDataProvider.JCO_TLS_P12FILE),
            Map.entry("tlsP12Password", DestinationDataProvider.JCO_TLS_P12PASSWD),
            Map.entry("tlsClientCertificateLogon", DestinationDataProvider.JCO_TLS_CLIENT_CERTIFICATE_LOGON),
            Map.entry("proxyHost", DestinationDataProvider.JCO_PROXY_HOST),
            Map.entry("proxyPort", DestinationDataProvider.JCO_PROXY_PORT),
            Map.entry("proxyUser", DestinationDataProvider.JCO_PROXY_USER),
            Map.entry("proxyPassword", DestinationDataProvider.JCO_PROXY_PASSWD),
            Map.entry("wsPingCheckInterval", DestinationDataProvider.JCO_PING_CHECK_INTERVAL),
            Map.entry("wsPingPeriod", DestinationDataProvider.JCO_PING_PERIOD),
            Map.entry("wsPongTimeout", DestinationDataProvider.JCO_PONG_TIMEOUT),
            Map.entry("jcoType", DestinationDataProvider.JCO_TYPE),
            Map.entry("useSapGui", DestinationDataProvider.JCO_USE_SAPGUI),
            Map.entry("denyInitialPassword", DestinationDataProvider.JCO_DENY_INITIAL_PASSWORD),
            Map.entry("repositoryRoundtripOptimization", DestinationDataProvider.JCO_REPOSITORY_ROUNDTRIP_OPTIMIZATION)
    );

    // Constants for SAP Listener
    public static final String JCO_SERVER = "JCO_SERVER";
    public static final String JCO_SERVICES = "JCO_SERVICES";
    public static final String JCO_STARTED_SERVICES = "JCO_STARTED_SERVICES";
    public static final String ON_RECEIVE = "onReceive";
    public static final String ON_ERROR = "onError";


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

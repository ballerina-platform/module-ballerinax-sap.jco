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
import ballerina/time;

# Holds the configuration details needed to create a BAPI connection.
#
# + host - The SAP host name (jco.client.ashost).
# + systemNumber - The SAP system number (jco.client.sysnr).
# + jcoClient - The SAP client (jco.client.client).
# + user - The SAP user name (jco.client.user).
# + password - The SAP password (jco.client.passwd).
# + language - The SAP language (jco.client.lang).
# + group - The SAP group (jco.client.group).
# + authType - The SAP authentication type (jco.destination.auth_type).
# + codePage - The SAP code page (jco.client.codepage).
# + aliasUser - The SAP alias user (jco.client.alias_user).
# + pcs - The SAP PCS (jco.client.pcs).
# + msHost - The SAP MS host (jco.client.mshost).
# + msServer - The SAP MS server (jco.client.msserv).
# + r3Name - The SAP R3 name (jco.client.r3name).
# + sticky - The SAP sticky (jco.client.sticky).
# + sapRouter - The SAP SAP router (jco.client.saprouter).
# + mySapSso2 - The SAP My SAP SSO2 (jco.client.mysapsso2).
# + getSso2 - The SAP get SSO2 (jco.client.getsso2).
# + x509Cert - The SAP X509 certificate (jco.client.x509cert).
# + oidcBearerToken - The SAP OIDC bearer token (jco.client.oidc_bearer_token).
# + extIdData - The SAP external ID data (jco.client.extid_data).
# + extIdType - The SAP external ID type (jco.client.extid_type).
# + lCheck - The SAP LCheck (jco.client.lcheck).
# + useBasXml - The SAP use BasXML (jco.client.use_basxml).
# + network - The SAP network (jco.client.network).
# + serializationFormat - The SAP serialization format (jco.client.serialization_format).
# + delta - The SAP delta (jco.client.delta).
# + sncMode - The SAP SNC mode (jco.client.snc_mode).
# + sncSso - The SAP SNC SSO (jco.client.snc_sso).
# + sncPartnerName - The SAP SNC partner name (jco.client.snc_partnername).
# + sncQop - The SAP SNC QOP (jco.client.snc_qop).
# + sncMyName - The SAP SNC my name (jco.client.snc_myname).
# + peakLimit - The SAP peak limit (jco.destination.peak_limit).
# + poolCapacity - The SAP pool capacity (jco.destination.pool_capacity).
# + expirationTime - The SAP expiration time (jco.destination.expiration_time).
# + expirationCheckPeriod - The SAP expiration check period (jco.destination.expiration_check_period).
# + maxGetClientTime - The SAP max get client time (jco.destination.max_get_client_time).
# + poolCheckConnection - The SAP pool check connection (jco.destination.pool_check_connection).
# + repositoryDestination - The SAP repository destination (jco.destination.repository_destination).
# + repositoryUser - The SAP repository user (jco.destination.repository.user).
# + repositoryPassword - The SAP repository password (jco.destination.repository.passwd).
# + repositorySncMode - The SAP repository SNC mode (jco.destination.repository.snc_mode).
# + repositoryCheckInterval - The SAP repository check interval (jco.destination.repository.check_interval).
# + trace - The SAP trace (jco.client.trace).
# + gwHost - The SAP GW host (jco.client.gwhost).
# + gwServ - The SAP GW server (jco.client.gwserv).
# + tpHost - The SAP TP host (jco.client.tphost).
# + tpName - The SAP TP name (jco.client.tpname).
# + wsHost - The SAP WS host (jco.client.wshost).
# + wsPort - The SAP WS port (jco.client.wsport).
# + useTls - The SAP use TLS (jco.client.use_tls).
# + tlsTrustAll - The SAP TLS trust all (jco.client.tls_trust_all).
# + tlsP12File - The SAP TLS P12 file (jco.client.tls_p12_file).
# + tlsP12Password - The SAP TLS P12 password (jco.client.tls_p12_passwd).
# + tlsClientCertificateLogon - The SAP TLS client certificate logon (jco.client.tls_client_certificate_logon).
# + proxyHost - The SAP proxy host (jco.client.proxy_host).
# + proxyPort - The SAP proxy port (jco.client.proxy_port).
# + proxyUser - The SAP proxy user (jco.client.proxy_user).
# + proxyPassword - The SAP proxy password (jco.client.proxy_passwd).
# + wsPingCheckInterval - The SAP WS ping check interval (jco.destination.ws_ping_check_interval).
# + wsPingPeriod - The SAP WS ping period (jco.destination.ws_ping_period).
# + wsPongTimeout - The SAP WS pong timeout (jco.destination.ws_pong_timeout).
# + jco_type - The SAP type (jco.client.type).
# + useSapGui - The SAP use SAP GUI (jco.client.use_sapgui).
# + denyInitialPassword - The SAP deny initial password (jco.client.deny_initial_password).
# + repositoryRoundtripOptimization - The SAP repository roundtrip optimization (jco.destination.repository_roundtrip_optimization).
public type DestinationConfig record {
    @display {label: "Host Name (jco.client.ashost)"}
    string host;
    @display {label: "System Number (jco.client.sysnr)"}
    string systemNumber;
    @display {label: "Client (jco.client.client)"}
    string jcoClient;
    @display {label: "User Name (jco.client.user)"}
    string user;
    @display {label: "Password (jco.client.passwd)"}
    string password;
    @display {label: "Language (jco.client.lang)"}
    string language = "EN";
    @display {label: "Group (jco.client.group)"}
    string group = "PUBLIC";
    @display {label: "Auth Type (jco.destination.auth_type)"}
    string authType?;
    @display {label: "Code Page (jco.client.codepage)"}
    string codePage?;
    @display {label: "Alias User (jco.client.alias_user)"}
    string aliasUser?;
    @display {label: "PCS (jco.client.pcs)"}
    string pcs?;
    @display {label: "MS Host (jco.client.mshost)"}
    string msHost?;
    @display {label: "MS Server (jco.client.msserv)"}
    string msServer?;
    @display {label: "R3 Name (jco.client.r3name)"}
    string r3Name?;
    @display {label: "Sticky (jco.client.sticky)"}
    string sticky?;
    @display {label: "SAP Router (jco.client.saprouter)"}
    string sapRouter?;
    @display {label: "My SAP SSO2 (jco.client.mysapsso2)"}
    string mySapSso2?;
    @display {label: "Get SSO2 (jco.client.getsso2)"}
    string getSso2?;
    @display {label: "X509 Cert (jco.client.x509cert)"}
    string x509Cert?;
    @display {label: "OIDC Bearer Token (jco.client.oidc_bearer_token)"}
    string oidcBearerToken?;
    @display {label: "Ext ID Data (jco.client.extid_data)"}
    string extIdData?;
    @display {label: "Ext ID Type (jco.client.extid_type)"}
    string extIdType?;
    @display {label: "LCheck (jco.client.lcheck)"}
    string lCheck?;
    @display {label: "Use BasXML (jco.client.use_basxml)"}
    string useBasXml?;
    @display {label: "Network (jco.client.network)"}
    string network?;
    @display {label: "Serialization Format (jco.client.serialization_format)"}
    string serializationFormat?;
    @display {label: "Delta (jco.client.delta)"}
    string delta?;
    @display {label: "SNC Mode (jco.client.snc_mode)"}
    string sncMode?;
    @display {label: "SNC SSO (jco.client.snc_sso)"}
    string sncSso?;
    @display {label: "SNC Partner Name (jco.client.snc_partnername)"}
    string sncPartnerName?;
    @display {label: "SNC QOP (jco.client.snc_qop)"}
    string sncQop?;
    @display {label: "SNC My Name (jco.client.snc_myname)"}
    string sncMyName?;
    @display {label: "Peak Limit (jco.destination.peak_limit)"}
    string peakLimit?;
    @display {label: "Pool Capacity (jco.destination.pool_capacity)"}
    string poolCapacity?;
    @display {label: "Expiration Time (jco.destination.expiration_time)"}
    string expirationTime?;
    @display {label: "Expiration Check Period (jco.destination.expiration_check_period)"}
    string expirationCheckPeriod?;
    @display {label: "Max Get Client Time (jco.destination.max_get_client_time)"}
    string maxGetClientTime?;
    @display {label: "Pool Check Connection (jco.destination.pool_check_connection)"}
    string poolCheckConnection?;
    @display {label: "Repository Destination (jco.destination.repository_destination)"}
    string repositoryDestination?;
    @display {label: "Repository User (jco.destination.repository.user)"}
    string repositoryUser?;
    @display {label: "Repository Password (jco.destination.repository.passwd)"}
    string repositoryPassword?;
    @display {label: "Repository SNC Mode (jco.destination.repository.snc_mode)"}
    string repositorySncMode?;
    @display {label: "Repository Check Interval (jco.destination.repository.check_interval)"}
    string repositoryCheckInterval?;
    @display {label: "Trace (jco.client.trace)"}
    string trace?;
    @display {label: "GW Host (jco.client.gwhost)"}
    string gwHost?;
    @display {label: "GW Serv (jco.client.gwserv)"}
    string gwServ?;
    @display {label: "TP Host (jco.client.tphost)"}
    string tpHost?;
    @display {label: "TP Name (jco.client.tpname)"}
    string tpName?;
    @display {label: "WS Host (jco.client.wshost)"}
    string wsHost?;
    @display {label: "WS Port (jco.client.wsport)"}
    string wsPort?;
    @display {label: "Use TLS (jco.client.use_tls)"}
    string useTls?;
    @display {label: "TLS Trust All (jco.client.tls_trust_all)"}
    string tlsTrustAll?;
    @display {label: "TLS P12 File (jco.client.tls_p12_file)"}
    string tlsP12File?;
    @display {label: "TLS P12 Password (jco.client.tls_p12_passwd)"}
    string tlsP12Password?;
    @display {label: "TLS Client Certificate Logon (jco.client.tls_client_certificate_logon)"}
    string tlsClientCertificateLogon?;
    @display {label: "Proxy Host (jco.client.proxy_host)"}
    string proxyHost?;
    @display {label: "Proxy Port (jco.client.proxy_port)"}
    string proxyPort?;
    @display {label: "Proxy User (jco.client.proxy_user)"}
    string proxyUser?;
    @display {label: "Proxy Password (jco.client.proxy_passwd)"}
    string proxyPassword?;
    @display {label: "WS Ping Check Interval (jco.destination.ws_ping_check_interval)"}
    string wsPingCheckInterval?;
    @display {label: "WS Ping Period (jco.destination.ws_ping_period)"}
    string wsPingPeriod?;
    @display {label: "WS Pong Timeout (jco.destination.ws_pong_timeout)"}
    string wsPongTimeout?;
    @display {label: "Type (jco.client.type)"}
    string jco_type?;
    @display {label: "Use SAP GUI (jco.client.use_sapgui)"}
    string useSapGui?;
    @display {label: "Deny Initial Password (jco.client.deny_initial_password)"}
    string denyInitialPassword?;
    @display {label: "Repository Roundtrip Optimization (jco.destination.repository_roundtrip_optimization)"}
    string repositoryRoundtripOptimization?;
};

public enum IDocType {
    DEFAULT = "0",
    VERSION_2 = "2",
    VERSION_3 = "3",
    VERSION_3_IN_QUEUE = "Q",
    VERSION_3_IN_QUEUE_VIA_QRFC = "I"
};

public type FieldType string|int|float|decimal|time:Date|time:TimeOfDay|byte[]|record {|FieldType...;|}|record {|FieldType...;|}[];

public type Error distinct error;

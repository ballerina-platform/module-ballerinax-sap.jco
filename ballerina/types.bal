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

# Connection parameters for an SAP RFC destination (`jco.client.*` properties).
#
# + ashost - SAP application server host name (jco.client.ashost).
# + sysnr - SAP system number (jco.client.sysnr).
# + jcoClient - SAP client number (jco.client.client).
# + user - SAP logon user name (jco.client.user).
# + passwd - SAP logon password (jco.client.passwd).
# + lang - SAP logon language (jco.client.lang).
# + group - SAP logon group for load balancing (jco.client.group).
public type DestinationConfig record {|
    string ashost;
    string sysnr;
    string jcoClient;
    string user;
    string passwd;
    string lang = "EN";
    string group = "PUBLIC";
|};

# A flat map of raw JCo property key-value pairs used to pass properties not covered by
# `DestinationConfig` or `ServerConfig` (server keys start with `"jco.server."`, destination keys with `"jco.client."`).
public type AdvancedConfig map<string>;

# Connection parameters for a JCo IDoc server (`jco.server.*` properties).
#
# + gwhost - SAP gateway host the server registers with (jco.server.gwhost).
# + gwserv - SAP gateway service name or port (jco.server.gwserv).
# + progid - Program ID registered in the SAP system via SM59 (jco.server.progid).
# + connectionCount - Maximum number of concurrent RFC connections (jco.server.connection_count).
# + repositoryDestination - RFC destination used to look up IDoc metadata; defaults to the server name (jco.server.repository_destination).
public type ServerConfig record {|
    string gwhost;
    string gwserv;
    string progid;
    int connectionCount = 2;
    string repositoryDestination?;
|};

# IDoc version passed to JCoIDoc.send to select the tRFC/qRFC protocol variant.
public enum IDocType {
    # Default IDoc version.
    DEFAULT = "0",
    # IDoc version 2.
    VERSION_2 = "2",
    # IDoc version 3, sent via tRFC.
    VERSION_3 = "3",
    # IDoc version 3, placed in an outbound qRFC queue.
    VERSION_3_IN_QUEUE = "Q",
    # IDoc version 3, placed in an inbound qRFC queue.
    VERSION_3_IN_QUEUE_VIA_QRFC = "I"
};

# Any value that can appear as an RFC import/export parameter or a field inside a JCo structure or table.
public type FieldType string|int|float|decimal|time:Date|time:TimeOfDay|byte[]|record {|FieldType?...;|}|record {|FieldType?...;|}[];

# Detail record carried by JCo-origin errors, providing the JCo error group and SAP exception key.
public type JCoErrorDetail record {|
    # JCo error group constant (e.g. `JCoException.JCO_ERROR_COMMUNICATION = 101`).
    int errorGroup;
    # SAP exception key identifying the specific error (e.g. `"TABLE_NOT_AVAILABLE"`).
    string key?;
|};

# Extended detail record for ABAP application exceptions, adding the ABAP message fields.
public type AbapApplicationErrorDetail record {|
    *JCoErrorDetail;
    # ABAP message class (two-character identifier).
    string abapMsgClass?;
    # ABAP message type: E (error), W (warning), I (info), S (success), A (abend).
    string abapMsgType?;
    # Three-digit ABAP message number.
    string abapMsgNumber?;
    # First ABAP message variable (&1 placeholder).
    string abapMsgV1?;
    # Second ABAP message variable (&2 placeholder).
    string abapMsgV2?;
    # Third ABAP message variable (&3 placeholder).
    string abapMsgV3?;
    # Fourth ABAP message variable (&4 placeholder).
    string abapMsgV4?;
|};

# Raised when the JCo client cannot reach the SAP gateway (network / communication failure).
public type ConnectionError distinct error<JCoErrorDetail>;

# Raised when the SAP system rejects the supplied logon credentials.
public type LogonError distinct error<JCoErrorDetail>;

# Raised when JCo cannot allocate a required resource (e.g. connection pool exhausted).
public type ResourceError distinct error<JCoErrorDetail>;

# Raised when the SAP system reports an internal system failure.
public type SystemError distinct error<JCoErrorDetail>;

# Raised when an ABAP function module throws a class-based or classic exception.
public type AbapApplicationError distinct error<AbapApplicationErrorDetail>;

# Raised for all other JCo-level errors not covered by a more specific type.
public type JCoError distinct error<JCoErrorDetail>;

# Raised for IDoc-specific failures (XML parsing, send, or server-side processing).
public type IDocError distinct error;

# Raised when an RFC import or export parameter cannot be converted to or from a Ballerina type.
public type ParameterError distinct error;

# Raised during client or listener initialization and lifecycle management.
public type ConfigurationError distinct error;

# Raised when an unexpected error occurs during RFC execution or other runtime operations.
public type ExecutionError distinct error;

# Union of all errors that can be returned by the SAP JCo connector.
public type Error ConnectionError|LogonError|ResourceError|SystemError|AbapApplicationError
    |JCoError|IDocError|ParameterError|ConfigurationError|ExecutionError;

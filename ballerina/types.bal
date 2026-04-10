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

# Connection parameters for an SAP RFC destination.
public type DestinationConfig record {|
    # SAP application server host name
    string ashost;
    # SAP system number
    string sysnr;
    # SAP client number
    string jcoClient;
    # SAP logon user name
    string user;
    # SAP logon password
    string passwd;
    # SAP logon language
    string lang = "EN";
    # SAP logon group for load balancing
    string group = "PUBLIC";
|};

# Advanced configuration using raw JCo property key-value pairs, for settings not covered by DestinationConfig or ServerConfig.
public type AdvancedConfig map<string>;

# Connection parameters for a JCo IDoc server.
public type ServerConfig record {|
    # SAP gateway host to register the server with
    string gwhost;
    # SAP gateway service name or port
    string gwserv;
    # Program ID registered in the SAP system via transaction SM59
    string progid;
    # Maximum number of concurrent RFC connections
    int connectionCount = 2;
    # RFC destination used to look up IDoc and RFC metadata; must match the destinationId of an already-initialised Client
    string repositoryDestination;
|};

# IDoc protocol version used when sending IDocs to the SAP system.
public enum IDocType {
    # Default IDoc version
    DEFAULT = "0",
    # IDoc version 2
    VERSION_2 = "2",
    # IDoc version 3, sent via tRFC
    VERSION_3 = "3",
    # IDoc version 3, placed in an outbound qRFC queue
    VERSION_3_IN_QUEUE = "Q",
    # IDoc version 3, placed in an inbound qRFC queue
    VERSION_3_IN_QUEUE_VIA_QRFC = "I"
};

# Any value that can appear as an RFC import/export parameter or a field inside a JCo structure or table.
public type FieldType string|int|float|decimal|time:Date|time:TimeOfDay|byte[]|record {|FieldType?...;|}|record {|FieldType?...;|}[];

# Represents a single RFC parameter set — scalar values, structures, or table row data.
public type RfcRecord record {|
    FieldType?...;
|};

# Groups all input parameters for an RFC call by category.
public type RfcParameters record {|
    # Scalar values and structures sent to SAP as import parameters
    RfcRecord importParameters?;
    # Named tables sent to SAP as table parameters, where each entry maps a table parameter name to its rows
    map<RfcRecord[]> tableParameters?;
|};

# Error detail for JCo-origin errors, providing the JCo error group and SAP exception key.
public type JCoErrorDetail record {|
    # JCo error group identifier
    int errorGroup;
    # SAP exception key identifying the specific error (e.g., TABLE_NOT_AVAILABLE)
    string key?;
|};

# Error detail for ABAP application exceptions, extending JCoErrorDetail with ABAP message fields.
public type AbapApplicationErrorDetail record {|
    *JCoErrorDetail;
    # ABAP message class (two-character identifier)
    string abapMsgClass?;
    # ABAP message type: E (error), W (warning), I (info), S (success), A (abend)
    string abapMsgType?;
    # Three-digit ABAP message number
    string abapMsgNumber?;
    # First ABAP message variable (&1 placeholder)
    string abapMsgV1?;
    # Second ABAP message variable (&2 placeholder)
    string abapMsgV2?;
    # Third ABAP message variable (&3 placeholder)
    string abapMsgV3?;
    # Fourth ABAP message variable (&4 placeholder)
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

# Raised when an RFC import or export parameter cannot be converted to or from the expected type.
public type ParameterError distinct error;

# Raised during client or listener initialization and lifecycle management.
public type ConfigurationError distinct error;

# Raised when an unexpected error occurs during RFC execution or other runtime operations.
public type ExecutionError distinct error;

# Represents all errors that can be returned by the SAP JCo connector.
public type Error ConnectionError|LogonError|ResourceError|SystemError|AbapApplicationError
    |JCoError|IDocError|ParameterError|ConfigurationError|ExecutionError;

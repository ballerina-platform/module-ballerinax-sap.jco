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
# + ashost - The SAP host name (jco.client.ashost).
# + sysnr - The SAP system number (jco.client.sysnr).
# + jcoClient - The SAP client (jco.client.client).
# + user - The SAP user name (jco.client.user).
# + passwd - The SAP password (jco.client.passwd).
# + lang - The SAP language (jco.client.lang).
# + group - The SAP group (jco.client.group).
public type DestinationConfig record {|
    @display {label: "Host Name (jco.client.ashost)"}
    string ashost;
    @display {label: "System Number (jco.client.sysnr)"}
    string sysnr;
    @display {label: "Client (jco.client.client)"}
    string jcoClient;
    @display {label: "User Name (jco.client.user)"}
    string user;
    @display {label: "Password (jco.client.passwd)"}
    string passwd;
    @display {label: "Language (jco.client.lang)"}
    string lang = "EN";
    @display {label: "Group (jco.client.group)"}
    string group = "PUBLIC";
|};

# Holds the any custom configurations needed to create a SAP connection.
public type AdvancedConfig map<string>;

# Holds the configuration details needed to create an iDoc connection.
# 
# + gwhost - The gateway host (jco.server.gwhost).
# + gwserv - The gateway service (jco.server.gwserv).
# + progid - The program ID (jco.server.progid).
public type ServerConfig record {|
    @display {label: "Gateway Host (jco.server.gwhost)"}
    string gwhost;
    @display {label: "Gateway Service (jco.server.gwserv)"}
    string gwserv;
    @display {label: "Program ID (jco.server.progid)"}
    string progid;
|};

public enum IDocType {
    DEFAULT = "0",
    VERSION_2 = "2",
    VERSION_3 = "3",
    VERSION_3_IN_QUEUE = "Q",
    VERSION_3_IN_QUEUE_VIA_QRFC = "I"
};

public type FieldType string|int|float|decimal|time:Date|time:TimeOfDay|byte[]|record {|FieldType?...;|}|record {|FieldType?...;|}[];

public type Error distinct error;

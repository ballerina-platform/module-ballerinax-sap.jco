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

import ballerina/jballerina.java as java;
import ballerina/uuid;

# SAP JCo client for calling RFC-enabled function modules and sending IDocs to an SAP system.
public isolated client class Client {

    # Registers a JCo RFC destination and verifies connectivity with a ping.
    #
    # + configurations - RFC destination connection parameters (`DestinationConfig` or `AdvancedConfig`).
    # + destinationId - Unique name for this RFC destination; provide an explicit name when a `Listener` references it as `repositoryDestination`.
    # + return - An error if the connection cannot be established.
    public isolated function init(DestinationConfig|AdvancedConfig configurations,
                                  string destinationId = uuid:createType4AsString()) returns Error? {
        check initializeClient(self, configurations, destinationId);
    }

    # Calls an RFC-enabled function module on the SAP system and returns the export parameters.
    #
    # + functionName - Name of the RFC function module to call (e.g. `"STFC_CONNECTION"`).
    # + importParams - Import parameter values keyed by parameter name.
    # + exportParams - Expected type of the RFC export parameters (`xml`, `json`, or a record type).
    # + return - The export parameters converted to the `exportParams` type, or an error on failure.
    isolated remote function execute(string functionName, record {|FieldType?...;|} importParams, typedesc<record {|FieldType?...;|}|xml|json?> exportParams = <>) returns exportParams|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Sends an IDoc to the SAP system over tRFC, including TID creation and confirmation.
    #
    # + iDoc - IDoc payload as XML.
    # + iDocType - IDoc version/protocol variant.
    # + return - An error if the IDoc cannot be delivered or the TID cannot be confirmed.
    isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

}

isolated function initializeClient(Client jcoClient, DestinationConfig|AdvancedConfig configurations, string destinationId) returns Error? = @java:Method {
    'class: "io.ballerina.lib.sap.Client"
} external;

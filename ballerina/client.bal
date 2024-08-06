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

# A Ballerina client for SAP BAPI/RFC.
@display {label: "RFC Client", iconPath: "icon.png"}
public isolated client class Client {

    # Initializes the connector.
    #
    # + configurations - The configurations required to initialize the BAPI client.
    # + return - An error if the initialization fails.
    public isolated function init(DestinationConfig configurations) returns Error? {
        check initializeClient(self, configurations, uuid:createType4AsString());
    }

    # Executes the RFC function.
    #
    # + functionName - The name of the function to be executed.
    # + importParams - The input parameters for the function.
    # + exportParams - The output parameters for the function.
    # + return - An error if the execution fails.
    isolated remote function execute(string functionName, record {|FieldType...;|} importParams, typedesc<record {|FieldType...;|}|xml|json?> exportParams = <>) returns exportParams|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Send the iDoc.
    #
    # + iDoc - The XML string of the iDoc.
    # + iDocType - The type of the iDoc.
    # + return - An error if the execution fails.
    isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

}

isolated function initializeClient(Client jcoClient, DestinationConfig configurations, string destinationId) returns Error? = @java:Method {
    'class: "io.ballerina.lib.sap.Client"
} external;

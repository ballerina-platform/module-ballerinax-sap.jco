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
    # + config - Connection configuration for the RFC destination
    # + destinationId - Unique name for this RFC destination. Provide an explicit name when a listener references it as the repository destination.
    # + return - An error if the connection cannot be established
    public isolated function init(DestinationConfig|AdvancedConfig config,
                                  string destinationId = uuid:createType4AsString()) returns Error? {
        check initializeClient(self, config, destinationId);
    }

    # Calls an RFC-enabled function module on the SAP system and returns the response.
    #
    # + functionName - Name of the RFC function module to call (e.g., STFC_CONNECTION).
    # + parameters   - Input parameters organized by category. Import parameters are scalar or structure values. Table parameters are named tables containing rows of data. Defaults to an empty parameter set for parameter-free RFCs.
    # + returnType   - Expected response type. The response is populated from both the SAP export parameter list and the table parameter list.
    # + return - The RFC response, or an error on failure
    isolated remote function execute(string functionName, RfcParameters parameters = {},
            typedesc<RfcRecord|xml|json> returnType = <>) returns returnType|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Sends an IDoc to the SAP system over tRFC, including TID creation and confirmation.
    #
    # + iDoc - IDoc payload as XML
    # + iDocType - IDoc protocol version
    # + return - An error if the IDoc cannot be delivered or the TID cannot be confirmed
    isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Releases the JCo destination registered for this client. Call this when the client is no
    # longer needed to free the destination ID for reuse. Calling this more than once is safe.
    #
    # + return - An error if the JCo destination could not be fully released; the client is marked closed regardless
    public isolated function close() returns Error? = @java:Method {
        name: "closeClient",
        'class: "io.ballerina.lib.sap.Client"
    } external;

}

isolated function initializeClient(Client jcoClient, DestinationConfig|AdvancedConfig config, string destinationId) returns Error? = @java:Method {
    'class: "io.ballerina.lib.sap.Client"
} external;

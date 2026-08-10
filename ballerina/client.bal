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
    public isolated function init(DestinationConfig configurations, boolean setImportParamNull = false) returns Error? {
        check initializeClient(self, configurations, uuid:createType4AsString(), setImportParamNull);
    }

    # Executes the RFC function.
    #
    # + functionName - The name of the function to be executed.
    # + importParams - The input parameters for the function.
    # + exportParams - The output parameters for the function.
    # + return - An error if the execution fails.
    isolated remote function execute(string functionName, record {|FieldType?...;|} importParams, typedesc<record {|string|int|float|decimal...;|}?|xml|json> exportParams = <>) returns exportParams|Error = @java:Method {
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

    # Creates a new transaction ID (TID) from the SAP backend for use with tRFC/qRFC calls.
    # Holding on to the TID before sending allows retrying a failed send with the same TID,
    # which guarantees exactly-once execution.
    #
    # + return - The TID, or an error if the creation fails.
    isolated remote function createTid() returns string|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Confirms a transaction ID (TID) so the SAP backend can clean up its bookkeeping for it.
    # Use this together with `autoConfirm = false` on `sendTRfc`/`sendQRfc`: retry the send with
    # the same TID until it succeeds, then confirm. A confirmed TID must not be reused - the
    # backend forgets it, so a resend under it would execute again.
    #
    # + tid - The TID to confirm.
    # + return - An error if the confirmation fails.
    isolated remote function confirmTid(string tid) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Executes the RFC function as a transactional RFC (tRFC) with exactly-once semantics.
    # The call is asynchronous on the SAP side, so no result is returned. If the send fails,
    # the returned `TransactionError` carries the TID in its detail; retrying with that TID
    # keeps exactly-once semantics (use `autoConfirm = false` for such retry flows and call
    # `confirmTid` after the send finally succeeds).
    #
    # + functionName - The name of the RFC-enabled function module to execute.
    # + importParams - The import, changing, and table parameters for the function.
    # + tid - The transaction ID to use. If not given, a new TID is created automatically.
    # + autoConfirm - If true, the TID is confirmed automatically after a successful send
    #                 (best-effort: a failed confirmation is logged but not surfaced, since
    #                 the call was already delivered). Set to false to confirm manually via
    #                 `confirmTid`.
    # + return - The TID under which the call was sent, or an error if the send fails.
    isolated remote function sendTRfc(string functionName, ParamStructure importParams = {},
            string? tid = (), boolean autoConfirm = true) returns string|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Executes the RFC function as a queued RFC (qRFC) with exactly-once-in-order semantics.
    # Calls sent to the same inbound queue are processed serially in the order they were sent.
    # If the send fails, the returned `TransactionError` carries the TID in its detail;
    # retrying with that TID keeps exactly-once semantics (use `autoConfirm = false` for such
    # retry flows and call `confirmTid` after the send finally succeeds).
    #
    # + functionName - The name of the RFC-enabled function module to execute.
    # + queueName - The name of the SAP inbound queue to place the call on.
    # + importParams - The import, changing, and table parameters for the function.
    # + tid - The transaction ID to use. If not given, a new TID is created automatically.
    # + autoConfirm - If true, the TID is confirmed automatically after a successful send
    #                 (best-effort: a failed confirmation is logged but not surfaced, since
    #                 the call was already delivered). Set to false to confirm manually via
    #                 `confirmTid`.
    # + return - The TID under which the call was sent, or an error if the send fails.
    isolated remote function sendQRfc(string functionName, string queueName,
            ParamStructure importParams = {}, string? tid = (), boolean autoConfirm = true)
            returns string|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Sends one or more function calls to SAP as a single bgRFC unit (one logical unit of work).
    # If `queueNames` is set in the unit configuration, the unit is sent as type Q (bg-qRFC,
    # exactly-once-in-order); otherwise as type T (exactly-once).
    #
    # + functionCalls - The function invocations that make up the unit. All of them are
    #                   executed in one logical unit of work on the SAP backend.
    # + unitConfig - The configurations for the unit.
    # + return - The ID and type of the committed unit, or an error if the commit fails.
    isolated remote function sendBgRfcUnit(FunctionCall[] functionCalls, BgRfcUnitConfig unitConfig = {})
            returns BgRfcUnitInfo|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Retrieves the processing state of a bgRFC unit from the SAP backend. `COMMITTED` means
    # the backend finished processing the unit and it can be confirmed; `CONFIRMED` means it
    # was already confirmed via `confirmBgRfcUnit`.
    #
    # + unit - The unit identification returned by `sendBgRfcUnit`.
    # + return - The state of the unit, or an error if the lookup fails.
    isolated remote function getBgRfcUnitState(BgRfcUnitInfo unit)
            returns BgRfcUnitState|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Confirms a bgRFC unit so the SAP backend can delete its status information for the unit.
    # Confirm once `getBgRfcUnitState` reports `COMMITTED` (processing finished); after the
    # confirmation the state becomes `CONFIRMED`. A confirmed unit ID must not be reused.
    #
    # + unit - The unit identification returned by `sendBgRfcUnit`.
    # + return - An error if the confirmation fails.
    isolated remote function confirmBgRfcUnit(BgRfcUnitInfo unit)
            returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

}

isolated function initializeClient(Client jcoClient, DestinationConfig configurations, string destinationId, boolean setImportParamNull) returns Error? = @java:Method {
    'class: "io.ballerina.lib.sap.Client"
} external;

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
        AdvancedConfig clientConfig;
        if config is DestinationConfig {
            clientConfig = {
                "jco.client.ashost": config.ashost,
                "jco.client.sysnr": config.sysnr,
                "jco.client.client": config.jcoClient,
                "jco.client.user": config.user,
                "jco.client.passwd": config.passwd,
                "jco.client.group": config.group,
                "jco.client.lang": config.lang
            };
        } else {
            clientConfig = config;
        }
        check initializeClient(self, clientConfig, destinationId);
    }

    # Calls an RFC-enabled function module on the SAP system and returns the response.
    #
    # + functionName - Name of the RFC function module to call (for example, STFC_CONNECTION)
    # + parameters - Input parameters organised by category. Import parameters carry scalar
    #                or structure values. Table parameters carry named tables of row data.
    #                Defaults to an empty parameter set for parameter-free RFCs.
    # + returnType - Expected response shape. The response is populated from both the SAP
    #                export parameter list and the table parameter list.
    # + return - The RFC response, or an error on failure
    isolated remote function execute(string functionName, RfcParameters parameters = {},
            typedesc<RfcRecord|xml> returnType = <>) returns returnType|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Sends an IDoc to the SAP system over tRFC or qRFC, including TID creation and confirmation.
    #
    # + iDoc - IDoc payload in XML format
    # + iDocType - IDoc protocol version. Use VERSION_3_IN_QUEUE or VERSION_3_IN_QUEUE_VIA_QRFC for qRFC delivery.
    # + tid - Optional Transaction ID (TID). If not provided, a new TID is created via the JCo destination.
    #         Supply your own TID for end-to-end idempotency when the application persists outbound intent.
    # + queueName - Required for qRFC versions (VERSION_3_IN_QUEUE or VERSION_3_IN_QUEUE_VIA_QRFC). Ignored with a warning for tRFC versions.
    # + return - An error if the IDoc cannot be delivered or the TID cannot be confirmed
    isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT, string? tid = (),
                                      string? queueName = ()) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Client"
    } external;

    # Creates a transaction ID (TID) on the SAP system for use with `sendTRfc` or `sendQRfc`.
    # Obtain the TID before the first send so that every retry can reuse it.
    #
    # + return - The TID, or an error if it cannot be created
    isolated remote function createTid() returns string|Error = @java:Method {
        'class: "io.ballerina.lib.sap.Transactions"
    } external;

    # Confirms a transaction ID so that the SAP system can discard its record of it. Confirm only
    # after the call has been delivered. A confirmed TID must not be reused, because the system
    # forgets it and a resend under it would execute the call again.
    #
    # + tid - The TID to confirm. Must be exactly 24 characters long.
    # + return - An error if the confirmation fails
    isolated remote function confirmTid(string tid) returns Error? {
        check validateTid(tid);
        return confirmTidExternal(self, tid);
    }

    # Returns the transaction ID to send under: the caller's, once validated, or a new one
    # obtained from the SAP system when none was supplied.
    #
    # + tid - The caller-supplied transaction ID, or `()` to obtain one from SAP
    # + return - The transaction ID to use, or an error if it is invalid or cannot be obtained
    isolated function resolveTid(string? tid) returns string|Error {
        if tid is string {
            check validateTid(tid);
            return tid;
        }
        return self->createTid();
    }

    # Calls an RFC-enabled function module as a transactional RFC (tRFC), which the SAP system
    # executes exactly once. The call is asynchronous, so export and table values the function
    # module produces are discarded and only the TID is returned. Use `execute` when the result
    # is needed.
    #
    # + functionName - Name of the RFC function module to call
    # + parameters - Input parameters organised by category
    # + tid - The transaction ID to use. If not provided, one is created automatically. A
    #         supplied TID must be exactly 24 characters long; the content is unrestricted, so
    #         it may be derived from an application idempotency key.
    # + autoConfirm - Whether to confirm the TID after a successful send. Set this to false to
    #                 retry a failed send under the same TID, then call `confirmTid` once the
    #                 send finally succeeds.
    # + return - The TID the call was sent under, or an error if the send fails
    isolated remote function sendTRfc(string functionName, RfcParameters parameters = {},
            string? tid = (), boolean autoConfirm = true) returns string|Error {
        string transactionId = check self.resolveTid(tid);
        check sendTRfcExternal(self, functionName, parameters, transactionId, autoConfirm);
        return transactionId;
    }

    # Calls an RFC-enabled function module as a queued RFC (qRFC). Calls placed on the same
    # inbound queue are executed exactly once and in the order they were sent.
    #
    # + functionName - Name of the RFC function module to call
    # + queueName - The SAP inbound queue that serialises the calls
    # + parameters - Input parameters organised by category
    # + tid - The transaction ID to use. If not provided, one is created automatically. A
    #         supplied TID must be exactly 24 characters long.
    # + autoConfirm - Whether to confirm the TID after a successful send
    # + return - The TID the call was sent under, or an error if the send fails
    isolated remote function sendQRfc(string functionName, string queueName,
            RfcParameters parameters = {}, string? tid = (), boolean autoConfirm = true)
            returns string|Error {
        if queueName.length() == 0 {
            return error ParameterError("Queue name is empty.");
        }
        string transactionId = check self.resolveTid(tid);
        check sendQRfcExternal(self, functionName, queueName, parameters, transactionId, autoConfirm);
        return transactionId;
    }

    # Commits one or more function calls to the SAP system as a single bgRFC unit of work. The
    # calls are applied together or not at all. Supplying `queueNames` makes the unit type Q,
    # which is executed in order within those queues; otherwise it is type T.
    #
    # + functionCalls - The calls that make up the unit
    # + unitConfig - Configuration for the unit
    # + return - The ID and type of the committed unit, or an error if the commit fails
    isolated remote function sendBgRfcUnit(RemoteFunctionCall[] functionCalls,
            BgRfcUnitConfig unitConfig = {}) returns BgRfcUnitInfo|Error {
        if functionCalls.length() == 0 {
            return error ParameterError("A bgRFC unit must contain at least one function call.");
        }
        string? unitId = unitConfig?.unitId;
        if unitId is string {
            check validateUnitId(unitId);
        }
        return sendBgRfcUnitExternal(self, functionCalls, unitConfig);
    }

    # Reads the processing state of a bgRFC unit. `COMMITTED` means processing finished and the
    # unit is ready to be confirmed. `CONFIRMED` occurs only after `confirmBgRfcUnit`, so waiting
    # for it before confirming would never return.
    #
    # + unit - The unit returned by `sendBgRfcUnit`
    # + return - The state of the unit, or an error if the state cannot be read
    isolated remote function getBgRfcUnitState(BgRfcUnitInfo unit)
            returns BgRfcUnitState|Error {
        check validateUnitId(unit.unitId);
        return getBgRfcUnitStateExternal(self, unit);
    }

    # Confirms a bgRFC unit so that the SAP system can delete its status record. Confirm once
    # `getBgRfcUnitState` reports `COMMITTED`. A confirmed unit ID must not be reused.
    #
    # + unit - The unit returned by `sendBgRfcUnit`
    # + return - An error if the confirmation fails
    isolated remote function confirmBgRfcUnit(BgRfcUnitInfo unit) returns Error? {
        check validateUnitId(unit.unitId);
        return confirmBgRfcUnitExternal(self, unit);
    }

    # Releases the JCo destination registered for this client. Call this when the client is
    # no longer needed to free the destination ID for reuse. Calling this more than once is
    # safe.
    #
    # + return - An error if the JCo destination could not be fully released; the client is
    #            marked closed regardless
    public isolated function close() returns Error? = @java:Method {
        name: "closeClient",
        'class: "io.ballerina.lib.sap.Client"
    } external;

}

// A TID is split positionally by JCo into four fixed-width character fields, so a shorter
// value fails inside JCo and a longer one is silently truncated, which would make SAP record a
// different identifier than the caller believes it used. The content is deliberately not
// restricted: SAP stores the TID components as CHAR, so an application may derive a TID from
// its own idempotency key.
isolated function validateTid(string tid) returns Error? {
    if tid.length() != TID_LENGTH {
        return error ParameterError(string `Invalid transaction ID '${tid}'. A TID must be exactly `
                + string `${TID_LENGTH} characters long.`);
    }
}

// Unit IDs, unlike TIDs, are 16 bytes in hexadecimal. JCo documents that format but
// JCo.createFunctionUnit does not enforce it, so a malformed ID would otherwise surface only
// later when the unit is queried.
isolated function validateUnitId(string unitId) returns Error? {
    if !UNIT_ID_PATTERN.isFullMatch(unitId) {
        return error ParameterError(string `Invalid bgRFC unit ID '${unitId}'. A unit ID must be a `
                + string `${UNIT_ID_LENGTH}-character hexadecimal string.`);
    }
}

isolated function sendTRfcExternal(Client jcoClient, string functionName, RfcParameters parameters,
        string tid, boolean autoConfirm) returns Error? = @java:Method {
    name: "sendTRfc",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function sendQRfcExternal(Client jcoClient, string functionName, string queueName,
        RfcParameters parameters, string tid, boolean autoConfirm) returns Error? = @java:Method {
    name: "sendQRfc",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function confirmTidExternal(Client jcoClient, string tid) returns Error? = @java:Method {
    name: "confirmTid",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function sendBgRfcUnitExternal(Client jcoClient, RemoteFunctionCall[] functionCalls,
        BgRfcUnitConfig unitConfig) returns BgRfcUnitInfo|Error = @java:Method {
    name: "sendBgRfcUnit",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function getBgRfcUnitStateExternal(Client jcoClient, BgRfcUnitInfo unit)
        returns BgRfcUnitState|Error = @java:Method {
    name: "getBgRfcUnitState",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function confirmBgRfcUnitExternal(Client jcoClient, BgRfcUnitInfo unit)
        returns Error? = @java:Method {
    name: "confirmBgRfcUnit",
    'class: "io.ballerina.lib.sap.Transactions"
} external;

isolated function initializeClient(Client jcoClient, AdvancedConfig config, string destinationId) returns Error? = @java:Method {
    'class: "io.ballerina.lib.sap.Client"
} external;

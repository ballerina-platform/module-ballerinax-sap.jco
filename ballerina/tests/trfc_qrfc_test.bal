// Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org).
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

import ballerina/test;

// Tests for the transactional and queued RFC operations: sendTRfc, sendQRfc, and the
// transaction ID lifecycle (createTid, confirmTid).
//
// All tests use standard SAP basis RFCs available on every ECC/S4 system:
//   STFC_WRITE_TO_TCPIC - writes rows to table TCPIC; has no export parameters, so it is
//                         suitable for asynchronous delivery where results are discarded
//
// All tests are disabled by default. Set the required environment variables
// (see config.bal) to enable them.

const string TRFC_FUNCTION = "STFC_WRITE_TO_TCPIC";

// A queue name that satisfies SAP's naming rules (uppercase letters, digits, underscores).
const string TEST_QUEUE = "BAL_JCO_TEST_QUEUE";

isolated function tcpicRow(string tag) returns RfcParameters {
    return {tableParameters: {"TCPICDAT": [{"LINE": "ballerina-test " + tag}]}};
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testCreateTidReturnsUsableTid() returns error? {
    Client sapClient = check new (destinationConfig);
    string tid = check sapClient->createTid();
    test:assertEquals(tid.length(), 24, "A TID created by SAP should be 24 characters long");
    check sapClient->confirmTid(tid);
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcWithGeneratedTid() returns error? {
    Client sapClient = check new (destinationConfig);
    string tid = check sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("trfc-auto"));
    test:assertEquals(tid.length(), 24, "sendTRfc should return the 24-character TID it used");
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcReusesSuppliedTid() returns error? {
    // Sending twice under one unconfirmed TID is the exactly-once contract: SAP recognises the
    // second call as a duplicate and does not execute it again. Both sends must report the
    // supplied TID so a caller can retry under it.
    Client sapClient = check new (destinationConfig);
    string tid = check sapClient->createTid();
    string first = check sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("trfc-retry"), tid,
            autoConfirm = false);
    string second = check sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("trfc-retry"), tid,
            autoConfirm = false);
    test:assertEquals(first, tid, "The first send should use the supplied TID");
    test:assertEquals(second, tid, "The retry should reuse the same TID");
    check sapClient->confirmTid(tid);
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcAcceptsNonHexadecimalTid() returns error? {
    // SAP stores the TID components as CHAR, so only the length is constrained. An application
    // may derive a TID from its own idempotency key.
    Client sapClient = check new (destinationConfig);
    string tid = "IDEMPOTENCYKEY0000000001";
    string used = check sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("trfc-bizkey"), tid,
            autoConfirm = false);
    test:assertEquals(used, tid, "A 24-character non-hexadecimal TID should be accepted");
    check sapClient->confirmTid(tid);
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcRejectsWrongLengthTid() returns error? {
    Client sapClient = check new (destinationConfig);
    foreach string badTid in ["TOOSHORT", "THISTIDISFARTOOLONGTOBEVALID"] {
        string|Error result = sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("trfc-bad"), badTid);
        test:assertTrue(result is Error, "A TID of the wrong length should be rejected");
        if result is Error {
            test:assertTrue(result.message().includes("24 characters"),
                    "The error should state the required TID length: " + result.message());
        }
    }
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcUnknownFunctionModule() returns error? {
    Client sapClient = check new (destinationConfig);
    string|Error result = sapClient->sendTRfc("BAL_NO_SUCH_FUNCTION_MODULE");
    test:assertTrue(result is Error, "An unknown function module should be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("not found"), result.message());
    }
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcUndefinedParameter() returns error? {
    Client sapClient = check new (destinationConfig);
    string|Error result = sapClient->sendTRfc(TRFC_FUNCTION,
            {importParameters: {"BAL_NO_SUCH_PARAM": "x"}});
    test:assertTrue(result is Error, "A parameter the function module does not define should fail");
}

@test:Config {
    enable: testsEnabled,
    groups: ["qrfc"]
}
function testSendQRfcQueuesCall() returns error? {
    Client sapClient = check new (destinationConfig);
    string tid = check sapClient->sendQRfc(TRFC_FUNCTION, TEST_QUEUE, tcpicRow("qrfc"));
    test:assertEquals(tid.length(), 24, "sendQRfc should return the TID it used");
}

@test:Config {
    enable: testsEnabled,
    groups: ["qrfc"]
}
function testSendQRfcEachCallGetsOwnTid() returns error? {
    // qRFC preserves the order in which calls reach the queue. These sends are sequential, so
    // the order is defined; each call must still be tracked by its own TID.
    Client sapClient = check new (destinationConfig);
    string first = check sapClient->sendQRfc(TRFC_FUNCTION, TEST_QUEUE, tcpicRow("qrfc-1"));
    string second = check sapClient->sendQRfc(TRFC_FUNCTION, TEST_QUEUE, tcpicRow("qrfc-2"));
    test:assertNotEquals(first, second, "Each queued call should have a distinct TID");
}

@test:Config {
    enable: testsEnabled,
    groups: ["qrfc"]
}
function testSendQRfcRejectsEmptyQueueName() returns error? {
    Client sapClient = check new (destinationConfig);
    string|Error result = sapClient->sendQRfc(TRFC_FUNCTION, "", tcpicRow("qrfc-noqueue"));
    test:assertTrue(result is Error, "An empty queue name should be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("Queue name"), result.message());
    }
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testTransactionalCallsShareOneClient() returns error? {
    // execute and the transactional operations must work through the same client instance.
    Client sapClient = check new (destinationConfig);
    RfcRecord echo = check sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "mixed"}});
    test:assertEquals(echo["ECHOTEXT"], "mixed", "execute should still work on this client");
    string tid = check sapClient->sendTRfc(TRFC_FUNCTION, tcpicRow("mixed"));
    test:assertEquals(tid.length(), 24, "sendTRfc should work on the same client as execute");
}

@test:Config {
    enable: testsEnabled,
    groups: ["trfc"]
}
function testSendTRfcConversionFailureReturnsError() returns error? {
    // Parameter binding raises ConversionException, a JCoRuntimeException subclass that is
    // unrelated to JCoException. Without an explicit guard it escapes as a raw Java error and
    // aborts the call instead of returning a Ballerina error. STFC_STRUCTURE's RFCHEX3 is a RAW
    // field, and binding a byte array to it inside a structure triggers that conversion failure.
    Client sapClient = check new (destinationConfig);
    string|Error result = sapClient->sendTRfc("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {"RFCHEX3": <byte[]>[1, 2, 3]}}
    });
    test:assertTrue(result is Error,
            "A value that cannot be converted to its ABAP field should return an error");
}

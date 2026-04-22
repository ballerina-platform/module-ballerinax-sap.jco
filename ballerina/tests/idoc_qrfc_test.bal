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

// Tests for qRFC and tRFC send paths in Client.sendIDoc().
//
// These tests reuse validTestIDoc from idoc_send_test.bal (same test module).
// All tests require a live SAP system and are disabled by default.
// Set the required environment variables (see config.bal) to enable them.

// A fixed 24-character hex TID in the format SAP expects for tRFC/qRFC bookkeeping.
final string validTestTid = "AABBCCDDEEFF001122334455";

@test:Config {
    groups: ["idoc-send", "qrfc"],
    enable: testsEnabled
}
function testSendIDocViaQrfcWithAutoTid() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3_IN_QUEUE, queueName = "TEST_QUEUE");
    if result is Error {
        test:assertFail("Failed to send IDoc via qRFC: " + result.message());
    }
    check jcoClient.close();
}

@test:Config {
    groups: ["idoc-send", "qrfc"],
    enable: testsEnabled
}
function testSendIDocViaQrfcInboundWithAutoTid() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3_IN_QUEUE_VIA_QRFC, queueName = "TEST_QUEUE_I");
    if result is Error {
        test:assertFail("Failed to send IDoc via VERSION_3_IN_QUEUE_VIA_QRFC: " + result.message());
    }
    check jcoClient.close();
}

@test:Config {
    groups: ["idoc-send", "qrfc"],
    enable: testsEnabled
}
function testSendIDocViaQrfcWithCustomTid() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3_IN_QUEUE_VIA_QRFC,
                                        tid = validTestTid, queueName = "TEST_QUEUE_I");
    if result is Error {
        test:assertFail("Failed to send IDoc via qRFC with custom TID: " + result.message());
    }
    check jcoClient.close();
}

@test:Config {
    groups: ["idoc-send", "trfc"],
    enable: testsEnabled
}
function testSendIDocViaTrfcWithCustomTid() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3, tid = validTestTid);
    if result is Error {
        test:assertFail("Failed to send IDoc via tRFC with custom TID: " + result.message());
    }
    check jcoClient.close();
}

// Verifies that VERSION_3_IN_QUEUE without a queueName returns a ParameterError.
@test:Config {
    groups: ["idoc-send", "qrfc"],
    enable: testsEnabled
}
function testSendIDocQrfcMissingQueueNameReturnsError() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3_IN_QUEUE);
    test:assertTrue(result is ParameterError,
            "Expected a ParameterError when queueName is omitted for VERSION_3_IN_QUEUE");
    if result is ParameterError {
        test:assertEquals(result.message(), "Queue name is required for qRFC IDoc version: Q");
    }
    check jcoClient.close();
}

// Verifies that passing queueName with a tRFC iDocType succeeds (queueName is warned and ignored).
@test:Config {
    groups: ["idoc-send", "trfc"],
    enable: testsEnabled
}
function testSendIDocTrfcWithQueueNameSucceeds() returns error? {
    Client jcoClient = check new (destinationConfig);
    Error? result = jcoClient->sendIDoc(validTestIDoc, VERSION_3, queueName = "IGNORED_QUEUE");
    if result is Error {
        test:assertFail("Expected send to succeed when queueName is used with a tRFC version: "
                        + result.message());
    }
    check jcoClient.close();
}

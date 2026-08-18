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

import ballerina/lang.runtime;
import ballerina/test;
import ballerina/time;

// Live tests run only when a reachable SAP system is configured via Config.toml.
configurable boolean enableLiveTests = false;
configurable string host = "";
configurable string systemNumber = "00";
configurable string jcoClient = "800";
configurable string user = "";
configurable string password = "";
configurable string queueName = "BALLERINA_TEST_QUEUE";

final string testMarker = string `BAL-${time:utcNow()[0]}`;

Client? sharedClient = ();

// One client is shared across tests; testMultipleClients proves that additional
// clients can coexist (single shared JCo DestinationDataProvider).
function getTestClient() returns Client|error {
    Client? existing = sharedClient;
    if existing is Client {
        return existing;
    }
    Client created = check new ({host, systemNumber, jcoClient, user, password});
    sharedClient = created;
    return created;
}

isolated function tcpicRows(string suffix) returns ParamStructure[] {
    return [{"LINE": string `${testMarker}-${suffix}`}];
}

// --- Always-on tests (no SAP system required): guard the Java/Ballerina string contract ---

@test:Config {}
function testBgRfcTypeAndStateContract() {
    test:assertEquals(<string>BGRFC_TYPE_T, "T");
    test:assertEquals(<string>BGRFC_TYPE_Q, "Q");
    test:assertEquals(<string>NOT_FOUND, "NOT_FOUND");
    test:assertEquals(<string>IN_PROCESS, "IN_PROCESS");
    test:assertEquals(<string>COMMITTED, "COMMITTED");
    test:assertEquals(<string>CONFIRMED, "CONFIRMED");
    test:assertEquals(<string>ROLLED_BACK, "ROLLED_BACK");
}

@test:Config {}
function testBgRfcUnitConfigDefaults() {
    BgRfcUnitConfig config = {};
    test:assertEquals(config.queueNames, <string[]>[]);
    test:assertFalse(config.'lock);
    test:assertFalse(config.unitHistory);
    test:assertFalse(config.kernelTrace);
    test:assertFalse(config.commitCheck);
    test:assertTrue(config.unitId is ());
}

// --- Live tests ---

@test:Config {enable: enableLiveTests}
function testMultipleClients() returns error? {
    // Regression test: creating a second client must not fail with
    // "DestinationDataProvider already registered".
    Client first = check getTestClient();
    Client second = check new ({host, systemNumber, jcoClient, user, password});
    string firstTid = check first->createTid();
    string secondTid = check second->createTid();
    test:assertTrue(firstTid.length() > 0, "the first client must stay usable");
    test:assertTrue(secondTid.length() > 0, "the second client must be usable");
    test:assertNotEquals(firstTid, secondTid, "each client must get its own TID");
}

@test:Config {enable: enableLiveTests}
function testSendTRfcWithAutoTid() returns error? {
    Client sap = check getTestClient();
    string tid = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": tcpicRows("TRFC-AUTO")});
    test:assertTrue(tid.length() > 0, "expected a non-empty TID");
}

@test:Config {enable: enableLiveTests}
function testSendTRfcExactlyOnceRetry() returns error? {
    // The core exactly-once guarantee: sending twice under the SAME unconfirmed TID
    // must execute only once on the backend. (Data-level proof: exactly one
    // '-TRFC-RETRY' row for this run's marker in table TCPIC, checked by the
    // external verifier.) Manual confirmation finishes the protocol and also
    // gives confirmTid live coverage.
    Client sap = check getTestClient();
    string tid = check sap->createTid();
    string first = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": tcpicRows("TRFC-RETRY")},
            tid, autoConfirm = false);
    string second = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": tcpicRows("TRFC-RETRY")},
            tid, autoConfirm = false);
    test:assertEquals(first, tid);
    test:assertEquals(second, tid);
    check sap->confirmTid(tid);
}

@test:Config {enable: enableLiveTests}
function testSendQRfc() returns error? {
    Client sap = check getTestClient();
    string tid = check sap->sendQRfc("STFC_WRITE_TO_TCPIC", queueName, {"TCPICDAT": tcpicRows("QRFC")});
    test:assertTrue(tid.length() > 0, "expected a non-empty TID");
}

@test:Config {enable: enableLiveTests}
function testSendQRfcSequence() returns error? {
    // Both sends to the same queue must be accepted; in-order processing on the backend
    // is verified at data level by the external verifier (row order of -QRFC-ORD-1/-2).
    Client sap = check getTestClient();
    string tid1 = check sap->sendQRfc("STFC_WRITE_TO_TCPIC", queueName, {"TCPICDAT": tcpicRows("QRFC-ORD-1")});
    string tid2 = check sap->sendQRfc("STFC_WRITE_TO_TCPIC", queueName, {"TCPICDAT": tcpicRows("QRFC-ORD-2")});
    test:assertNotEquals(tid1, tid2, "each qRFC call must get its own TID");
}

@test:Config {enable: enableLiveTests}
function testSendQRfcEmptyQueueName() returns error? {
    Client sap = check getTestClient();
    string|Error result = sap->sendQRfc("STFC_WRITE_TO_TCPIC", "", {"TCPICDAT": tcpicRows("QRFC-EMPTY")});
    test:assertTrue(result is Error, "an empty queue name must be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("Queue name"), result.message());
    }
}

@test:Config {enable: enableLiveTests}
function testSendBgRfcUnitTypeT() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("BGRFC-T-1")}},
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("BGRFC-T-2")}}
    ], {unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_T, "a unit without queue names must be type T");
    test:assertEquals(unit.unitId.length(), 32, "expected a 32-character hexadecimal unit ID");

    BgRfcUnitState state = check waitForUnitProcessing(sap, unit);
    test:assertTrue(state is COMMITTED|CONFIRMED|IN_PROCESS,
            string `expected the unit to be accepted by the backend, got state '${state}'`);
}

@test:Config {enable: enableLiveTests}
function testSendBgRfcUnitTypeQ() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("BGRFC-Q")}}
    ], {queueNames: [queueName], unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_Q, "a unit with queue names must be type Q");

    BgRfcUnitState state = check waitForUnitProcessing(sap, unit);
    test:assertTrue(state is COMMITTED|CONFIRMED|IN_PROCESS,
            string `expected the unit to be accepted by the backend, got state '${state}'`);
}

@test:Config {enable: enableLiveTests}
function testConfirmBgRfcUnit() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("BGRFC-CONF")}}
    ], {unitHistory: true});
    BgRfcUnitState processed = check waitForUnitProcessing(sap, unit);
    test:assertEquals(processed, COMMITTED,
            "the unit must reach COMMITTED (processing finished) before confirmation");
    check sap->confirmBgRfcUnit(unit);
    BgRfcUnitState confirmed = check sap->getBgRfcUnitState(unit);
    // Without unit history the backend may already have deleted the status record.
    test:assertTrue(confirmed is CONFIRMED|NOT_FOUND,
            string `expected CONFIRMED (or cleaned up) after confirmation, got '${confirmed}'`);
}

@test:Config {enable: enableLiveTests}
function testSendTRfcUnknownFunction() returns error? {
    Client sap = check getTestClient();
    string|Error result = sap->sendTRfc("BALLERINA_NO_SUCH_FUNCTION");
    test:assertTrue(result is Error, "sending an unknown function must fail");
    if result is Error {
        test:assertTrue(result.message().includes("not found"), result.message());
    }
}

@test:Config {enable: enableLiveTests}
function testSendTRfcUnknownParameter() returns error? {
    Client sap = check getTestClient();
    string|Error result = sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"BOGUS_PARAM": "x"});
    test:assertTrue(result is Error, "sending an undefined parameter must fail");
    if result is Error {
        test:assertTrue(result.message().includes("BOGUS_PARAM"), result.message());
    }
}

// Polls until the unit leaves the in-flight states. COMMITTED is the terminal state from the
// sender's perspective until confirmBgRfcUnit is called (CONFIRMED only exists after that).
isolated function waitForUnitProcessing(Client sap, BgRfcUnitInfo unit) returns BgRfcUnitState|error {
    BgRfcUnitState state = NOT_FOUND;
    foreach int attempt in 0 ..< 10 {
        state = check sap->getBgRfcUnitState(unit);
        if state is COMMITTED|CONFIRMED|ROLLED_BACK {
            break;
        }
        runtime:sleep(1);
    }
    return state;
}

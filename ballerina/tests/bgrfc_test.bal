// Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
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
import ballerina/uuid;

// Tests for background RFC (bgRFC) units of work: sendBgRfcUnit, getBgRfcUnitState, and
// confirmBgRfcUnit.
//
// Uses STFC_WRITE_TO_TCPIC, a standard SAP basis RFC with no export parameters, which suits
// asynchronous unit execution.
//
// All tests are disabled by default. Set the required environment variables
// (see config.bal) to enable them.

const BGRFC_FUNCTION = "STFC_WRITE_TO_TCPIC";
const BGRFC_QUEUE = "BAL_JCO_BGRFC_QUEUE";

isolated function unitCall(string tag) returns RemoteFunctionCall {
    return {
        functionName: BGRFC_FUNCTION,
        parameters: {tableParameters: {"TCPICDAT": [{"LINE": "ballerina-bgrfc " + tag}]}}
    };
}

// A 32-character hexadecimal unit ID, unique per run.
isolated function newUnitId() returns string =>
        re `-`.replaceAll(uuid:createRandomUuid(), "").toUpperAscii();

// Polls until the unit leaves the in-flight states. COMMITTED is the terminal state from the
// sender's point of view: CONFIRMED only exists after confirmBgRfcUnit is called, so waiting
// for it here would never return.
isolated function awaitUnitProcessing(Client sapClient, BgRfcUnitInfo unit)
        returns BgRfcUnitState|error {
    BgRfcUnitState state = NOT_FOUND;
    foreach int _ in 0 ..< 10 {
        state = check sapClient->getBgRfcUnitState(unit);
        if state is COMMITTED|CONFIRMED|ROLLED_BACK {
            return state;
        }
        runtime:sleep(1);
    }
    return state;
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testSendBgRfcUnitTypeT() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo unit = check sapClient->sendBgRfcUnit([unitCall("t-1"), unitCall("t-2")],
            {unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_T,
            "A unit without queue names should be type T");
    test:assertEquals(unit.unitId.length(), 32,
            "A bgRFC unit ID should be 32 hexadecimal characters");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testSendBgRfcUnitTypeQ() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo unit = check sapClient->sendBgRfcUnit([unitCall("q")],
            {queueNames: [BGRFC_QUEUE], unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_Q,
            "Supplying queue names should make the unit type Q");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testBgRfcUnitLifecycle() returns error? {
    // commit -> COMMITTED -> confirm -> CONFIRMED is the order SAP defines.
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo unit = check sapClient->sendBgRfcUnit([unitCall("lifecycle")],
            {unitHistory: true});
    BgRfcUnitState processed = check awaitUnitProcessing(sapClient, unit);
    test:assertEquals(processed, COMMITTED,
            "A processed unit should reach COMMITTED before it is confirmed");
    check sapClient->confirmBgRfcUnit(unit);
    BgRfcUnitState confirmed = check sapClient->getBgRfcUnitState(unit);
    // Without unit history the backend may already have discarded the status record.
    test:assertTrue(confirmed is CONFIRMED|NOT_FOUND,
            "After confirmation the unit should report CONFIRMED, or NOT_FOUND once cleaned up");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testExplicitUnitIdIsUsed() returns error? {
    // A unit ID derived from a business key makes a repeated submission idempotent: SAP
    // recognises the unit and executes it only once.
    Client sapClient = check new (destinationConfig);
    string unitId = newUnitId();
    BgRfcUnitInfo first = check sapClient->sendBgRfcUnit([unitCall("explicit")],
            {unitId: unitId, unitHistory: true});
    test:assertEquals(first.unitId, unitId, "The supplied unit ID should be used");

    BgRfcUnitInfo second = check sapClient->sendBgRfcUnit([unitCall("explicit")],
            {unitId: unitId, unitHistory: true});
    test:assertEquals(second.unitId, unitId, "Re-committing the same unit ID should be accepted");

    // The backend must know the unit, which the locally echoed ID alone would not prove.
    BgRfcUnitState state = check awaitUnitProcessing(sapClient, first);
    test:assertNotEquals(state, NOT_FOUND,
            "SAP should have a record of the unit committed under this ID");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testUnitAttributesAccepted() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo unit = check sapClient->sendBgRfcUnit([unitCall("attrs")], {
        unitHistory: true,
        commitCheck: true,
        programName: "BAL_JCO_TEST",
        transactionCode: "SE38"
    });
    BgRfcUnitState state = check awaitUnitProcessing(sapClient, unit);
    test:assertNotEquals(state, NOT_FOUND,
            "A unit sent with attributes should exist on the backend");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testDuplicateQueueNameIsAccepted() returns error? {
    // JCo returns false from addQueueName when the queue is already assigned, which is
    // harmless and must not fail the call.
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo unit = check sapClient->sendBgRfcUnit([unitCall("dupqueue")],
            {queueNames: [BGRFC_QUEUE, BGRFC_QUEUE], unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_Q, "A duplicate queue name should be tolerated");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testInvalidQueueNameRejectedCleanly() returns error? {
    // SAP allows uppercase letters, digits and underscores only. JCo signals a violation with a
    // runtime exception, which must surface as an error rather than aborting the call.
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo|Error result = sapClient->sendBgRfcUnit([unitCall("badqueue")],
            {queueNames: ["not a queue"], unitHistory: true});
    test:assertTrue(result is Error, "An invalid queue name should be rejected");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testEmptyUnitRejected() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo|Error result = sapClient->sendBgRfcUnit([]);
    test:assertTrue(result is Error, "A unit with no function calls should be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("at least one function call"), result.message());
    }
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testUnknownUnitReportsNotFound() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo phantom = {unitId: newUnitId(), unitType: BGRFC_TYPE_T};
    BgRfcUnitState state = check sapClient->getBgRfcUnitState(phantom);
    test:assertEquals(state, NOT_FOUND, "A unit that was never sent should report NOT_FOUND");
}

@test:Config {
    enable: testsEnabled,
    groups: ["bgrfc"]
}
function testMalformedUnitIdRejected() returns error? {
    Client sapClient = check new (destinationConfig);
    BgRfcUnitInfo malformed = {unitId: "NOT-A-UNIT-ID", unitType: BGRFC_TYPE_T};
    BgRfcUnitState|Error result = sapClient->getBgRfcUnitState(malformed);
    test:assertTrue(result is Error, "A malformed unit ID should be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("32-character hexadecimal"), result.message());
    }
}

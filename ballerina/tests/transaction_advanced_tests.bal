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

// Extended coverage for the transactional API. The expectations here were established by
// probing a live SAP system first, so they assert observed backend behaviour rather than
// assumptions (for example: SAP silently accepts the confirmation of a TID it never issued).

import ballerina/test;
import ballerina/time;
import ballerina/uuid;

// --- Identifier validation (no SAP round trip needed for the rejection itself) ---

@test:Config {enable: enableLiveTests}
function testSendTRfcRejectsMalformedTid() returns error? {
    Client sap = check getTestClient();
    string|Error result = sap->sendTRfc("STFC_WRITE_TO_TCPIC",
            {"TCPICDAT": tcpicRows("BADTID")}, "NOT-A-VALID-TID");
    test:assertTrue(result is Error, "a malformed TID must be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("24-character hexadecimal"),
                "the error must explain the required TID format, got: " + result.message());
    }
}

@test:Config {enable: enableLiveTests}
function testConfirmTidRejectsMalformedTid() returns error? {
    Client sap = check getTestClient();
    Error? result = sap->confirmTid("XYZ");
    test:assertTrue(result is Error, "a malformed TID must be rejected");
}

@test:Config {enable: enableLiveTests}
function testBgRfcRejectsMalformedUnitId() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo malformed = {unitId: "TOO-SHORT", unitType: BGRFC_TYPE_T};
    BgRfcUnitState|Error state = sap->getBgRfcUnitState(malformed);
    test:assertTrue(state is Error, "a malformed unit ID must be rejected");
    if state is Error {
        test:assertTrue(state.message().includes("32-character hexadecimal"), state.message());
    }
}

// --- Identifier lifecycle edge cases (behaviour confirmed against the live system) ---

@test:Config {enable: enableLiveTests}
function testUnknownUnitStateIsNotFound() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo phantom = {unitId: newUnitId(), unitType: BGRFC_TYPE_T};
    BgRfcUnitState state = check sap->getBgRfcUnitState(phantom);
    test:assertEquals(state, NOT_FOUND, "a unit that was never sent must report NOT_FOUND");
}

@test:Config {enable: enableLiveTests}
function testConfirmTidIsIdempotent() returns error? {
    Client sap = check getTestClient();
    string tid = check sap->createTid();
    check sap->confirmTid(tid);
    // SAP accepts a repeated confirmation rather than failing.
    check sap->confirmTid(tid);
}

@test:Config {enable: enableLiveTests}
function testEmptyBgRfcUnitRejected() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo|Error result = sap->sendBgRfcUnit([]);
    test:assertTrue(result is Error, "a unit with no function calls must be rejected");
    if result is Error {
        test:assertTrue(result.message().includes("at least one function call"), result.message());
    }
}

// --- bgRFC unit configuration ---

@test:Config {enable: enableLiveTests}
function testExplicitUnitIdIsHonoured() returns error? {
    Client sap = check getTestClient();
    string wanted = newUnitId();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("EXPLICIT-UNITID")}}
    ], {unitId: wanted, unitHistory: true});
    test:assertEquals(unit.unitId, wanted, "the requested unit ID must be used");

    // Re-committing the same unit ID is accepted, and SAP executes the payload only once.
    BgRfcUnitInfo again = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("EXPLICIT-UNITID")}}
    ], {unitId: wanted, unitHistory: true});
    test:assertEquals(again.unitId, wanted);
}

@test:Config {enable: enableLiveTests}
function testMultipleQueueNamesProduceTypeQ() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("MULTIQ")}}
    ], {queueNames: [queueName + "_A", queueName + "_B"], unitHistory: true});
    test:assertEquals(unit.unitType, BGRFC_TYPE_Q, "queue names must make the unit type Q");
}

@test:Config {enable: enableLiveTests}
function testUnitAttributesAccepted() returns error? {
    Client sap = check getTestClient();
    BgRfcUnitInfo unit = check sap->sendBgRfcUnit([
        {functionName: "STFC_WRITE_TO_TCPIC", importParams: {"TCPICDAT": tcpicRows("ATTRS")}}
    ], {
        programName: "BAL_TEST_PROG",
        transactionCode: "SE38",
        commitCheck: true,
        unitHistory: true
    });
    test:assertEquals(unit.unitId.length(), 32);
}

// --- Parameter handling ---

@test:Config {enable: enableLiveTests}
function testMultiRowTableParameter() returns error? {
    Client sap = check getTestClient();
    ParamStructure[] rows = [];
    foreach int i in 1 ... 5 {
        rows.push({"LINE": string `${testMarker}-MULTIROW-${i}`});
    }
    string tid = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": rows});
    test:assertTrue(tid.length() > 0);
}

@test:Config {enable: enableLiveTests}
function testEmptyTableParameter() returns error? {
    Client sap = check getTestClient();
    ParamStructure[] rows = [];
    string tid = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": rows});
    test:assertTrue(tid.length() > 0, "an empty table must be accepted");
}

@test:Config {enable: enableLiveTests}
function testUnicodePayload() returns error? {
    Client sap = check getTestClient();
    string line = string `${testMarker}-UNI-\u{0DC3}\u{0DD2}\u{0D82}-\u{4F60}\u{597D}`;
    string tid = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {"TCPICDAT": [{"LINE": line}]});
    test:assertTrue(tid.length() > 0);
}

@test:Config {enable: enableLiveTests}
function testImportAndTableParametersTogether() returns error? {
    // RESTART_QNAME is an IMPORT parameter while TCPICDAT is a TABLES parameter, so this
    // exercises routing a single call across two different parameter lists.
    Client sap = check getTestClient();
    string tid = check sap->sendTRfc("STFC_WRITE_TO_TCPIC", {
        "RESTART_QNAME": queueName + "_RESTART",
        "TCPICDAT": tcpicRows("IMPORTPARAM")
    });
    test:assertTrue(tid.length() > 0);
}

@test:Config {enable: enableLiveTests}
function testFullyTypedStructureAndTable() returns error? {
    // STFC_STRUCTURE takes the RFCTEST structure, which covers every ABAP type the
    // connector converts: FLTP, INT1, INT2, INT4, RAW, DATS, TIMS and CHAR. The structure
    // goes to the IMPORT list and the table of the same type to the TABLES list, so one
    // call exercises the whole of ParameterProcessor.
    Client sap = check getTestClient();
    time:Date postingDate = {year: 2026, month: 8, day: 18};
    ParamStructure postingTime = {
        "year": 2026,
        "month": 8,
        "day": 18,
        "hour": 14,
        "minute": 5,
        "second": 12
    };
    ParamStructure row = {
        "RFCFLOAT": 1250.75,
        "RFCCHAR1": "R",
        // STFC_STRUCTURE's ABAP body increments the numeric fields of RFCTABLE, so the
        // values stay clear of the ABAP type limits (INT1 is 0-255, INT2 is signed 16-bit).
        "RFCINT1": 100,
        "RFCINT2": 32000,
        "RFCINT4": 2147483000,
        "RFCCHAR2": "KG",
        "RFCCHAR4": "1010",
        "RFCHEX3": <byte[]>[0xA1, 0xB2, 0xC3],
        "RFCDATE": postingDate,
        "RFCTIME": postingTime,
        "RFCDATA1": "MAT-1000",
        "RFCDATA2": testMarker
    };

    string tid = check sap->sendTRfc("STFC_STRUCTURE", {"IMPORTSTRUCT": row, "RFCTABLE": [row, row]});
    test:assertTrue(tid.length() > 0, "a fully typed structure and table must be accepted");
}

@test:Config {enable: enableLiveTests}
function testChangingParameterRouting() returns error? {
    // STFC_CHANGING exposes COUNTER as a CHANGING parameter and START_VALUE as an IMPORT
    // parameter, so this is the only call shape that exercises the changing-list branch.
    Client sap = check getTestClient();
    string tid = check sap->sendTRfc("STFC_CHANGING", {"START_VALUE": 10, "COUNTER": 5});
    test:assertTrue(tid.length() > 0, "a changing parameter must be routed and accepted");
}

// --- Concurrency ---

@test:Config {enable: enableLiveTests}
function testConcurrentSendsThroughOneClient() returns error? {
    Client sap = check getTestClient();
    future<string|Error> f1 = start sap->sendTRfc("STFC_WRITE_TO_TCPIC",
            {"TCPICDAT": tcpicRows("CONC-1")});
    future<string|Error> f2 = start sap->sendTRfc("STFC_WRITE_TO_TCPIC",
            {"TCPICDAT": tcpicRows("CONC-2")});
    future<string|Error> f3 = start sap->sendTRfc("STFC_WRITE_TO_TCPIC",
            {"TCPICDAT": tcpicRows("CONC-3")});
    future<string|Error> f4 = start sap->sendTRfc("STFC_WRITE_TO_TCPIC",
            {"TCPICDAT": tcpicRows("CONC-4")});

    string tid1 = check wait f1;
    string tid2 = check wait f2;
    string tid3 = check wait f3;
    string tid4 = check wait f4;

    map<int> uniqueTids = {};
    foreach string tid in [tid1, tid2, tid3, tid4] {
        uniqueTids[tid] = 1;
    }
    test:assertEquals(uniqueTids.length(), 4, "concurrent sends must each get their own TID");
}

isolated function newUnitId() returns string {
    string raw = uuid:createType4AsString();
    string id = "";
    foreach string:Char c in raw {
        if c != "-" {
            id += c;
        }
    }
    return id.toUpperAscii();
}

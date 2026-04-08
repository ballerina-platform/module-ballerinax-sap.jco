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
import ballerina/time;

// Tests for Client.execute() covering RFC function calls.
//
// All tests use standard SAP basis RFCs that are available on every ECC/S4 system:
//   RFC_PING        - connectivity smoke test, no parameters
//   STFC_CONNECTION - string echo (REQUTEXT → ECHOTEXT)
//   STFC_STRUCTURE  - structure/table echo using the RFCTEST ABAP type
//
// All tests are disabled by default. Set the required environment variables
// (see config.bal) to enable them.

// Output type for the STFC_CONNECTION RFC.
type StfcConnectionOutput record {|
    string ECHOTEXT?;
    string RESPTEXT?;
|};

// Mirrors the RFCTEST ABAP structure used by STFC_STRUCTURE.
type RfcTestStruct record {|
    float RFCFLOAT?;
    string RFCCHAR1?;
    string RFCCHAR2?;
    string RFCCHAR4?;
    int RFCINT1?;
    int RFCINT2?;
    int RFCINT4?;
    byte[] RFCHEX3?;
    time:TimeOfDay RFCTIME?;
    time:Date RFCDATE?;
    string RFCDATA1?;
    string RFCDATA2?;
|};

// Output type for the STFC_STRUCTURE RFC.
type StfcStructureOutput record {|
    RfcTestStruct ECHOSTRUCT?;
    string RESPTEXT?;
    RfcTestStruct[] RFCTABLE?;
|};

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteRfcPing() returns error? {
    Client sapClient = check new (destinationConfig);
    xml _ = check sapClient->execute("RFC_PING");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithStringImportParamAndTypedOutput() returns error? {
    Client sapClient = check new (destinationConfig);
    StfcConnectionOutput result = check sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "Hello SAP"}});
    test:assertEquals(result.ECHOTEXT, "Hello SAP", "ECHOTEXT should mirror the REQUTEXT input");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteReturningXml() returns error? {
    Client sapClient = check new (destinationConfig);
    xml result = check sapClient->execute("STFC_CONNECTION", {importParameters: {"REQUTEXT": "Test"}});
    test:assertTrue(result.length() > 0, "XML result should be non-empty");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteReturningJson() returns error? {
    Client sapClient = check new (destinationConfig);
    json result = check sapClient->execute("STFC_CONNECTION", {importParameters: {"REQUTEXT": "Test"}});
    test:assertNotEquals(result, (), "JSON result should not be null");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithStructureParam() returns error? {
    Client sapClient = check new (destinationConfig);
    RfcTestStruct importStruct = {RFCCHAR1: "X", RFCCHAR2: "AB", RFCINT1: 42, RFCFLOAT: 3.14};
    StfcStructureOutput result = check sapClient->execute("STFC_STRUCTURE",
            {importParameters: {"IMPORTSTRUCT": importStruct}});
    test:assertEquals(result.ECHOSTRUCT?.RFCCHAR1, "X", "ECHOSTRUCT should reflect IMPORTSTRUCT");
    test:assertEquals(result.ECHOSTRUCT?.RFCINT1, 42, "ECHOSTRUCT RFCINT1 should reflect IMPORTSTRUCT");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithTableParam() returns error? {
    Client sapClient = check new (destinationConfig);
    RfcTestStruct[] inputTable = [
        {RFCCHAR1: "A", RFCINT1: 1},
        {RFCCHAR1: "B", RFCINT1: 2}
    ];
    StfcStructureOutput result = check sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {}},
        tableParameters: {"RFCTABLE": inputTable}
    });
    test:assertEquals((result.RFCTABLE ?: []).length(), 3, "Returned RFCTABLE should have 3 rows (2 input + 1 appended by SAP)");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithDateAndTimeParams() returns error? {
    Client sapClient = check new (destinationConfig);
    time:Date testDate = {year: 2024, month: 6, day: 15};
    time:TimeOfDay testTime = {hour: 10, minute: 30, second: 0};
    RfcTestStruct importStruct = {RFCDATE: testDate, RFCTIME: testTime};
    StfcStructureOutput result = check sapClient->execute("STFC_STRUCTURE",
            {importParameters: {"IMPORTSTRUCT": importStruct}});
    test:assertEquals(result.ECHOSTRUCT?.RFCDATE, testDate, "Echo date should match the input date");
    test:assertEquals(result.ECHOSTRUCT?.RFCTIME, testTime, "Echo time should match the input time");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithEmptyFunctionName() returns error? {
    Client sapClient = check new (destinationConfig);
    json?|error result = sapClient->execute("");
    test:assertTrue(result is Error, "Expected an Error for an empty function name");
    test:assertTrue(result is ParameterError, "Expected a ParameterError for an empty function name");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithInvalidFunctionName() returns error? {
    Client sapClient = check new (destinationConfig);
    json?|error result = sapClient->execute("NONEXISTENT_RFC_FUNCTION_XYZ");
    test:assertTrue(result is Error, "Expected an Error for a non-existent RFC function name");
    test:assertTrue(result is ParameterError, "Expected a ParameterError for a non-existent RFC function");
}

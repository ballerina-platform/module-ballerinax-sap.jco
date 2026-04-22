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
function testExecuteReturningRfcRecord() returns error? {
    Client sapClient = check new (destinationConfig);
    RfcRecord result = check sapClient->execute("STFC_CONNECTION", {importParameters: {"REQUTEXT": "Test"}});
    test:assertNotEquals(result, {}, "RfcRecord result should not be empty");
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
    // Verify export params and table params are both present in the merged output.
    test:assertNotEquals(result.ECHOSTRUCT, (), "ECHOSTRUCT export param should be present alongside RFCTABLE in merged response");
}

// Type definitions for RFC_READ_TABLE — a real-world RFC that accepts two table parameters
// on input (OPTIONS = WHERE clause rows, FIELDS = column selection) and returns result rows
// as a table parameter on output (DATA). Used to test the primary table-parameter use case.
type OptionsRow record {|
    string TEXT;
|};

type FieldsRow record {|
    string FIELDNAME;
|};

type DataRow record {|
    string WA;
|};

type ReadTableResponse record {|
    DataRow[] DATA?;
|};

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
    RfcRecord|error result = sapClient->execute("");
    test:assertTrue(result is Error, "Expected an Error for an empty function name");
    test:assertTrue(result is ParameterError, "Expected a ParameterError for an empty function name");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithInvalidFunctionName() returns error? {
    Client sapClient = check new (destinationConfig);
    RfcRecord|error result = sapClient->execute("NONEXISTENT_RFC_FUNCTION_XYZ");
    test:assertTrue(result is Error, "Expected an Error for a non-existent RFC function name");
    test:assertTrue(result is ParameterError, "Expected a ParameterError for a non-existent RFC function");
}

// --- Table parameter gap coverage ---

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithExplicitEmptyParameters() returns error? {
    // Verify that parameters = {} (the default value) works when passed explicitly,
    // not just when the argument is omitted.
    Client sapClient = check new (destinationConfig);
    xml _ = check sapClient->execute("RFC_PING", {});
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithTableParamOnlyNoImportParams() returns error? {
    // tableParameters supplied without importParameters — exercises the null-check path
    // for importParameters in Client.java.
    Client sapClient = check new (destinationConfig);
    RfcTestStruct[] inputTable = [{RFCCHAR1: "C", RFCINT1: 3}];
    StfcStructureOutput result = check sapClient->execute("STFC_STRUCTURE", {
        tableParameters: {"RFCTABLE": inputTable}
    });
    test:assertEquals((result.RFCTABLE ?: []).length(), 2,
        "RFCTABLE should have 2 rows (1 input + 1 appended by SAP) when importParameters is omitted");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteWithEmptyTableParam() returns error? {
    // Zero-row table parameter — exercises the empty-array path in setTableParams
    // and verifies SAP still appends its own row on return.
    Client sapClient = check new (destinationConfig);
    StfcStructureOutput result = check sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {}},
        tableParameters: {"RFCTABLE": []}
    });
    test:assertEquals((result.RFCTABLE ?: []).length(), 1,
        "SAP should append one row to an empty input RFCTABLE");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteRfcReadTableWithMultipleTableInputsAndTableOutput() returns error? {
    // RFC_READ_TABLE is the canonical real-world RFC for this feature:
    //   - Two table parameters on input: OPTIONS (WHERE clause) and FIELDS (column list)
    //   - One table parameter on output: DATA (result rows)
    // This exercises sending multiple table params in a single call and receiving table output.
    Client sapClient = check new (destinationConfig);
    ReadTableResponse result = check sapClient->execute("RFC_READ_TABLE", {
        importParameters: {"QUERY_TABLE": "T000", "ROWCOUNT": 5},
        tableParameters: {
            "OPTIONS": [{"TEXT": "MANDT >= '000'"}],
            "FIELDS": [{"FIELDNAME": "MANDT"}, {"FIELDNAME": "MTEXT"}]
        }
    });
    test:assertTrue((result.DATA ?: []).length() > 0,
        "RFC_READ_TABLE DATA should contain at least one row for table T000");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteReturningXmlWithTableData() returns error? {
    // Verify XML serialization includes table parameter rows in the response.
    Client sapClient = check new (destinationConfig);
    xml result = check sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {"RFCCHAR1": "Z"}},
        tableParameters: {"RFCTABLE": [{"RFCCHAR1": "D", "RFCINT1": 4}]}
    });
    test:assertTrue(result.length() > 0, "XML result should be non-empty when table data is present");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteReturningRfcRecordWithTableData() returns error? {
    // Verify RfcRecord response includes table parameter rows in the merged output.
    Client sapClient = check new (destinationConfig);
    RfcRecord result = check sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {"RFCCHAR1": "Z"}},
        tableParameters: {"RFCTABLE": [{"RFCCHAR1": "E", "RFCINT1": 5}]}
    });
    test:assertNotEquals(result, {}, "RfcRecord result should not be empty when table data is present");
}

// --- Type mismatch and field-presence validation ---
//
// These tests verify that ExportParameterProcessor produces a ParameterError
// whenever the caller's declared return type is incompatible with the actual
// JCo-typed value returned by SAP, and that optional/nilable/required field
// semantics are honoured when a declared field is absent from the SAP response.

// Declares ECHOTEXT as int, but STFC_CONNECTION always returns it as a string.
type ScalarTypeMismatchOutput record {|
    int ECHOTEXT;
    string RESPTEXT?;
|};

// Declares ECHOSTRUCT as int, but STFC_STRUCTURE returns it as a JCo structure.
type StructureTypeMismatchOutput record {|
    int ECHOSTRUCT;
    string RESPTEXT?;
|};

// Declares RFCTABLE as a plain string, but STFC_STRUCTURE returns it as a JCo table.
type TableNonArrayTypeMismatchOutput record {|
    string RFCTABLE;
    string RESPTEXT?;
|};

// Declares RFCTABLE as byte[], but STFC_STRUCTURE returns a table of RFCTEST records.
// byte[] is a valid FieldType (accepted by the compiler), but its element type (byte)
// is not a RecordType, so the connector throws a ParameterError at runtime.
type TableWrongElementTypeMismatchOutput record {|
    byte[] RFCTABLE;
    string RESPTEXT?;
|};

// NONEXISTENT_SAP_FIELD does not exist in STFC_CONNECTION's export parameter list.
// Declared optional (field?) — should be silently absent with no error.
type EchoWithOptionalUnknownField record {|
    string ECHOTEXT?;
    string RESPTEXT?;
    string NONEXISTENT_SAP_FIELD?;
|};

// NONEXISTENT_SAP_FIELD does not exist in STFC_CONNECTION's export parameter list.
// Declared nilable (type?) — should be nil with no error.
type EchoWithNilableUnknownField record {|
    string? ECHOTEXT;
    string? RESPTEXT;
    string? NONEXISTENT_SAP_FIELD;
|};

// NONEXISTENT_SAP_FIELD does not exist in STFC_CONNECTION's export parameter list.
// Declared required (no ? suffix) — should produce a ParameterError.
type EchoWithRequiredUnknownField record {|
    string ECHOTEXT?;
    string RESPTEXT?;
    string NONEXISTENT_SAP_FIELD;
|};

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteScalarTypeMismatchReturnsParameterError() returns error? {
    Client sapClient = check new (destinationConfig);
    ScalarTypeMismatchOutput|error result = sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "Test"}});
    test:assertTrue(result is ParameterError,
            "Expected ParameterError when ECHOTEXT is declared as int but SAP returns a string");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteStructureTypeMismatchReturnsParameterError() returns error? {
    Client sapClient = check new (destinationConfig);
    StructureTypeMismatchOutput|error result = sapClient->execute("STFC_STRUCTURE",
            {importParameters: {"IMPORTSTRUCT": {}}});
    test:assertTrue(result is ParameterError,
            "Expected ParameterError when ECHOSTRUCT is declared as int but SAP returns a structure");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteTableNonArrayTypeMismatchReturnsParameterError() returns error? {
    Client sapClient = check new (destinationConfig);
    TableNonArrayTypeMismatchOutput|error result = sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {}},
        tableParameters: {"RFCTABLE": [{"RFCCHAR1": "A"}]}
    });
    test:assertTrue(result is ParameterError,
            "Expected ParameterError when RFCTABLE is declared as string but SAP returns a table");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteTableWrongElementTypeMismatchReturnsParameterError() returns error? {
    Client sapClient = check new (destinationConfig);
    TableWrongElementTypeMismatchOutput|error result = sapClient->execute("STFC_STRUCTURE", {
        importParameters: {"IMPORTSTRUCT": {}},
        tableParameters: {"RFCTABLE": [{"RFCCHAR1": "A"}]}
    });
    test:assertTrue(result is ParameterError,
            "Expected ParameterError when RFCTABLE is declared as string[] but SAP returns a table of records");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteOptionalFieldAbsentFromSapIsSkipped() returns error? {
    Client sapClient = check new (destinationConfig);
    EchoWithOptionalUnknownField result = check sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "Test"}});
    test:assertEquals(result.ECHOTEXT, "Test", "ECHOTEXT should be present from SAP response");
    test:assertEquals(result.NONEXISTENT_SAP_FIELD, (),
            "Optional field absent from SAP response should be absent in the result");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteNilableFieldAbsentFromSapIsNil() returns error? {
    Client sapClient = check new (destinationConfig);
    EchoWithNilableUnknownField result = check sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "Test"}});
    test:assertNotEquals(result.ECHOTEXT, (), "ECHOTEXT should be set from SAP response");
    test:assertEquals(result.NONEXISTENT_SAP_FIELD, (),
            "Nilable field absent from SAP response should be nil in the result");
}

@test:Config {
    enable: testsEnabled,
    groups: ["rfc-execute"]
}
function testExecuteRequiredFieldAbsentFromSapReturnsParameterError() returns error? {
    Client sapClient = check new (destinationConfig);
    EchoWithRequiredUnknownField|error result = sapClient->execute("STFC_CONNECTION",
            {importParameters: {"REQUTEXT": "Test"}});
    test:assertTrue(result is ParameterError,
            "Expected ParameterError when a required declared field is not present in the SAP response");
}

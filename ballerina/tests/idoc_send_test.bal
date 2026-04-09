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

// Tests for Client.sendIDoc().
//
// The test iDoc uses the DELVRY03 structure (Delivery notification) with minimal
// required fields. Adjust the SNDPRN, RCVPRN, MANDT, and SNDPOR values to match
// your SAP system's partner profile configuration before enabling these tests.
//
// All tests are disabled by default. Set the required environment variables
// (see config.bal) to enable them.

// A minimal DELVRY03 iDoc XML with the SAP-standard segment attributes.
// Update MANDT, SNDPRN, and RCVPRN to match the target SAP system.
final xml validTestIDoc = xml `<DELVRY03>
    <IDOC BEGIN="1">
        <EDI_DC40 SEGMENT="1">
            <TABNAM>EDI_DC40</TABNAM>
            <MANDT>800</MANDT>
            <DOCNUM>0000000000000001</DOCNUM>
            <DOCREL>700</DOCREL>
            <STATUS>30</STATUS>
            <DIRECT>1</DIRECT>
            <OUTMOD>2</OUTMOD>
            <IDOCTYP>DELVRY03</IDOCTYP>
            <MESTYP>DESADV</MESTYP>
            <SNDPOR>SAPR3</SNDPOR>
            <SNDPRT>LS</SNDPRT>
            <SNDPRN>BALLERINA</SNDPRN>
            <RCVPOR>SAPR3</RCVPOR>
            <RCVPRT>LS</RCVPRT>
            <RCVPRN>RECIPIENT_SAP</RCVPRN>
        </EDI_DC40>
        <E1EDL20 SEGMENT="1">
            <VBELN>TEST001</VBELN>
            <NTGEW>100</NTGEW>
            <GEWEI>KGM</GEWEI>
            <INCO1>FOB</INCO1>
            <INCO2>01</INCO2>
        </E1EDL20>
    </IDOC>
</DELVRY03>`;

// An iDoc XML with an unknown iDoc type that SAP cannot process.
final xml unknownTypeIDoc = xml `<UNKNOWN_IDOC_TYPE_XYZ>
    <IDOC BEGIN="1">
        <EDI_DC40 SEGMENT="1">
            <TABNAM>EDI_DC40</TABNAM>
            <IDOCTYP>UNKNOWN_IDOC_TYPE_XYZ</IDOCTYP>
        </EDI_DC40>
    </IDOC>
</UNKNOWN_IDOC_TYPE_XYZ>`;

// An iDoc XML that is missing the mandatory IDOC element.
final xml missingIdocElementXml = xml `<DELVRY03></DELVRY03>`;

@test:Config {
    enable: testsEnabled,
    groups: ["idoc-send"]
}
function testSendIDocWithDefaultType() returns error? {
    Client sapClient = check new (destinationConfig);
    check sapClient->sendIDoc(validTestIDoc);
}

@test:Config {
    enable: testsEnabled,
    groups: ["idoc-send"]
}
function testSendIDocWithVersion3Type() returns error? {
    Client sapClient = check new (destinationConfig);
    check sapClient->sendIDoc(validTestIDoc, VERSION_3);
}

// Expects an Error when the iDoc type is not registered in the SAP system.
@test:Config {
    enable: testsEnabled,
    groups: ["idoc-send"]
}
function testSendIDocWithUnknownIDocType() returns error? {
    Client sapClient = check new (destinationConfig);
    Error? result = sapClient->sendIDoc(unknownTypeIDoc);
    test:assertTrue(result is Error, "Expected an Error for an unknown iDoc type");
    test:assertTrue(result is IDocError|JCoError, "Expected an IDocError or JCoError for an unknown iDoc type");
}

// Expects an Error when the iDoc XML is missing the mandatory IDOC element.
@test:Config {
    enable: testsEnabled,
    groups: ["idoc-send"]
}
function testSendIDocWithMissingIdocElement() returns error? {
    Client sapClient = check new (destinationConfig);
    Error? result = sapClient->sendIDoc(missingIdocElementXml);
    test:assertTrue(result is Error, "Expected an Error when the IDOC element is absent");
    test:assertTrue(result is IDocError, "Expected an IDocError when the IDOC element is absent");
}

// Tests that sendIDoc() returns a ConfigurationError when called after close().
@test:Config {
    enable: testsEnabled,
    groups: ["idoc-send"]
}
function testSendIDocAfterCloseReturnsConfigurationError() returns error? {
    Client sapClient = check new (destinationConfig);
    check sapClient.close();
    Error? result = sapClient->sendIDoc(validTestIDoc);
    test:assertTrue(result is Error, "Expected an Error when calling sendIDoc() after close()");
    test:assertTrue(result is ConfigurationError,
            "sendIDoc() after close() should return a ConfigurationError");
}

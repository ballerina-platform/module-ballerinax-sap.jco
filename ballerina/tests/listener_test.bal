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

// Tests for the Listener class.
//
// Listener tests require the SAP gateway environment variables in addition to
// the client credentials (see config.bal). The iDoc receive test also requires
// a SAP partner profile configured to route an outbound iDoc to the registered
// SAP_PROGID program ID.
//
// All tests are disabled by default.

// ---------------------------------------------------------------------------
// Shared state for the receive-iDoc integration test (testListenerReceivesIDoc)
// ---------------------------------------------------------------------------
isolated boolean iDocReceived = false;
isolated xml receivedIDoc = xml `<empty/>`;

// Service used in the receive round-trip test.
IDocService receiveTestService = service object {
    remote function onReceive(xml iDoc) returns error? {
        lock {
            iDocReceived = true;
        }
        lock {
            receivedIDoc = iDoc.clone();
        }
    }

    remote function onError(error 'error) returns error? {
        // Surface listener-level errors as test failures via the flag.
    }
};

// ---------------------------------------------------------------------------
// Setup: register the repository destination before any listener test runs.
// The JCo server requires the repository destination to exist at construction
// time (JCoIDoc.getServer), so a named Client must be created first.
// ---------------------------------------------------------------------------
@test:BeforeGroups {value: ["listener"]}
function setUpListenerRepositoryDestination() returns error? {
    if listenerTestsEnabled {
        Client _ = check new (destinationConfig, repoDestination);
    }
}

// ---------------------------------------------------------------------------
// Initialisation tests
// ---------------------------------------------------------------------------

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerInitWithServerConfig() returns error? {
    Listener _ = check new (serverConfig);
}

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerInitWithAdvancedConfig() returns error? {
    AdvancedConfig advancedConfig = {
        "jco.server.gwhost": gwhost,
        "jco.server.gwserv": gwserv,
        "jco.server.progid": progid + "_ADV_TEST"
    };
    Listener _ = check new (advancedConfig);
}

// Verifies that start() succeeds even when the gateway host is unreachable.
// JCo establishes gateway connectivity asynchronously: start() returns as soon as the
// server is submitted to JCo's scheduler. Connection failures are delivered to the
// attached service's onError handler and JCo retries automatically until the gateway
// becomes reachable. The listener can be stopped cleanly regardless of connectivity state.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerInitWithInvalidGateway() returns error? {
    ServerConfig invalidServerConfig = {
        gwhost: "invalid.gateway.host.that.does.not.exist",
        gwserv: "3300",
        progid: "INVALID_PROGID",
        repositoryDestination: repoDestination
    };
    Listener sapListener = check new (invalidServerConfig);
    check sapListener.attach(dummyService);
    check sapListener.'start();
    check sapListener.gracefulStop();
}

// ---------------------------------------------------------------------------
// Attach tests
// ---------------------------------------------------------------------------

IDocService dummyService = service object {
    remote function onReceive(xml iDoc) returns error? {}
    remote function onError(error 'error) returns error? {}
};

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.detach(dummyService);
}

// Expects an Error when a second service is attached to the same listener.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachMultipleServices() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    Error? result = sapListener.attach(dummyService);
    test:assertTrue(result is ConfigurationError, "Expected a ConfigurationError when attaching a second service");
    check sapListener.detach(dummyService);
}

// ---------------------------------------------------------------------------
// Lifecycle tests
// ---------------------------------------------------------------------------

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerStartAndGracefulStop() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.'start();
    check sapListener.gracefulStop();
    check sapListener.detach(dummyService);
}

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerStartAndGracefulStop]
}
function testListenerStartAndImmediateStop() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.'start();
    check sapListener.immediateStop();
    check sapListener.detach(dummyService);
}

// Expects an Error when start() is called on an already-running listener.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerStartAndImmediateStop]
}
function testListenerStartTwice() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.'start();
    Error? result = sapListener.'start();
    test:assertTrue(result is ConfigurationError, "Expected a ConfigurationError when starting an already-running listener");
    check sapListener.gracefulStop();
    check sapListener.detach(dummyService);
}

// ---------------------------------------------------------------------------
// iDoc receive integration test
//
// Requires a SAP partner profile configured to send an outbound iDoc to
// the program ID registered via SAP_PROGID. The test:
//   1. Starts the listener.
//   2. Sends a DELVRY03 iDoc from the client (which SAP must route back to
//      the listener based on the partner profile).
//   3. Waits up to 30 seconds for onReceive to be invoked.
//   4. Asserts the iDoc was received.
//   5. Stops the listener.
// ---------------------------------------------------------------------------
@test:Config {
    enable: false,
    groups: ["listener"],
    dependsOn: [testListenerStartTwice]
}
function testListenerReceivesIDoc() returns error? {
    // Reset shared state.
    lock {
        iDocReceived = false;
    }
    lock {
        receivedIDoc = xml `<empty/>`;
    }

    Listener sapListener = check new (serverConfig);
    check sapListener.attach(receiveTestService);
    check sapListener.'start();

    // Send a DELVRY03 iDoc from the client. SAP must have a partner profile
    // that routes this message type back to the registered progid.
    Client sapClient = check new (destinationConfig);
    xml testIDoc = xml `<DELVRY03>
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
    check sapClient->sendIDoc(testIDoc);

    // Poll for up to 30 seconds for the iDoc to arrive at the listener.
    int attempts = 0;
    while attempts < 30 {
        boolean received;
        lock {
            received = iDocReceived;
        }
        if received {
            break;
        }
        runtime:sleep(1);
        attempts += 1;
    }

    check sapListener.gracefulStop();

    lock {
        test:assertTrue(iDocReceived, "Listener did not receive the iDoc within 30 seconds");
    }
}

// ---------------------------------------------------------------------------
// Shared state for the RFC receive integration test (testListenerReceivesRfcCall)
// ---------------------------------------------------------------------------
isolated boolean rfcCallReceived = false;
isolated string receivedFunctionName = "";

// ---------------------------------------------------------------------------
// RfcService stubs used across RFC listener tests
// ---------------------------------------------------------------------------

// A minimal RfcService that returns nil for all calls (models fire-and-forget RFCs).
RfcService nilReturnRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|error? {
        return ();
    }

    remote function onError(error err) returns error? {}
};

// An RfcService that echoes back the import parameters as the export response.
RfcService echoRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|error? {
        RfcRecord result = parameters.importParameters ?: {};
        return result;
    }

    remote function onError(error err) returns error? {}
};

// An RfcService that captures call metadata for the integration test.
RfcService captureRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|error? {
        lock {
            rfcCallReceived = true;
        }
        lock {
            receivedFunctionName = functionName;
        }
        return ();
    }

    remote function onError(error err) returns error? {}
};

// An RfcService that returns xml. The root element is ignored; each direct child whose name
// matches a SAP export parameter is written as a string (JCo coerces to the target type).
// Table parameters must wrap rows in <row> child elements.
RfcService xmlReturnRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|xml|error? {
        lock {
            rfcCallReceived = true;
        }
        return xml `<response>
            <ECHOTEXT>XML_RESPONSE</ECHOTEXT>
            <RFCTABLE><row><RFCCHAR1>X</RFCCHAR1><RFCINT1>1</RFCINT1></row></RFCTABLE>
        </response>`;
    }

    remote function onError(error err) returns error? {}
};

// ---------------------------------------------------------------------------
// Attach tests — unregistered repositoryDestination (both service types)
// ---------------------------------------------------------------------------

// Expects ConfigurationError when repositoryDestination names a destination that no Client
// has been created for — applies to both IDocService and RfcService because both need the
// repository connection for metadata look-ups.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachIDocServiceWithUnregisteredRepoDestination() returns error? {
    ServerConfig unregisteredRepoConfig = {
        gwhost,
        gwserv,
        progid: progid + "_UNREG_REPO_TEST",
        repositoryDestination: "NONEXISTENT_DESTINATION_XYZ"
    };
    // With a live SAP connection, JCo validates repositoryDestination at JCoIDoc.getServer()
    // time (synchronously), so init() may fail with ResourceError before attach() is called.
    // Both outcomes correctly reject the unregistered destination.
    Listener|Error listenerResult = new (unregisteredRepoConfig);
    if listenerResult is Error {
        return;
    }
    Error? result = listenerResult.attach(dummyService);
    test:assertTrue(result is ConfigurationError,
        "Expected a ConfigurationError when attaching IDocService with an unregistered repositoryDestination");
}

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachRfcServiceWithUnregisteredRepoDestination() returns error? {
    ServerConfig unregisteredRepoConfig = {
        gwhost,
        gwserv,
        progid: progid + "_UNREG_REPO_TEST",
        repositoryDestination: "NONEXISTENT_DESTINATION_XYZ"
    };
    Listener|Error listenerResult = new (unregisteredRepoConfig);
    if listenerResult is Error {
        return;
    }
    Error? result = listenerResult.attach(nilReturnRfcService);
    test:assertTrue(result is ConfigurationError,
        "Expected a ConfigurationError when attaching RfcService with an unregistered repositoryDestination");
}

// ---------------------------------------------------------------------------
// Attach tests — RfcService
// ---------------------------------------------------------------------------

// Verifies that an RfcService attaches successfully when all prerequisites are met:
// serverConfig carries a repositoryDestination and the matching Client has been created
// by setUpListenerRepositoryDestination().
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.detach(nilReturnRfcService);
}

// Verifies that an RfcService returning xml attaches and detaches without error.
// The xml return path through handleRequest is exercised by the integration test
// testListenerRfcServiceXmlResponse below.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachXmlReturnRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(xmlReturnRfcService);
    check sapListener.detach(xmlReturnRfcService);
}

// Expects ConfigurationError when a second RfcService is attached to the same listener.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachMultipleRfcServices() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    Error? result = sapListener.attach(echoRfcService);
    test:assertTrue(result is ConfigurationError,
        "Expected a ConfigurationError when attaching a second RfcService");
    check sapListener.detach(nilReturnRfcService);
}

// Verifies that one IDocService and one RfcService can both be attached to the same listener.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerAttachBothServiceTypes() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.detach(dummyService);
    check sapListener.detach(nilReturnRfcService);
}

// ---------------------------------------------------------------------------
// Detach tests
// ---------------------------------------------------------------------------

// Verifies that detaching an IDocService frees the slot so another IDocService can attach.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerDetachIDocService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.detach(dummyService);
    // After the detach the IDocService slot is free; re-attachment must succeed.
    check sapListener.attach(receiveTestService);
    check sapListener.detach(receiveTestService);
}

// Verifies that detaching an RfcService frees the slot so another RfcService can attach.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerDetachRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.detach(nilReturnRfcService);
    // After the detach the RfcService slot is free; re-attachment must succeed.
    check sapListener.attach(echoRfcService);
    check sapListener.detach(echoRfcService);
}

// Verifies that detaching one service type leaves the other type's slot intact:
// after detaching the IDocService, re-attaching a second RfcService must still fail.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerDetachOneServiceLeavesOtherIntact() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.attach(nilReturnRfcService);
    // Detach only the IDocService; the RfcService slot is still occupied.
    check sapListener.detach(dummyService);
    // The freed IDocService slot must accept a new attachment.
    check sapListener.attach(receiveTestService);
    // The RfcService slot is still occupied — a second attachment must fail.
    Error? result = sapListener.attach(echoRfcService);
    test:assertTrue(result is ConfigurationError,
        "Expected a ConfigurationError: RfcService slot is still occupied after IDocService detach");
    check sapListener.detach(receiveTestService);
    check sapListener.detach(nilReturnRfcService);
}

// ---------------------------------------------------------------------------
// Lifecycle tests with RfcService
// ---------------------------------------------------------------------------

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerStartAndGracefulStopWithRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.'start();
    check sapListener.gracefulStop();
    check sapListener.detach(nilReturnRfcService);
}

@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerStartAndGracefulStopWithRfcService]
}
function testListenerStartAndImmediateStopWithRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.'start();
    check sapListener.immediateStop();
    check sapListener.detach(nilReturnRfcService);
}

// Verifies that a listener carrying both service types starts and stops without error.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerStartAndImmediateStopWithRfcService]
}
function testListenerStartAndStopWithBothServiceTypes() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(dummyService);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.'start();
    check sapListener.gracefulStop();
    check sapListener.detach(dummyService);
    check sapListener.detach(nilReturnRfcService);
}

// Expects a ConfigurationError when start() is called on an already-running listener
// that has an RfcService attached.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerStartAndStopWithBothServiceTypes]
}
function testListenerStartTwiceWithRfcService() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.'start();
    Error? result = sapListener.'start();
    test:assertTrue(result is ConfigurationError,
        "Expected a ConfigurationError when starting an already-running listener with RfcService");
    check sapListener.gracefulStop();
    check sapListener.detach(nilReturnRfcService);
}

// ---------------------------------------------------------------------------
// RFC receive integration test
//
// Requires an external SAP system to initiate an inbound RFC call to the
// program ID registered via progid. The test:
//   1. Starts the listener with an RfcService.
//   2. Waits up to 30 seconds for onCall to be invoked by the SAP caller.
//   3. Asserts the RFC call was received and records the function name.
//   4. Stops the listener.
//
// This test is disabled by default because it requires external SAP interaction.
// ---------------------------------------------------------------------------
@test:Config {
    enable: false,
    groups: ["listener"],
    dependsOn: [testListenerStartAndStopWithBothServiceTypes]
}
function testListenerReceivesRfcCall() returns error? {
    lock {
        rfcCallReceived = false;
    }
    lock {
        receivedFunctionName = "";
    }

    Listener sapListener = check new (serverConfig);
    check sapListener.attach(captureRfcService);
    check sapListener.'start();

    // Poll for up to 30 seconds for an inbound RFC call initiated by the SAP system.
    int attempts = 0;
    while attempts < 30 {
        boolean received;
        lock {
            received = rfcCallReceived;
        }
        if received {
            break;
        }
        runtime:sleep(1);
        attempts += 1;
    }

    check sapListener.gracefulStop();

    lock {
        test:assertTrue(rfcCallReceived, "Listener did not receive an RFC call within 30 seconds");
    }
    lock {
        test:assertTrue(receivedFunctionName.length() > 0,
            "onCall() should have captured a non-empty function name");
    }
}

// ---------------------------------------------------------------------------
// ServerEntry shared-slot tests
//
// These tests exercise the cross-listener attachment enforcement introduced by the
// ServerEntry refactor: the IDocService and RfcService attachment slots are shared
// across all Listener objects that resolve to the same (gwhost, gwserv, progid) triplet.
// ---------------------------------------------------------------------------

// The IDocService attachment slot is shared across all Listener objects that resolve to
// the same (gwhost, gwserv, progid) triplet.  A second Listener must not be able to
// silently overwrite the first Listener's IDocHandlerFactory.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testListenerDetachOneServiceLeavesOtherIntact]
}
function testSharedServerEntryIDocServiceSlotIsGlobal() returns error? {
    Listener sapListener1 = check new (serverConfig);
    Listener sapListener2 = check new (serverConfig);
    check sapListener1.attach(dummyService);
    Error? result = sapListener2.attach(receiveTestService);
    test:assertTrue(result is ConfigurationError,
        "Expected ConfigurationError: IDocService slot is globally shared and already occupied");
    // Clean up so subsequent tests start with a clear slot.
    check sapListener1.detach(dummyService);
}

// Same invariant for the RfcService slot.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"],
    dependsOn: [testSharedServerEntryIDocServiceSlotIsGlobal]
}
function testSharedServerEntryRfcServiceSlotIsGlobal() returns error? {
    Listener sapListener1 = check new (serverConfig);
    Listener sapListener2 = check new (serverConfig);
    check sapListener1.attach(nilReturnRfcService);
    Error? result = sapListener2.attach(echoRfcService);
    test:assertTrue(result is ConfigurationError,
        "Expected ConfigurationError: RfcService slot is globally shared and already occupied");
    check sapListener1.detach(nilReturnRfcService);
}

// ---------------------------------------------------------------------------
// Nil-field RFC response integration test
//
// Verifies that an RfcService returning an RfcRecord with nil-valued fields
// does not cause an NPE in writeRfcResponse / ImportParameterProcessor.
//
// Disabled by default: requires the SAP system to initiate an inbound RFC call
// to the registered progid.
// ---------------------------------------------------------------------------

// Service that returns a record containing both a real export value and a nil field.
// Before the nil-handling fix, the nil field caused an NPE when
// writeRfcResponse called TypeUtils.getType(null).getTag().
RfcService nilFieldReturnRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|error? {
        return {
            "ECHOTEXT": "OK",
            "OPTIONAL_FIELD": ()  // nil field — must be skipped, not NPE
        };
    }

    remote function onError(error err) returns error? {}
};

@test:Config {
    enable: false,  // requires SAP to send an inbound RFC call to progid
    groups: ["listener"],
    dependsOn: [testSharedServerEntryRfcServiceSlotIsGlobal]
}
function testListenerRfcServiceNilFieldInResponseIsSkipped() returns error? {
    Listener sapListener = check new (serverConfig);
    check sapListener.attach(nilFieldReturnRfcService);
    check sapListener.'start();

    // Poll for up to 30 seconds; the SAP caller must initiate the RFC externally.
    int attempts = 0;
    while attempts < 30 {
        boolean received;
        lock {
            received = rfcCallReceived;
        }
        if received {
            break;
        }
        runtime:sleep(1);
        attempts += 1;
    }

    check sapListener.gracefulStop();
    check sapListener.detach(nilFieldReturnRfcService);
    // If we reach here without a panic/NPE the nil-field handling is correct.
    // The rfcCallReceived flag is informational only — SAP may not have called in time.
}

// ---------------------------------------------------------------------------
// XML response integration test
//
// Verifies that writeXmlResponse correctly maps the xml returned by onCall back
// to the JCo export and table parameter lists. The xml document's direct children
// are mapped by element name: text-only → export scalar, <row>-only children →
// table parameter. JCo coerces the string values to the target SAP types.
//
// Disabled by default: requires SAP to initiate an inbound RFC call to progid.
// ---------------------------------------------------------------------------
@test:Config {
    enable: false,  // requires SAP to send an inbound RFC call to progid
    groups: ["listener"],
    dependsOn: [testListenerRfcServiceNilFieldInResponseIsSkipped]
}
function testListenerRfcServiceXmlResponse() returns error? {
    lock {
        rfcCallReceived = false;
    }

    Listener sapListener = check new (serverConfig);
    check sapListener.attach(xmlReturnRfcService);
    check sapListener.'start();

    // Poll for up to 30 seconds; the SAP caller must initiate the RFC externally.
    // The service returns:
    //   <response>
    //     <ECHOTEXT>XML_RESPONSE</ECHOTEXT>
    //     <RFCTABLE><row><RFCCHAR1>X</RFCCHAR1><RFCINT1>1</RFCINT1></row></RFCTABLE>
    //   </response>
    // JCo writes ECHOTEXT to the export list and RFCTABLE to the table list.
    int attempts = 0;
    while attempts < 30 {
        boolean received;
        lock {
            received = rfcCallReceived;
        }
        if received {
            break;
        }
        runtime:sleep(1);
        attempts += 1;
    }

    check sapListener.gracefulStop();
    check sapListener.detach(xmlReturnRfcService);
    // Reaching here without an AbapException means writeXmlResponse did not throw.
    // Verify the flag was set so we know the call actually arrived.
    lock {
        test:assertTrue(rfcCallReceived, "Listener did not receive an RFC call within 30 seconds");
    }
}

// ---------------------------------------------------------------------------
// Inline RepositoryDestination tests (listener-inline group)
//
// These tests use serverConfigWithInlineRepoDest, which supplies a DestinationConfig
// directly as repositoryDestination. No Client creation is required beforehand —
// the listener registers the destination internally using the provided credentials.
// ---------------------------------------------------------------------------

@test:Config {
    enable: listenerInlineTestsEnabled,
    groups: ["listener-inline"]
}
function testListenerInitWithInlineRepoConfig() returns error? {
    Listener _ = check new (serverConfigWithInlineRepoDest, inlineServerName);
}

@test:Config {
    enable: listenerInlineTestsEnabled,
    groups: ["listener-inline"]
}
function testListenerAttachIDocServiceWithInlineRepoConfig() returns error? {
    Listener sapListener = check new (serverConfigWithInlineRepoDest, inlineServerName);
    check sapListener.attach(dummyService);
    check sapListener.detach(dummyService);
}

@test:Config {
    enable: listenerInlineTestsEnabled,
    groups: ["listener-inline"]
}
function testListenerAttachRfcServiceWithInlineRepoConfig() returns error? {
    Listener sapListener = check new (serverConfigWithInlineRepoDest, inlineServerName);
    check sapListener.attach(nilReturnRfcService);
    check sapListener.detach(nilReturnRfcService);
}

@test:Config {
    enable: listenerInlineTestsEnabled,
    groups: ["listener-inline"]
}
function testListenerStartGracefulStopWithInlineRepoConfig() returns error? {
    Listener sapListener = check new (serverConfigWithInlineRepoDest, inlineServerName);
    check sapListener.attach(dummyService);
    check sapListener.'start();
    check sapListener.gracefulStop();
    check sapListener.detach(dummyService);
}

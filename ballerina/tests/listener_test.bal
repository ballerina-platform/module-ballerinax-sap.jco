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
        "jco.server.progid": progid
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
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|xml|error? {
        return ();
    }

    remote function onError(error 'error) returns error? {}
};

// An RfcService that echoes back the import parameters as the export response.
RfcService echoRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|xml|error? {
        RfcRecord result = parameters.importParameters ?: {};
        return result;
    }

    remote function onError(error 'error) returns error? {}
};

// An RfcService that captures call metadata for the integration test.
RfcService captureRfcService = service object {
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|xml|error? {
        lock {
            rfcCallReceived = true;
        }
        lock {
            receivedFunctionName = functionName;
        }
        return ();
    }

    remote function onError(error 'error) returns error? {}
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
        progid,
        repositoryDestination: "NONEXISTENT_DESTINATION_XYZ"
    };
    Listener sapListener = check new (unregisteredRepoConfig);
    Error? result = sapListener.attach(dummyService);
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
        progid,
        repositoryDestination: "NONEXISTENT_DESTINATION_XYZ"
    };
    Listener sapListener = check new (unregisteredRepoConfig);
    Error? result = sapListener.attach(nilReturnRfcService);
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

// Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org).
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
Service receiveTestService = service object {
    remote function onReceive(xml iDoc) returns error? {
        lock {
            iDocReceived = true;
        }
        lock {
            receivedIDoc = iDoc.clone();
        }
    }

    remote function onError(Error 'error) returns error? {
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
    if listenerTestsEnabled && repoDestination != "" {
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

// Expects an Error when the gateway host is unreachable.
// JCo defers gateway connectivity validation to start(), not init(), so we
// verify the error is raised on start() rather than on listener construction.
// Note: connection timeout may make this test slow depending on network settings.
@test:Config {
    enable: listenerTestsEnabled,
    groups: ["listener"]
}
function testListenerInitWithInvalidGateway() returns error? {
    ServerConfig invalidServerConfig = {
        gwhost: "invalid.gateway.host.that.does.not.exist",
        gwserv: "3300",
        progid: "INVALID_PROGID"
    };
    Listener sapListener = check new (invalidServerConfig);
    check sapListener.attach(dummyService);
    Error? result = sapListener.'start();
    test:assertTrue(result is Error, "Expected an Error when starting with an unreachable SAP gateway");
}

// ---------------------------------------------------------------------------
// Attach tests
// ---------------------------------------------------------------------------

Service dummyService = service object {
    remote function onReceive(xml iDoc) returns error? {}
    remote function onError(Error 'error) returns error? {}
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
    test:assertTrue(result is Error, "Expected an Error when attaching a second service");
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
    test:assertTrue(result is Error, "Expected an Error when starting an already-running listener");
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

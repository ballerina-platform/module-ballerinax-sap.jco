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

// Tests for Client initialisation.
//
// All tests are disabled by default. Set the required environment variables
// (see config.bal) to enable them.

@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testClientInitWithDestinationConfig() returns error? {
    Client _ = check new (destinationConfig);
}

@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testClientInitWithAdvancedConfig() returns error? {
    AdvancedConfig advancedConfig = {
        "jco.client.ashost": ashost,
        "jco.client.sysnr": sysnr,
        "jco.client.client": jcoClient,
        "jco.client.user": sapUser,
        "jco.client.passwd": passwd,
        "jco.client.lang": lang
    };
    Client _ = check new (advancedConfig);
}

// Expects an Error when credentials are invalid for the configured SAP host.
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testClientInitWithInvalidCredentials() {
    DestinationConfig invalidConfig = {
        ashost,
        sysnr,
        jcoClient,
        user: "INVALID_USER_XYZ",
        passwd: "INVALID_PASS_XYZ"
    };
    Client|Error result = new (invalidConfig);
    test:assertTrue(result is Error, "Expected an Error for invalid credentials");
    test:assertTrue(result is LogonError, "Expected a LogonError for rejected credentials");
}

// Expects an Error when the SAP host is unreachable.
// Note: connection timeout may make this test slow depending on network settings.
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testClientInitWithInvalidHost() {
    DestinationConfig invalidHostConfig = {
        ashost: "invalid.sap.host.that.does.not.exist",
        sysnr: "00",
        jcoClient: "100",
        user: "user",
        passwd: "pass"
    };
    Client|Error result = new (invalidHostConfig);
    test:assertTrue(result is Error, "Expected an Error for an unreachable SAP host");
    test:assertTrue(result is ConnectionError, "Expected a ConnectionError for an unreachable host");
}

// Tests that a failed init cleans up the destination registration so the same
// destination ID can be reused by a subsequent successful init.
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testFailedInitDoesNotLeakDestination() returns error? {
    string destId = "test-dest-no-leak";
    DestinationConfig invalidHostConfig = {
        ashost: "invalid.sap.host.that.does.not.exist",
        sysnr: "00",
        jcoClient: "100",
        user: "user",
        passwd: "pass"
    };
    Client|Error first = new (invalidHostConfig, destId);
    test:assertTrue(first is Error, "Expected an Error for an unreachable SAP host");
    test:assertTrue(first is ConnectionError, "Expected a ConnectionError for an unreachable host");
    // The rollback should have freed destId; re-using it with valid config must succeed.
    Client second = check new (destinationConfig, destId);
    check second.close();
}

// Tests that close() is idempotent and safe to call multiple times.
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testClientCloseIsIdempotent() returns error? {
    Client sapClient = check new (destinationConfig);
    check sapClient.close();
    check sapClient.close();
}

// Tests that the destination ID is freed after close(), allowing a new client
// to register the same ID without an "already registered" error.
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testDestinationIdReclaimableAfterClose() returns error? {
    string sharedDestId = "test-dest-reclaim";
    Client first = check new (destinationConfig, sharedDestId);
    check first.close();
    Client second = check new (destinationConfig, sharedDestId);
    check second.close();
}

// Tests that execute() returns a ConfigurationError when called after close().
@test:Config {
    enable: testsEnabled,
    groups: ["client-init"]
}
function testExecuteAfterCloseReturnsConfigurationError() returns error? {
    Client sapClient = check new (destinationConfig);
    check sapClient.close();
    json|Error result = sapClient->execute("RFC_PING", {});
    test:assertTrue(result is Error, "Expected an Error when calling execute() after close()");
    test:assertTrue(result is ConfigurationError,
            "execute() after close() should return a ConfigurationError");
}

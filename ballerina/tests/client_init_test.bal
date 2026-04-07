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

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

// Test configuration populated from Config.toml (or the BAL_CONFIG_DATA environment variable).
//
// To run the tests, create a Config.toml file in the ballerina/tests/ directory with the
// values for a live SAP ECC instance, then run `bal test` from the ballerina/ directory.
//
// Example Config.toml:
//
//   # Required for all tests
//   ashost   = "my-sap-host.example.com"
//   sysnr    = "00"
//   jcoClient = "100"
//   sapUser  = "MYUSER"
//   passwd   = "S3cr3t!"
//
//   # Optional — defaults to "EN" when omitted
//   lang = "EN"
//
//   # Required for listener tests
//   gwhost = "my-sap-host.example.com"
//   gwserv = "sapgw00"
//   progid = "MY_PROG_ID"
//
//   # Optional — repository destination for iDoc metadata resolution (listener tests)
//   repoDestination = ""

// Client credentials
configurable string ashost = "";
configurable string sysnr = "";
configurable string jcoClient = "";
configurable string sapUser = "";
configurable string passwd = "";
configurable string lang = "EN";

// Server / gateway configuration (listener tests)
configurable string gwhost = "";
configurable string gwserv = "";
configurable string progid = "";
configurable string repoDestination = "";

// Tests are enabled only when all mandatory client credentials are configured.
final boolean testsEnabled = ashost != "" && sysnr != "" && jcoClient != ""
    && sapUser != "" && passwd != "";

// Listener tests additionally require the SAP gateway configuration.
final boolean listenerTestsEnabled = testsEnabled && gwhost != "" && gwserv != "" && progid != "";

final DestinationConfig destinationConfig = {
    ashost,
    sysnr,
    jcoClient,
    user: sapUser,
    passwd,
    lang
};

function buildServerConfig() returns ServerConfig {
    if repoDestination != "" {
        return {gwhost, gwserv, progid, repositoryDestination: repoDestination};
    }
    return {gwhost, gwserv, progid};
}

final ServerConfig serverConfig = buildServerConfig();

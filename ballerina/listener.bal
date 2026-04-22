// Copyright (c) 2024 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
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

import ballerina/jballerina.java as java;
import ballerina/uuid;

# SAP JCo IDoc listener that registers as a JCo server with the SAP gateway and forwards incoming IDocs to a configured IDoc handler.
public isolated class Listener {

    # Registers a JCo IDoc server with the SAP gateway. Reuses an existing server for the same gateway host, gateway service, and program ID combination.
    #
    # + serverConfig - Connection configuration for the JCo IDoc server
    # + serverName - Unique name used to register the server with the JCo framework
    # + return - An error if the server cannot be registered
    public isolated function init(ServerConfig|AdvancedConfig serverConfig, string serverName = uuid:createType4AsString()) returns Error? {
        return externInit(self, serverConfig, serverName);
    }

    # Registers an IDoc handler to receive incoming IDoc documents. Only one handler may be registered at a time.
    #
    # + s - The IDoc handler to register
    # + name - Optional handler name
    # + return - An error if the handler cannot be registered
    public isolated function attach(Service s, string[]|string? name = ()) returns Error? =
    @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Starts the JCo server and begins accepting IDoc connections from the SAP gateway.
    #
    # + return - An error if the server cannot be started
    public isolated function 'start() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Unregisters the IDoc handler without stopping the JCo server.
    #
    # + s - The IDoc handler to unregister
    # + return - An error if the unregister operation fails
    public isolated function detach(Service s) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server and blocks until it fully leaves the stopping state (up to 15 seconds).
    #
    # + return - An error if the server cannot be stopped
    public isolated function gracefulStop() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server immediately.
    #
    # + return - An error if the server cannot be stopped
    public isolated function immediateStop() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;
}

isolated function externInit(Listener jcoListener, ServerConfig|AdvancedConfig serverConfig, string serverName) returns Error? = @java:Method {
    name: "init",
    'class: "io.ballerina.lib.sap.Listener"
} external;

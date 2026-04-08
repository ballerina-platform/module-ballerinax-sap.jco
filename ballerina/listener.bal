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

# SAP JCo IDoc listener that registers as a JCo server with the SAP gateway and dispatches incoming IDocs to an attached service.
public isolated class Listener {

    # Registers a JCo IDoc server with the SAP gateway; reuses an existing server for the same (gwhost, gwserv, progid) combination.
    #
    # + serverConfig - JCo server connection parameters (`ServerConfig` or `AdvancedConfig`).
    # + serverName - Unique name used to register the server with the JCo framework.
    # + return - An error if the server cannot be registered.
    public isolated function init(ServerConfig|AdvancedConfig serverConfig, string serverName = uuid:createType4AsString()) returns Error? {
        return externInit(self, serverConfig, serverName);
    }

    # Attaches a service to receive incoming IDoc documents; only one service may be attached at a time.
    #
    # + s - The service to attach.
    # + name - Optional service name (not used by the JCo transport).
    # + return - An error if the service cannot be attached.
    public isolated function attach(Service s, string[]|string? name = ()) returns Error? =
    @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Starts the JCo server and begins accepting IDoc connections from the SAP gateway.
    #
    # + return - An error if the server cannot be started.
    public isolated function 'start() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Detaches the service and clears the server reference without stopping the JCo server.
    #
    # + s - The service to detach.
    # + return - An error if the detach operation fails.
    public isolated function detach(Service s) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server and blocks until it fully leaves the stopping state (up to 15 seconds).
    #
    # + return - An error if the server cannot be stopped.
    public isolated function gracefulStop() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server immediately; currently behaves identically to `gracefulStop`.
    #
    # + return - An error if the server cannot be stopped.
    public isolated function immediateStop() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;
}

isolated function externInit(Listener jcoListener, ServerConfig|AdvancedConfig serverConfig, string serverName) returns Error? = @java:Method {
    name: "init",
    'class: "io.ballerina.lib.sap.Listener"
} external;

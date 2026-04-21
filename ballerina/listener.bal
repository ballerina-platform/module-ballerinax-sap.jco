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

# SAP JCo listener that registers as a JCo server with the SAP gateway and forwards
# incoming IDocs and inbound RFC calls to the attached handlers.
public isolated class Listener {

    # Registers a JCo server with the SAP gateway. Reuses an existing server for the same
    # combination of gateway host, gateway service, and program ID.
    #
    # + serverConfig - Connection configuration for the JCo server
    # + serverName - Unique name used to register the server with the JCo framework
    # + return - An error if the server cannot be registered
    public isolated function init(ServerConfig|AdvancedConfig serverConfig,
                                  string serverName = uuid:createType4AsString()) returns Error? {
        AdvancedConfig listenerConfig;
        if serverConfig is ServerConfig {
            RepositoryDestination repoDest = serverConfig.repositoryDestination;
            if repoDest is string {
                listenerConfig = {
                    "jco.server.gwhost": serverConfig.gwhost,
                    "jco.server.gwserv": serverConfig.gwserv,
                    "jco.server.progid": serverConfig.progid,
                    "jco.server.connection_count": serverConfig.connectionCount.toString(),
                    "jco.server.repository_destination": repoDest
                };
            } else {
                // DestinationConfig: register an internal JCo destination using the serverName UUID.
                // Non-jco.server.* keys are routed to advancedDestinationConfig by the Java init().
                listenerConfig = {
                    "jco.server.gwhost": serverConfig.gwhost,
                    "jco.server.gwserv": serverConfig.gwserv,
                    "jco.server.progid": serverConfig.progid,
                    "jco.server.connection_count": serverConfig.connectionCount.toString(),
                    "jco.server.repository_destination": serverName,
                    "jco.client.ashost": repoDest.ashost,
                    "jco.client.sysnr": repoDest.sysnr,
                    "jco.client.client": repoDest.jcoClient,
                    "jco.client.user": repoDest.user,
                    "jco.client.passwd": repoDest.passwd,
                    "jco.client.lang": repoDest.lang,
                    "jco.client.group": repoDest.group
                };
            }
        } else {
            // Type narrowing doesn't work here hence casting to AdvancedConfig directly
            listenerConfig = <AdvancedConfig> serverConfig;
        }
        return externInit(self, listenerConfig, serverName);
    }

    # Attaches a service to the listener. At most one IDoc handler and one RFC handler may
    # be attached at the same time. The listener's repository destination must either
    # reference the ID of an already-initialised client, or supply SAP credentials that are
    # registered as an internal JCo destination automatically at initialization time.
    #
    # + s - The service to attach; must be an IDoc or RFC service
    # + name - Optional service name (unused at runtime)
    # + return - An error if the repository destination is not registered, a handler of the
    #            same kind is already attached, or attachment fails
    public isolated function attach(IDocService|RfcService s, string[]|string? name = ()) returns Error? =
    @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Starts the JCo server and returns immediately. Gateway connectivity is established
    # asynchronously by JCo's internal connection threads — a successful return only means
    # the server has been submitted to JCo's scheduler, not that the gateway handshake is
    # complete. If the gateway is unreachable, JCo retries automatically and delivers each
    # failure to the attached handler's error callback as an execution error. When the
    # gateway becomes reachable again, JCo reconnects silently and the errors stop; no
    # listener restart is needed.
    #
    # + return - An error only for pre-flight failures such as an uninitialised or
    #            already-started listener
    public isolated function 'start() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Unregisters a service from the listener without stopping the JCo server. A handler of
    # the other kind, if attached, continues to operate. Services may be re-attached to the
    # same listener after being detached.
    #
    # + s - The service to detach
    # + return - An error if the detach operation fails
    public isolated function detach(IDocService|RfcService s) returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server and blocks until it fully leaves the stopping state (up to
    # 15 seconds). In-flight requests are allowed to complete before the server stops.
    #
    # + return - An error if the server cannot be stopped
    public isolated function gracefulStop() returns Error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Stops the JCo server immediately without waiting for in-flight requests to complete.
    # Use graceful stop when a clean shutdown is possible.
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

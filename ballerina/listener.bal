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

# Ballerina iDoc Listener
public isolated class Listener {

    # Initializes a Listener object with the given connection configuration.
    # which is used to listen to iDoc messages.
    #
    # + configurations - The configurations required to initialize the listener.
    # + return - An error if the initialization fails.
    public isolated function init(*DestinationConfig configurations) returns Error? {
        configurations["destinationId"] = uuid:createType4AsString();
        Error? initResult = externInit(self, configurations);
        return initResult;
    }

    # Attach a listener to the iDoc listener.
    #
    # + s - The service to be attached.
    # + name - The name of the service.
    # + return - An error if the attachment fails.
    public isolated function attach(Service s, string[]|string? name = ()) returns error? =
    @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Start the iDoc listener.
    #
    # + return - An error if the start fails.
    public isolated function 'start() returns error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Detach a service from the iDoc listener.
    #
    # + s - The service to be detached.
    # + return - An error if the detachment fails.
    public isolated function detach(Service s) returns error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Gracefully stop the iDoc listener.
    #
    # + return - An error if the graceful stop fails.
    public isolated function gracefulStop() returns error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;

    # Immediately stop the iDoc listener.
    #
    # + return - An error if the immediate stop fails.
    public isolated function immediateStop() returns error? = @java:Method {
        'class: "io.ballerina.lib.sap.Listener"
    } external;
}

isolated function externInit(Listener jcoListener, DestinationConfig configurations) returns Error? = @java:Method {
    name: "init",
    'class: "io.ballerina.lib.sap.Listener"
} external;

// Copyright (c) 2024 WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

# Handles IDocs pushed from an SAP system.
public type IDocService distinct service object {
    # Invoked when an IDoc is received from the SAP system.
    #
    # + iDoc - The received IDoc in XML format
    # + return - An error if processing fails
    remote function onReceive(xml iDoc) returns error?;

    # Invoked when a framework-level error occurs while the listener is handling a request.
    # This covers JCo gateway and server errors surfaced asynchronously by the JCo runtime
    # (connection retries, registration failures) and pre-dispatch failures such as IDoc
    # rendering or parsing. It is not invoked for errors raised by the IDoc receive handler
    # itself — IDoc delivery is fire-and-forget and per-call errors are logged instead.
    # Because the listener starts before the gateway handshake completes, this handler is
    # also the primary signal for connectivity problems: JCo retries automatically on every
    # failed attempt, and stops once the gateway becomes reachable again.
    #
    # + err - The error that occurred
    # + return - An error if the error handler itself fails
    remote function onError(error err) returns error?;
};

# Handles inbound RFC calls from an SAP system. SAP invokes the service as if it were a
# registered RFC function module.
public type RfcService distinct service object {
    # Invoked synchronously when SAP calls a function module registered on this server.
    # The return value is serialized and sent back to the SAP caller as the RFC response.
    #
    # + functionName - Name of the RFC function module being called
    # + parameters - Import and table parameters sent by the SAP caller
    # + return - The RFC response to send back to SAP, or an error. An empty response is
    #            valid for fire-and-forget RFCs. An error response causes an ABAP exception
    #            to be raised back to the SAP caller.
    remote function onCall(string functionName, RfcParameters parameters) returns RfcRecord|xml|error?;

    # Invoked when a framework-level error occurs while the listener is handling a request.
    # This covers JCo gateway and server errors surfaced asynchronously by the JCo runtime
    # (connection retries, registration failures), pre-dispatch failures such as RFC parameter
    # construction, and post-dispatch failures such as RFC response serialization. It is not
    # invoked for errors raised by the RFC call handler itself — those surface to the SAP
    # caller as an ABAP exception. Because the listener starts before the gateway handshake
    # completes, this handler is also the primary signal for connectivity problems: JCo
    # retries automatically on every failed attempt, and stops once the gateway becomes
    # reachable again.
    #
    # + err - The error that occurred
    # + return - An error if the error handler itself fails
    remote function onError(error err) returns error?;
};

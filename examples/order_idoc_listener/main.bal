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

import ballerina/io;
import ballerina/xmldata;
import ballerinax/sap.jco;

// Configurable variables to hold the configuration values from Config.toml
configurable jco:ServerConfig serverConfig = ?;
configurable jco:DestinationConfig destinationConfig = ?;

// Initialize iDoc listener
listener jco:Listener idocListener = new (serverConfig, destinationConfig);

// Service to process incoming iDocs
service jco:Service on idocListener {
    remote function onReceive(xml iDoc) returns error? {
        // Parse iDoc XML to iDoc record
        ORDERS05 iDocRecord = check xmldata:fromXml(iDoc);

        // Transform iDoc to internal order format
        InternalOrder internalOrder = transform(iDocRecord);

        // Process the internal order in inventory system (logic not shown)
        check processOrder(internalOrder);
    }

    remote function onError(error 'error) returns error? {
        io:println("Error processing iDoc: ", 'error.message());
    }

}

function transform(ORDERS05 orders05) returns InternalOrder => {
    quantity: (orders05.IDOC.E1EDP01).length(),
    supplierId: orders05.IDOC.EDI_DC40.SNDPRT,
    partId: orders05.IDOC.EDI_DC40.DOCNUM,
    orderDate: orders05.IDOC.E1EDK01.DOCDAT
};

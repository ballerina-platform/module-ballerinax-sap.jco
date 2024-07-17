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

import ballerina/data.xmldata;
import ballerina/http;
import ballerina/io;
import ballerinax/sap.jco;

configurable jco:DestinationConfig sapConfig = ?;
configurable string apiEndpoint = ?;

public function main() returns error? {

    // Initialize SAP client and Logistics API client
    jco:Client sapClient = check new (sapConfig);
    http:Client logisticsApi = check new (apiEndpoint);

    // Get latest shipment data from logistics API
    ShipmentData[] shipments = check logisticsApi->get("/shipments/latest");

    // Iterate through the shipment data and send iDocs to SAP
    foreach ShipmentData shipment in shipments {

        // Transform shipment data to iDoc format
        DELVRY03 iDocRecord = transform(shipment);

        // Convert iDoc record to XML
        xml iDoc = check xmldata:toXml(iDocRecord);

        // Write iDoc to a file and send to SAP
        check io:fileWriteXml("resources/generated_iDocs/" + shipment.orderId + ".xml", iDoc);
        check sapClient->sendIDoc(iDoc.toString());
        io:println("iDoc sent for Order ID: ", shipment.orderId);
    }
}

function transform(ShipmentData shipmentData) returns DELVRY03 => {
    IDOC: {
        EDI_DC40: {
            DOCNUM: shipmentData.orderId
        },
        E1EDL20: {
            VBELN: shipmentData.orderId,
            NTGEW: shipmentData.quantity.toString(),
            INCO1: shipmentData.destination
        }
    }

};

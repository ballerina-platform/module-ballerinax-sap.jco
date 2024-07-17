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

import ballerina/http;
import ballerina/io;
import ballerinax/sap.jco;

// Configurable variables to hold the configuration values from Config.toml
configurable jco:DestinationConfig sapConfig = ?;
configurable string apiEndpoint = ?;

public function main() returns error? {

    // Create SAP and HTTP clients
    jco:Client sapClient = check new (sapConfig);
    http:Client logisticsApi = check new (apiEndpoint);

    // Fetch inventory data from the API
    ApiInventoryData[] inventoryData = check logisticsApi->get("/latest");

    // Update inventory data in SAP for each material in the inventory data
    foreach ApiInventoryData data in inventoryData {
        SapUpdateResponse? result = check sapClient->execute("UPDATE_INVENTORY", transform(data));
        io:println("Update Status for Material ", data.widgetId, ": ", result?.status);
    }
}

function transform(ApiInventoryData apiInventoryData) returns SapInventoryInput => {
    plant: apiInventoryData.location,
    materialId: apiInventoryData.widgetId,
    stockLevel: apiInventoryData.quantity
};

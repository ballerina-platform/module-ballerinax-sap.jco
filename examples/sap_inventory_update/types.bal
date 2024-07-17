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

// Define the record to hold the API response
type ApiInventoryData record {
    string widgetId;
    int quantity;
    string location;
    string lastUpdated;
};

// Define the record for SAP RFC function input
type SapInventoryInput record {| 
    string materialId;
    int stockLevel;
    string plant;
    |};

// Define the record for RFC function output
type SapUpdateResponse record {|
    string status;
    string message;
|};

// Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org).
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

import ballerina/http;
import ballerina/io;
import ballerinax/sap.jco;

configurable jco:DestinationConfig sapConfig = ?;
configurable string catalogApiEndpoint = ?;

const string FIELD_DELIMITER = "|";

public function main() returns error? {
    jco:Client sapClient = check new (sapConfig);
    http:Client catalogApi = check new (catalogApiEndpoint);

    // Query finished goods (MTART = 'FERT') from the MARA material master table.
    // OPTIONS and FIELDS are table parameters — sent as input to the RFC alongside
    // the import parameters QUERY_TABLE and DELIMITER.
    ReadTableResponse result = check sapClient->execute("RFC_READ_TABLE", {
        importParameters: {
            "QUERY_TABLE": "MARA",
            "DELIMITER": FIELD_DELIMITER
        },
        tableParameters: {
            "OPTIONS": [{"TEXT": "MTART EQ 'FERT'"}],
            "FIELDS": [
                {"FIELDNAME": "MATNR"},
                {"FIELDNAME": "MTART"},
                {"FIELDNAME": "MBRSH"},
                {"FIELDNAME": "MEINS"}
            ]
        }
    });

    // DATA is a table parameter returned by the RFC. It is merged alongside export
    // parameters into the flat ReadTableResponse record.
    ProductCatalogItem[] catalogItems = [];
    foreach TableDataRow row in result.DATA {
        string[] fields = re `\|`.split(row.WA.trim());
        if fields.length() == 4 {
            catalogItems.push({
                materialNumber: fields[0].trim(),
                materialType: fields[1].trim(),
                industrySector: fields[2].trim(),
                baseUnit: fields[3].trim()
            });
        }
    }

    http:Response _ = check catalogApi->post("/products/bulk", catalogItems);
    io:println("Synced ", catalogItems.length(), " finished goods to the product catalog.");
}

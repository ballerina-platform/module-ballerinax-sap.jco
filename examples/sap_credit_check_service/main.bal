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

configurable jco:DestinationConfig clientConfig = ?;
configurable jco:ServerConfig sapConfig = ?;
configurable string creditBureauApiEndpoint = ?;

// The client must be initialized before the listener so that the repositoryDestination
// is registered and available for RFC function module metadata lookups.
final jco:Client sapClient = check new (clientConfig, sapConfig.repositoryDestination);

listener jco:Listener creditCheckListener = new (sapConfig);

// RFC service that handles inbound Z_CHECK_CUSTOMER_CREDIT calls from SAP.
// SAP's ABAP code calls this function module synchronously during sales order creation
// to validate customer creditworthiness before confirming the order.
service jco:RfcService on creditCheckListener {

    // Called by SAP when the RFC function module registered on this server is invoked.
    // Import parameters received from SAP:
    //   CUSTOMER_ID  - SAP customer number (KUNNR)
    //   ORDER_AMOUNT - Requested order value in document currency
    //
    // Export parameters returned to SAP:
    //   CREDIT_STATUS - "A" (approved) or "R" (rejected)
    //   CREDIT_SCORE  - Numeric credit score from the bureau
    //   CREDIT_LIMIT  - Maximum credit amount the bureau will extend
    //   MESSAGE       - Human-readable decision summary
    remote function onCall(string functionName, jco:RfcParameters parameters) returns jco:RfcRecord|error {
        if functionName != "Z_CHECK_CUSTOMER_CREDIT" {
            return error("Unsupported function module: " + functionName);
        }
        return check evaluateCredit(parameters);
    }

    remote function onError(error err) returns error? {
        io:println("SAP gateway error: ", err.message());
    }
}

// Extracts import parameters, calls the credit bureau, and returns the RFC response record.
// Isolated into its own function so that `check` expressions have an unambiguous return type.
function evaluateCredit(jco:RfcParameters parameters) returns jco:RfcRecord|error {
    jco:RfcRecord importParams = parameters.importParameters ?: {};
    string customerId = (importParams["CUSTOMER_ID"] ?: "").toString();
    decimal orderAmount = check decimal:fromString((importParams["ORDER_AMOUNT"] ?: "0").toString());

    http:Client creditApi = check new (creditBureauApiEndpoint);
    CreditBureauResponse bureauResponse = check creditApi->get("/credit/" + customerId);

    boolean approved = bureauResponse.accountStatus == "ACTIVE"
        && bureauResponse.creditScore >= 600
        && bureauResponse.creditLimit >= orderAmount;

    string statusCode = approved ? "A" : "R";
    string message = approved
        ? string `Credit approved: score ${bureauResponse.creditScore}, limit ${bureauResponse.creditLimit}`
        : string `Credit rejected: score ${bureauResponse.creditScore}, limit ${bureauResponse.creditLimit}, status ${bureauResponse.accountStatus}`;

    io:println(string `Credit check for customer ${customerId}: ${message}`);

    return {
        "CREDIT_STATUS": statusCode,
        "CREDIT_SCORE": bureauResponse.creditScore,
        "CREDIT_LIMIT": bureauResponse.creditLimit,
        "MESSAGE": message
    };
}

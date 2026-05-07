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

// Response from the external credit bureau HTTP API.
type CreditBureauResponse record {|
    // FICO-style credit score (300–850)
    int creditScore;
    // Maximum credit amount the bureau will extend to this customer
    decimal creditLimit;
    // Bureau account status: "ACTIVE", "DELINQUENT", or "FROZEN"
    string accountStatus;
|};

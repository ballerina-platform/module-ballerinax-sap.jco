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

// Each row returned by RFC_READ_TABLE. The WA field contains pipe-delimited
// field values in the order specified by the FIELDS input table parameter.
type TableDataRow record {|
    string WA;
|};

// Response from RFC_READ_TABLE. DATA is a table parameter returned by SAP and
// is merged alongside export parameters into this flat response record.
type ReadTableResponse record {|
    TableDataRow[] DATA;
|};

// Represents a finished goods material to be synced to the product catalog API.
type ProductCatalogItem record {|
    string materialNumber;
    string materialType;
    string industrySector;
    string baseUnit;
|};

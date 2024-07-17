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

// Define the record for shipment data as fetched from the API
public type ShipmentData record {
    string orderId;
    string productCode;
    int quantity;
    string destination;
};

// Data structure to hold iDoc data
type EDI_DC40 record {
    @xmldata:Attribute
    string SEGMENT = "1";
    string TABNAM = "EDI_DC40";
    string MANDT = "800";
    string DOCNUM;
    string DOCREL = "700";
    string STATUS = "30";
    string DIRECT = "1";
    string OUTMOD = "2";
    string IDOCTYP = "DELVRY03";
    string MESTYP = "DESADV";
    string SNDPOR = "SAPR3";
    string SNDPRT = "LS";
    string SNDPRN = "YOUR_SAP";
    string RCVPOR = "SAPR3";
    string RCVPRT = "LS";
    string RCVPRN = "RECIPIENT_SAP";
};

type E1EDL20 record {
    @xmldata:Attribute
    string SEGMENT = "1";
    string VBELN;
    string NTGEW;
    string GEWEI = "KGM";
    string INCO1;
    string INCO2 = "01";
};

type IDOC record {
    @xmldata:Attribute
    string BEGIN = "1";
    EDI_DC40 EDI_DC40;
    E1EDL20 E1EDL20;
};

type DELVRY03 record {
    IDOC IDOC;
};


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

// Define the record for internal order processing
public type InternalOrder record {
    string partId;
    int quantity;
    string supplierId;
    string orderDate;
};

// Definition of EDI_DC40 record for handling control segment information
type EDI_DC40 record {
    string TABNAM = "EDI_DC40";
    string MANDT = "800";
    string DOCNUM = "0000001234567";
    string DOCREL = "700";
    string STATUS = "30";
    string DIRECT = "1";
    string OUTMOD = "2";
    string EXPRSS = "X";
    string TEST = "T";
    string IDOCTYP = "ORDERS05";
    string MESTYP = "ORDERS";
    string MESCOD = "01";
    string MESFCT = "04";
    string STD = "ISO";
    string STDVRS = "00401";
    string STDMES = "ORDERS";
    string SNDPOR = "SAPTRX";
    string SNDPRT = "LS";
    string SNDPRN = "AutoParts";
    string RCVPOR = "SAPAUTOPARTS";
    string RCVPRT = "LS";
    string RCVPRN = "SUPPLIER_A";
    string SEGMENT = "1";
};

type E1EDK01 record {
    string ACTION = "ADD";
    string DOCNUM = "0000001234567";
    string DOCDAT = "20240712";
    string DOCDES = "Supplier Order";
    string SEGMENT = "1";
};

type E1EDP19 record {
    string QUALF = "001";
    string IDTNR = "02938384";
    string SEGMENT = "1";
};

type E1EDP01 record {
    string POSEX = "00010";
    string MATNR = "02938384";
    string MENGE = "100"; // Quantity of the part ordered
    string MEINS = "EA"; // Unit of measure
    string PSTYP = "0"; // Item category
    string WERKS = "0010"; // Plant
    string LGORT = "0001"; // Storage location
    E1EDP19 E1EDP19; // Component for additional item identification
    string SEGMENT = "1";
};

type IDOC record {
    EDI_DC40 EDI_DC40;
    E1EDK01 E1EDK01;
    E1EDP01[] E1EDP01;
    string BEGIN = "1";
};

type ORDERS05 record {
    IDOC IDOC;
};


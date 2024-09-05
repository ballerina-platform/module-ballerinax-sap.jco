# Specification: Ballerina SAP JCo Connector

_Authors_: @RDPerera\
_Reviewers_: @daneshk @aashikam @shafreenAnfar\
_Created_: 2024/08/28\
_Updated_: 2024/09/02\
_Edition_: Swan Lake

## Introduction

This is the specification for the SAP JCo connector library of [Ballerina language](https://ballerina.io/), which is used for integrating Ballerina applications with SAP systems.

The SAP JCo connector specification has evolved and may continue to evolve in the future. The released versions of the specification can be found under the relevant GitHub tag.

If you have any feedback or suggestions about the connector, start a discussion via a GitHub issue or in the Discord server. Based on the outcome of the discussion, the specification and implementation can be updated. Community feedback is always welcome. Any accepted proposal, which affects the specification is stored under /docs/proposals. Proposals under discussion can be found with the label type/proposal in GitHub.

The conforming implementation of the specification is released and included in the distribution. Any deviation from the specification is considered a bug.

## Contents

1. [Overview](#1-overview)
2. [Components](#2-components)
   - 2.1 [RFC Client](#21-rfc-client)
     - 2.1.1 [Configurations](#211-configurations)
       - 2.1.1.1 [DestinationConfig](#2111-destinationconfig)
       - 2.1.1.2 [AdvancedConfig](#2112-advancedconfig)
     - 2.1.2 [Available Operations](#212-available-operations)
       - 2.1.2.1 [Execute a Function Module via RFC](#2121-execute-a-function-module-via-rfc)
       - 2.1.2.2 [Send IDocs to an SAP System](#2122-send-idocs-to-an-sap-system)
     - 2.1.3 [Data Types](#213-data-type-mapping-between-ballerina-and-sap-jco)
   - 2.2 [Listener](#22-listener)
     - 2.2.1 [Configurations](#221-configurations)
       - 2.2.1.1 [ServerConfig](#2211-serverconfig)
       - 2.2.1.2 [AdvancedConfig](#2212-advancedconfig)
     - 2.2.2 [Available Operations](#222-available-operations)
       - 2.2.2.1 [Receive IDocs](#2221-receive-idocs)
       - 2.2.2.2 [Error Handling in Listener](#2222-error-handling)

## 1. Overview

The Ballerina SAP JCo Connector provides seamless integration with SAP systems through Java Connector (JCo) capabilities. This connector allows Ballerina applications to interact with SAP systems, enabling operations like Remote Function Call (RFC) execution, IDoc processing, and more. By leveraging this connector, developers can easily integrate SAP functionalities into their Ballerina applications, making it an essential tool for enterprises working with SAP.

The `ballerinax/sap.jco` package exposes the SAP JCo library as Ballerina functions, enabling easy access to SAP's enterprise resource planning (ERP) software and other SAP solutions, such as human capital management (HCM), customer relationship management (CRM), and supply chain management (SCM).

### Architecture Overview

The SAP JCo Connector architecture comprises key components facilitating communication between Ballerina applications and SAP systems. These components include the SAP function modules, the SAP system itself, the Ballerina code that handles the RFC calls, and the IDoc servers responsible for sending and receiving IDocs. This architecture enables robust integration, supporting various enterprise operations.

## 2. Components

This section outlines the core components of the Ballerina SAP JCo Connector and their configurations, focusing on how they interact with the SAP system.

### 2.1 RFC Client

The RFC Client component allows the Ballerina application to communicate with an SAP system by invoking remote function modules and sending IDocs.

#### 2.1.1 Configurations

The RFC Client supports two types of configurations, `DestinationConfig` and `AdvancedConfig`, to establish a connection with the SAP system.

##### 2.1.1.1 DestinationConfig

The `DestinationConfig` type represents the configuration details needed to create a RFC connection.

```ballerina
public type DestinationConfig record {]
    # The SAP hostname (jco.client.ashost)
    string ashost;
    # The SAP system number (jco.client.sysnr)
    string sysnr;
    # The SAP client (jco.client.client)
    string jcoClient;
    # The SAP user name (jco.client.user)
    string user;
    # The SAP password (jco.client.passwd)
    string passwd;
    # The SAP language (jco.client.lang)
    string lang = "EN";
    # The SAP group (jco.client.group)
    string group = "PUBLIC";
};
```

**Example Configuration:**

```ballerina
configurable jco:DestinationConfig config = ?;
jco:Client sapClient = check new (config);
```

Config.toml file:

```toml
[config]
ashost = "host"
sysnr = "00"
jcoClient = "000"
user = "user"
passwd = "password"
lang = "en"
group = "group"
```

##### 2.1.1.2 AdvancedConfig

If the user wants to specify more detailed configurations beyond `DestinationConfig`, they can use `AdvancedConfig`. The `AdvancedConfig` type is a map that holds any SAP JCo configurations accepted by the SAP destination provider. 

```ballerina
public type AdvancedConfig map<string>;
```

**Example Configuration:**

```ballerina
configurable jco:AdvancedConfig config = ?;
jco:Client sapClient = check new (config);
```

Config.toml file:

```toml
[config]
"jco.destination.pool_capacity" = "3"
"jco.destination.peak_limit" = "10"
```

#### 2.1.2 Available Operations

##### 2.1.2.1 Execute a Function Module via RFC

An RFC function call is made using the following function;

```ballerina
# Executes the RFC function.
#
# + functionName - The name of the function to be executed.
# + importParams - The input parameters for the function.
# + exportParams - The output parameters for the function.
# + return - An error if the execution fails.
remote function execute(string functionName, record {|FieldType?...;|} importParams, typedesc<record {|FieldType?...;|}|xml|json?> exportParams = <>) returns exportParams|Error
```

The input parameters require functionName and importParams. The functionName refers to the Remote Function Module name and importParams accepts a FieldType value, which can be;

```ballerina
public type FieldType string|int|float|decimal|time:Date|time:TimeOfDay|byte[]|record {|FieldType?...;|}|record {|FieldType?...;|}[];
```

For the export parameters, the type descriptor of the user's return type is extracted. It should be a closed record of `FieldType` as the rest fields or a closed record with the exact output parameter names from the SAP system. This initial record provided by the user will be considered the export parameter list, and within this closed record, the user can include nested records for `SAP structures` and record arrays for `SAP tables`.

Users can invoke it as shown below:

```ballerina
public function main() returns error? {
    ImportParams importParams = {
        importParam1: "Hello",
        importParam2: 123,
        importParam3: 123.45,
        importParam4: 123.456
    };

    ExportParams? result = check jcoClient->execute("TEST_FUNCTION", importParams);
    if result is jco:ExportParams {
        io:println("Result: ", result);
    } else {
        io:println("Error: Function execution failed");
    }
}
```

##### 2.1.2.2 Send IDocs to an SAP System

The RFC Client also supports sending IDocs to an SAP system, allowing you to automate the exchange of structured data.

An IDoc can be sent using the following function:

```ballerina
# Send the iDoc.
#
# + iDoc - The XML string of the iDoc.
# + iDocType - The type of the iDoc.
# + return - An error if the execution fails.
remote function sendIDoc(xml iDoc, IDocType iDocType = jco:DEFAULT) returns Error
```

The input parameters require `iDoc`, with an optional `iDocType`. The `iDoc` represents the content of the IDoc in XML format. The `iDocType` specifies the version of the IDoc being sent and can be set to `DEFAULT`, `VERSION_2`, `VERSION_3`, `VERSION_3_IN_QUEUE`, or `VERSION_3_IN_QUEUE_VIA_QRFC`. If no `iDocType` is provided, the `DEFAULT` type will be applied.

Users can invoke it as shown below:

```ballerina
public function main() returns error? {
    xml iDoc = check io:fileReadXml("resources/sample.xml");
    check jcoClient->sendIDoc(iDoc);
    io:println("IDoc sent successfully.");
}
```

#### 2.1.3 Data Type Mapping Between Ballerina and SAP JCo

The following table maps Ballerina data types to their corresponding SAP JCo types:

| Ballerina Type                        | SAP JCo Type   | SAP Data Type         |
| ------------------------------------- | -------------- | --------------------- |
| string                                | char           | CHAR                  |
| string                                | char[]         | STRING, SSTRING       |
| time:Date                             | Date           | DATE                  |
| time:TimeOfDay                        | Time           | TIME                  |
| int                                   | short          | INT1                  |
| int                                   | int            | INT2                  |
| int                                   | long           | INT4                  |
| float                                 | float          | FLOAT                 |
| decimal                               | double         | DEC, QUAN, CURR, PREC |
| byte                                  | byte           | INT1                  |
| byte[]                                | byte[]         | RAW, LRAW, BYTE       |
| decimal                               | BigDecimal     | DEC, QUAN, CURR       |
| int                                   | BigInteger     | NUM                   |
| record (with/without rest fields)     | JCoStructure   | STRUCTURE             |
| record[] (with/without rest fields)   | JCoTable       | TABLE                 |

### 2.2 Listener

#### 2.2.1 Configurations

The Listener supports two types of configurations, `ServerConfig` and `AdvancedConfig`, to facilitate communication with the SAP system by receiving IDocs and handling events.

##### 2.2.1.1 ServerConfig

The `ServerConfig` type represents the configuration details needed to create an IDoc connection.

```ballerina
public type ServerConfig record {
    # The gateway host (jco.server.gwhost)
    string gwhost;
    # The gateway service (jco.server.gwserv)
    string gwserv;
    # The program ID (jco.server.progid)
    string progid;
};
```

**Example Configuration:**

```ballerina
configurable jco:ServerConfig configs = ?;
listener jco:Listener idocListener = new (configs);
```

Config.toml file:

```toml
[configs]
gwhost = "gwhost"
gwserv = "sapgw00"
progid = "progid"
```

##### 2.2.1.2 AdvancedConfig

If the user needs to specify more detailed configurations beyond `ServerConfig`—for example, to register a destination with the listener—they can use `AdvancedConfig`. The `AdvancedConfig` type is a map that holds any SAP JCo configurations accepted by the SAP server provider.

```ballerina
public type AdvancedConfig map<string>;
```

**Example Configuration:**

```ballerina
configurable jco:AdvancedConfig configs = ?;
listener jco:Listener idocListener = new (configs);
```

Config.toml file:

```toml
[configs]
"jco.server.gwhost" = "gwhost"
"jco.server.gwserv" = "sapgw00"
"jco.server.repository_destination" = "SAMPLE_DESTINATION"
"jco.client.ashost" = "host"
"jco.client.sysnr" = "00"
"jco.client.client" = "000"
"jco.client.user" = "user"
"jco.client.passwd" = "password"
```

#### 2.2.2 Available Operations

##### 2.2.2.1 Receive IDocs

The Listener can be used to receive IDocs from the SAP system, which can then be processed within your Ballerina application.

```ballerina
listener jco:Listener iDocListener = new (configurations);

service jco:Service on iDocListener {
    remote function onReceive(xml iDoc) returns error? {
        check io:fileWriteXml("resources/received_idoc.xml", iDoc);
        io:println("IDoc received and saved.");
    }
}
```

When an IDoc is received, it will be in XML format, and the user can easily map it to a record using Ballerina's XML-to-Record conversion features.

##### 2.2.2.2 Error Handling

 Listener

The Listener also supports error handling, allowing you to capture and manage any issues that occur during IDoc reception.

```ballerina
service jco:Service on iDocListener {
    remote function onReceive(xml iDoc) returns error? {
        check io:fileWriteXml("resources/received_idoc.xml", iDoc);
        io:println("IDoc received and saved.");
    }

    remote function onError(error 'error) returns error? {
        io:println("Error occurred: ", 'error.message());
    }
}
```

# Specification: Ballerina SAP JCo Connector

_Authors_: RDPerera\
_Reviewers_: Niveathika\
_Created_: 2024/08/28\
_Updated_: 2024/09/02\
_Edition_: Swan Lake

## Introduction

The Ballerina SAP JCo Connector provides seamless integration with SAP systems through Java Connector (JCo) capabilities. This connector allows Ballerina applications to interact with SAP systems, enabling operations like Remote Function Call (RFC) execution, IDoc processing, and more. By leveraging this connector, developers can easily integrate SAP functionalities into their Ballerina applications, making it an essential tool for enterprises working with SAP.

The `ballerinax/sap.jco` package exposes the SAP JCo library as Ballerina functions, enabling easy access to SAP's enterprise resource planning (ERP) software and other SAP solutions, such as human capital management (HCM), customer relationship management (CRM), and supply chain management (SCM).

The Ballerina SAP JCo connector specification is continuously evolving, reflecting ongoing improvements and feedback. Released versions of the specification are maintained under the relevant GitHub tags.

Community contributions are highly valued. If you have feedback or suggestions, please start a discussion via a GitHub issue or in the Ballerina Discord server. Any accepted proposals affecting the specification will be documented in the `/docs/proposals` directory, with active discussions labeled as `type/proposal` on GitHub.

The conforming implementation of the specification is included in the Ballerina distribution. Any deviations from the specification are considered bugs.

## Contents

1. [Introduction](#1-introduction)
2. [Components](#2-components)
   - 2.1 [RFC Client](#21-rfc-client)
     - 2.1.1 [Configurations](#211-configurations)
     - 2.1.2 [Available Operations](#212-available-operations)
       - 2.1.2.1 [Execute a Function Module via RFC](#2121-execute-a-function-module-via-rfc)
       - 2.1.2.2 [Send IDocs to an SAP System](#2122-send-idocs-to-an-sap-system)
     - 2.1.3 [Data Types](#213-data-types)
   - 2.2 [Listener](#22-listener)
     - 2.2.1 [Configurations](#221-configurations)
     - 2.2.2 [Available Operations](#222-available-operations)
       - 2.2.2.1 [Receive IDocs](#2221-receive-idocs)
       - 2.2.2.2 [Error Handling in Listener](#2222-error-handling-in-listener)

## 1. Introduction

The Ballerina SAP JCo Connector provides seamless integration with SAP systems through Java Connector (JCo) capabilities. This connector allows Ballerina applications to interact with SAP systems, enabling operations like Remote Function Call (RFC) execution, IDoc processing, and more. By leveraging this connector, developers can easily integrate SAP functionalities into their Ballerina applications, making it an essential tool for enterprises working with SAP.

The `ballerinax/sap.jco` package exposes the SAP JCo library as Ballerina functions, enabling easy access to SAP's enterprise resource planning (ERP) software and other SAP solutions, such as human capital management (HCM), customer relationship management (CRM), and supply chain management (SCM).

### Architecture Overview

The SAP JCo Connector architecture consists of key components that facilitate communication between Ballerina applications and SAP systems. These components include the SAP function modules, the SAP system itself, Ballerina code that handles the RFC calls, and the IDoc servers responsible for sending and receiving IDocs. This architecture enables robust integration, supporting various enterprise operations.

## 2. Components

### 2.1 RFC Client

#### 2.1.1 Configurations

##### 2.1.1.1 DestinationConfig

The `DestinationConfig` type holds the configuration details needed to create a BAPI connection.

- **ashost**: The SAP host name (`jco.client.ashost`).
- **sysnr**: The SAP system number (`jco.client.sysnr`).
- **jcoClient**: The SAP client (`jco.client.client`).
- **user**: The SAP user name (`jco.client.user`).
- **passwd**: The SAP password (`jco.client.passwd`).
- **lang**: The SAP language (`jco.client.lang`). Default is "EN".
- **group**: The SAP group (`jco.client.group`). Default is "PUBLIC".

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

The `AdvancedConfig` type is a map that holds any custom configurations needed to create a SAP connection.

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

You can execute a specific function module on the SAP system using the `execute` function. This operation requires you to specify the function module name and the necessary parameters.

```ballerina
public function main() returns error? {
    ImportParams importParams = {
        importParam1: "Hello",
        importParam2: 123,
        importParam3: 123.45,
        importParam4: 123.456
    };

    ExportParams? result = check jcoClient->execute("TEST_FUNCTION", importParams);
    if (result is jco:ExportParams) {
        io:println("Result: ", result);
    } else {
        io:println("Error: Function execution failed");
    }
}
```

##### 2.1.2.2 Send IDocs to an SAP System

The RFC Client also supports sending IDocs to an SAP system, allowing you to automate the exchange of structured data.

```ballerina
public function main() returns error? {
    xml iDoc = check io:fileReadXml("resources/sample.xml");
    check jcoClient->sendIDoc(iDoc);
    io:println("IDoc sent successfully.");
}
```

#### 2.1.3 Data Type Mapping Between Ballerina and SAP JCo

##### 2.1.3.6 Ballerina to SAP JCo Mapping

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

##### 2.2.1.1 ServerConfig

The `ServerConfig` type holds the configuration details needed to create an IDoc connection.

- **gwhost**: The gateway host (`jco.server.gwhost`).
- **gwserv**: The gateway service (`jco.server.gwserv`).
- **progid**: The program ID (`jco.server.progid`).

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

The `AdvancedConfig` type is a map that holds any custom configurations needed to create a SAP connection.

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

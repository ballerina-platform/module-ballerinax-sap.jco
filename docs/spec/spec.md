# Specification: Ballerina SAP JCo Connector

_Authors_: @RDPerera  
_Reviewers_: @niveathika, @NipunaRanasinghe, @shafreenAnfar  
_Created_: 2024/08/28  
_Updated_: 2026/04/07  
_Edition_: Swan Lake

## Introduction

This is the specification for the SAP JCo connector library of [Ballerina language](https://ballerina.io/), which is used for integrating Ballerina applications with SAP systems.

The SAP JCo connector specification has evolved and may continue to evolve in the future. The released versions of the specification can be found under the relevant GitHub tag.

If you have any feedback or suggestions about the connector, start a discussion via a GitHub issue or in the Discord server. Based on the outcome of the discussion, the specification and implementation can be updated. Community feedback is always welcome. Any accepted proposal, which affects the specification is stored under `/docs/proposals`. Proposals under discussion can be found with the label `type/proposal` in GitHub.

The conforming implementation of the specification is released and included in the distribution. Any deviation from the specification is considered a bug.

## Contents

1. [Overview](#1-overview)
2. [Components](#2-components)
   - 2.1 [RFC client](#21-rfc-client)
     - 2.1.1 [Configurations](#211-configurations)
       - 2.1.1.1 [Destination configurations](#2111-destination-configurations)
       - 2.1.1.2 [Advanced configurations](#2112-advanced-configurations)
     - 2.1.2 [Available operations](#212-available-operations)
       - 2.1.2.1 [Execute a function module via RFC](#2121-execute-a-function-module-via-rfc)
       - 2.1.2.2 [Close the client](#2122-close-the-client)
       - 2.1.2.3 [Send iDocs to an SAP system](#2123-send-idocs-to-an-sap-system)
     - 2.1.3 [Data types](#213-data-type-mapping-between-ballerina-and-sap-jco)
   - 2.2 [Listener](#22-listener)
     - 2.2.1 [Configurations](#221-configurations)
       - 2.2.1.1 [Server configurations](#2211-server-configurations)
       - 2.2.1.2 [Advanced configurations](#2212-advanced-configurations)
     - 2.2.2 [Listener lifecycle](#222-listener-lifecycle)
     - 2.2.3 [Available operations](#223-available-operations)
       - 2.2.3.1 [Receive iDocs](#2231-receive-idocs)
       - 2.2.3.2 [Handle inbound RFC calls](#2232-handle-inbound-rfc-calls)
       - 2.2.3.3 [Error handling in listener](#2233-error-handling-in-listener)
3. [Error types](#3-error-types)
         
## 1. Overview

The Ballerina SAP JCo Connector provides seamless integration with SAP systems through Java Connector (JCo) capabilities. This connector allows Ballerina applications to interact with SAP systems, enabling operations like Remote Function Call (RFC) execution, IDoc processing, and more. By leveraging this connector, developers can easily integrate SAP functionalities into their Ballerina applications, making it an essential tool for enterprises working with SAP.

The `ballerinax/sap.jco` package exposes the SAP JCo library as Ballerina functions, enabling easy access to SAP's enterprise resource planning (ERP) software and other SAP solutions, such as human capital management (HCM), customer relationship management (CRM), and supply chain management (SCM).

### Architecture overview

The SAP JCo Connector architecture comprises key components facilitating communication between Ballerina applications and SAP systems. These components include the SAP function modules, the SAP system itself, the Ballerina code that handles the RFC calls, and the IDoc servers responsible for sending and receiving IDocs. This architecture enables robust integration, supporting various enterprise operations.

## 2. Components

This section outlines the core components of the Ballerina SAP JCo Connector and their configurations, focusing on how they interact with the SAP system.

### 2.1 RFC client

The RFC Client component allows the Ballerina application to communicate with an SAP system by invoking remote function modules and sending IDocs.

#### 2.1.1 Configurations

The RFC Client supports two types of configurations, `DestinationConfig` and `AdvancedConfig`, to establish a connection with the SAP system.

##### 2.1.1.1 Destination configurations

The `DestinationConfig` type represents the configuration details needed to create a RFC connection.

```ballerina
public type DestinationConfig record {|
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
|};
```

**Example configuration:**

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

When a `Listener` references this client as its `repositoryDestination`, provide an explicit `destinationId` so the listener can resolve it by name:

```ballerina
configurable jco:DestinationConfig config = ?;
jco:Client sapClient = check new (config, destinationId = "MY_DESTINATION");
```

##### 2.1.1.2 Advanced configurations

If the user wants to specify more detailed configurations beyond `DestinationConfig`, they can use `AdvancedConfig`. The `AdvancedConfig` type is a map that holds any SAP JCo configurations accepted by the SAP destination provider. 

```ballerina
public type AdvancedConfig map<string>;
```

**Example configuration:**

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

#### 2.1.2 Available operations

##### 2.1.2.1 Execute a function module via RFC

An RFC function call is made using the following function:

```ballerina
# Calls an RFC-enabled function module on the SAP system and returns the export and table parameters.
#
# + functionName - Name of the RFC function module to call (e.g. `"STFC_CONNECTION"`).
# + parameters - Import and table parameter values wrapped in an `RfcParameters` record.
# + returnType - Expected type of the RFC response (`xml` or a record type).
# + return - The export and table parameters merged and converted to the `returnType` type, or an error on failure.
isolated remote function execute(string functionName, RfcParameters parameters = {}, typedesc<RfcRecord|xml> returnType = <>) returns returnType|Error
```

The `RfcParameters` type wraps import parameters and optional table parameters:

```ballerina
public type RfcParameters record {|
    RfcRecord importParameters?;
    map<RfcRecord[]> tableParameters?;
|};
```

`RfcRecord` is a type alias for an open record of `FieldType` values:

```ballerina
public type RfcRecord record {| FieldType?...; |};
```

`FieldType` covers all scalar, temporal, binary, structure, and table value types supported by SAP JCo:

```ballerina
public type FieldType string|int|float|decimal|boolean|time:Date|time:TimeOfDay|byte[]|record {|FieldType?...;|}|record {|FieldType?...;|}[];
```

The `functionName` refers to the Remote Function Module name. The `parameters.importParameters` map holds scalar and structure values keyed by SAP parameter name. The `parameters.tableParameters` map holds table input parameters, where each key is a SAP table parameter name and the value is an array of `RfcRecord` rows.

For the return type, the type descriptor of the user's `returnType` is extracted. The response merges both export parameters and table parameters returned by the SAP function module. Use a `RfcRecord`-compatible record to receive typed results, or `xml` for untyped access.

Users can invoke it as shown below:

```ballerina
public function main() returns error? {
    ExportParams result = check jcoClient->execute("TEST_FUNCTION", {
        importParameters: {
            importParam1: "Hello",
            importParam2: 123,
            importParam3: 123.45,
            importParam4: 123.456
        }
    });
    io:println("Result: ", result);
}
```

To pass table parameters (SAP table inputs):

```ballerina
public function main() returns error? {
    ExportParams result = check jcoClient->execute("TABLE_FUNC", {
        importParameters: {PARAM1: "val"},
        tableParameters: {"TABLE_PARAM": [{COL1: "row1"}, {COL1: "row2"}]}
    });
    io:println("Result: ", result);
}
```

##### 2.1.2.2 Close the client

When a client is no longer needed, its JCo destination registration should be released by calling `close`:

```ballerina
# Releases the JCo destination registered for this client. After calling `close`, further
# `execute` or `sendIDoc` calls will fail with a `ConfigurationError`. Call this when the
# client is no longer needed to free the destination ID for reuse. Calling `close` more
# than once is safe and has no effect after the first call.
#
# + return - A `ConfigurationError` if the JCo destination could not be fully released,
#            otherwise `()`. The client is marked closed regardless of the outcome.
public isolated function close() returns Error?
```

Any call to `execute` or `sendIDoc` after `close` returns a `ConfigurationError` immediately without contacting SAP. Calling `close` more than once is safe and has no effect after the first call.

##### 2.1.2.3 Send iDocs to an SAP system

The RFC Client also supports sending IDocs to an SAP system, allowing you to automate the exchange of structured data.

An IDoc can be sent using the following function:

```ballerina
# Sends an IDoc to the SAP system over tRFC or qRFC, including TID creation and confirmation.
#
# + iDoc - IDoc payload in XML format
# + iDocType - IDoc protocol version. Use `VERSION_3_IN_QUEUE` or `VERSION_3_IN_QUEUE_VIA_QRFC` for qRFC.
# + tid - Optional Transaction ID (TID). If not provided, a new TID is created via the JCo destination.
#         Supply your own TID for end-to-end idempotency when the caller persists outbound intent.
# + queueName - Required when `iDocType` is a qRFC version (`VERSION_3_IN_QUEUE` or
#               `VERSION_3_IN_QUEUE_VIA_QRFC`). Ignored with a warning for tRFC versions.
# + return - An error if the IDoc cannot be delivered or the TID cannot be confirmed
isolated remote function sendIDoc(xml iDoc, IDocType iDocType = DEFAULT,
                                  string? tid = (), string? queueName = ()) returns Error?
```

The required parameter is `iDoc` (the IDoc payload in XML format). Optional parameters:

- `iDocType` — the IDoc version/protocol variant. `DEFAULT`, `VERSION_2`, and `VERSION_3` use **tRFC** (unordered, exactly-once). `VERSION_3_IN_QUEUE` and `VERSION_3_IN_QUEUE_VIA_QRFC` use **qRFC** (ordered delivery via a named queue).
- `tid` — a caller-supplied Transaction ID for end-to-end idempotency. If omitted, the JCo destination generates one automatically. Useful when the caller persists the outbound intent (outbox pattern) and needs SAP to recognise the TID as already processed on retry.
- `queueName` — the SAP inbound queue name. Required for qRFC `iDocType` values; ignored (with a warning) for tRFC values.

Users can invoke it as shown below:

```ballerina
public function main() returns error? {
    xml iDoc = check io:fileReadXml("resources/sample.xml");

    // tRFC send (unordered, auto-generated TID)
    check jcoClient->sendIDoc(iDoc);

    // qRFC send (ordered delivery into a named queue)
    check jcoClient->sendIDoc(iDoc, iDocType = VERSION_3_IN_QUEUE_VIA_QRFC,
                              queueName = "MATMAS_SENDER_CLNT100");

    io:println("IDocs sent successfully.");
}
```

#### 2.1.3 Data type mapping between Ballerina and SAP JCo

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
| boolean                               | Boolean        | CHAR (X/space)        |
| byte                                  | byte           | INT1                  |
| byte[]                                | byte[]         | RAW, LRAW, BYTE       |
| decimal                               | BigDecimal     | DEC, QUAN, CURR       |
| int                                   | BigInteger     | NUM                   |
| record (with/without rest fields)     | JCoStructure   | STRUCTURE             |
| record[] (with/without rest fields)   | JCoTable       | TABLE                 |

### 2.2 Listener

#### 2.2.1 Configurations

The Listener supports two types of configurations, `ServerConfig` and `AdvancedConfig`, to facilitate communication with the SAP system by receiving IDocs and handling events.

##### 2.2.1.1 Server configurations

The `ServerConfig` type represents the configuration details needed to create an IDoc connection.

```ballerina
public type RepositoryDestination string|DestinationConfig;

public type ServerConfig record {|
    # The gateway host (jco.server.gwhost)
    string gwhost;
    # The gateway service (jco.server.gwserv)
    string gwserv;
    # The program ID (jco.server.progid)
    string progid;
    # Maximum number of concurrent RFC connections (jco.server.connection_count)
    int connectionCount = 2;
    # RFC destination used to look up IDoc and RFC metadata (jco.server.repository_destination)
    RepositoryDestination repositoryDestination;
|};
```

`repositoryDestination` is required and accepts two forms:

- **String** — the `destinationId` of an already-initialised `Client`. The listener reuses that client's connection to look up IDoc segment metadata and RFC function module metadata from SAP.
- **`DestinationConfig`** — SAP credentials supplied directly. The listener registers an internal JCo destination automatically, so no separate `Client` is required.

**Example configuration (string form):**

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
connectionCount = 2
repositoryDestination = "MY_DESTINATION"
```

**Example configuration (inline DestinationConfig form):**

```toml
[configs]
gwhost = "gwhost"
gwserv = "sapgw00"
progid = "progid"
connectionCount = 2

[configs.repositoryDestination]
ashost = "sap.example.com"
sysnr = "00"
jcoClient = "100"
user = "SAP_USER"
passwd = "SAP_PASSWORD"
```

##### 2.2.1.2 Advanced configurations

If the user needs to specify more detailed configurations beyond `ServerConfig`—for example, to register a destination with the listener—they can use `AdvancedConfig`. The `AdvancedConfig` type is a map that holds any SAP JCo configurations accepted by the SAP server provider.

```ballerina
public type AdvancedConfig map<string>;
```

**Example configuration:**

```ballerina
configurable jco:AdvancedConfig configs = ?;
listener jco:Listener idocListener = new (configs);
```

Config.toml file:

```toml
[configs]
"jco.server.gwhost" = "gwhost"
"jco.server.gwserv" = "sapgw00"
"jco.server.progid" = "progID"
"jco.server.repository_destination" = "SAMPLE_DESTINATION"
"jco.client.ashost" = "host"
"jco.client.sysnr" = "00"
"jco.client.client" = "000"
"jco.client.user" = "user"
"jco.client.passwd" = "password"
```

#### 2.2.2 Listener lifecycle

This section describes how to initialise, attach, start, and stop the listener.

- To initialise the listener with a given configuration, the `init` function can be used.

```ballerina
# Registers a JCo server with the SAP gateway; reuses an existing server for the same (gwhost, gwserv, progid) combination.
#
# + serverConfig - JCo server connection parameters (`ServerConfig` or `AdvancedConfig`).
# + serverName - Unique name used to register the server with the JCo framework (default is a UUID).
# + return - An error if the server cannot be registered.
public isolated function init(ServerConfig|AdvancedConfig serverConfig, string serverName = uuid:createType4AsString()) returns Error?
```

- To attach a service to the listener, the `attach` function can be used.

```ballerina
# Attaches a service to the listener. At most one IDocService and one RfcService may be
# attached at the same time. Both service types require repositoryDestination to be set in
# ServerConfig. When repositoryDestination is a string destinationId, a Client with that
# destinationId must have been created first. When repositoryDestination is an inline
# DestinationConfig, no separate Client creation is required.
#
# + s - The service to attach; must be an IDocService or an RfcService.
# + name - Optional service name (unused at runtime).
# + return - An error if the repositoryDestination is not registered, the service type is already attached, or attachment fails.
public isolated function attach(IDocService|RfcService s, string[]|string? name = ()) returns Error?
```

- To detach a service from the listener, the `detach` function can be used.

```ballerina
# Unregisters a service from the listener without stopping the JCo server.
# The other service type, if attached, continues to operate.
#
# + s - The service to detach.
# + return - An error if the detach operation fails.
public isolated function detach(IDocService|RfcService s) returns Error?
```

- To start the listener, the `'start` function can be used.

```ballerina
# Starts the JCo server and returns immediately. Gateway connectivity is established
# asynchronously by JCo's internal connection threads. A successful return means the server
# has been submitted to JCo's scheduler — it does not mean the gateway handshake is complete.
#
# If the gateway is unreachable, JCo retries automatically and delivers each failure to the
# attached service's `onError` handler as an `ExecutionError`.
#
# + return - An error only for pre-flight failures: listener not initialised or already started.
public isolated function 'start() returns Error?
```

- To stop the listener gracefully, the `gracefulStop` function can be used.

```ballerina
# Stops the JCo server and blocks until it fully leaves the stopping state (up to 15 seconds).
#
# + return - An error if the server cannot be stopped.
public isolated function gracefulStop() returns Error?
```

- To stop the listener immediately, the `immediateStop` function can be used.

```ballerina
# Stops the JCo server. Behaves identically to gracefulStop because JCo exposes a single
# stop operation with no immediate/graceful distinction.
#
# + return - An error if the server cannot be stopped.
public isolated function immediateStop() returns Error?
```

#### 2.2.3 Available operations

##### 2.2.3.1 Receive iDocs

The Listener can be used to receive IDocs from the SAP system using the `IDocService` type.

```ballerina
listener jco:Listener iDocListener = new (configurations);

service jco:IDocService on iDocListener {
    remote function onReceive(xml iDoc) returns error? {
        check io:fileWriteXml("resources/received_idoc.xml", iDoc);
        io:println("IDoc received and saved.");
    }
    remote function onError(error err) returns error? {
        io:println("Error occurred: ", err.message());
    }
}
```

When an IDoc is received, it will be in XML format, and the user can easily map it to a record using Ballerina's XML-to-Record conversion features.

##### 2.2.3.2 Handle inbound RFC calls

The Listener can also receive inbound RFC calls from the SAP system using the `RfcService` type. SAP invokes this service as if it were a registered RFC function module.

```ballerina
service jco:RfcService on rfcListener {
    remote function onCall(string functionName, jco:RfcParameters parameters) returns jco:RfcRecord|xml|error? {
        io:println("RFC called: ", functionName);
        return ();
    }
    remote function onError(error err) returns error? {
        io:println("Error occurred: ", err.message());
    }
}
```

The return value of `onCall` is serialized and sent back to the SAP caller as the RFC response. An error return causes an `AbapException` to be raised on the SAP side. An empty (`()`) response is valid for fire-and-forget RFCs.

##### 2.2.3.3 Error handling in listener

Both `IDocService` and `RfcService` expose an `onError` remote function that receives server-level errors, including gateway connectivity failures. Because the listener starts before the gateway handshake completes, this handler is the primary signal for connectivity problems. JCo retries the connection automatically and calls this method on every failed attempt. When the gateway becomes reachable again, JCo reconnects silently and the errors stop — there is no need to restart the listener.

## 3. Error types

The connector defines distinct error types aligned with Ballerina conventions. All error types are members of the `Error` union.

```ballerina
public type Error ConnectionError|LogonError|ResourceError|SystemError|AbapApplicationError
    |JCoError|IDocError|ParameterError|ConfigurationError|ExecutionError;
```

| Error type               | Description                                                                                   |
| ------------------------ | --------------------------------------------------------------------------------------------- |
| `ConnectionError`        | Raised when the JCo client cannot reach the SAP gateway (network/communication failure).      |
| `LogonError`             | Raised when the SAP system rejects the supplied logon credentials.                            |
| `ResourceError`          | Raised when JCo cannot allocate a required resource (e.g. connection pool exhausted).         |
| `SystemError`            | Raised when the SAP system reports an internal system failure.                                |
| `AbapApplicationError`   | Raised when an ABAP function module throws a class-based or classic exception.                |
| `JCoError`               | Raised for all other JCo-level errors not covered by a more specific type.                    |
| `IDocError`              | Raised for IDoc-specific failures (XML parsing, send, or server-side processing).             |
| `ParameterError`         | Raised when an RFC import or export parameter cannot be converted to or from a Ballerina type.|
| `ConfigurationError`     | Raised during client or listener initialisation and lifecycle management, including calls to `execute` or `sendIDoc` after `close`.|
| `ExecutionError`         | Raised when an unexpected error occurs during RFC execution or other runtime operations.                                           |

Most JCo-origin errors carry a `JCoErrorDetail` record with an `errorGroup` integer and an optional `key` string. ABAP application errors additionally carry ABAP message class, type, number, and up to four message variables via `AbapApplicationErrorDetail`.

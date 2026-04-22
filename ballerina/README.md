## Overview

[SAP](https://www.sap.com/india/index.html) is a global leader in enterprise resource planning (ERP) software. Beyond
ERP, SAP offers a diverse range of solutions including human capital management (HCM), customer relationship
management (CRM), enterprise performance management (EPM), product lifecycle management (PLM), supplier relationship
management (SRM), supply chain management (SCM), business technology platform (BTP), and the SAP AppGyver programming
environment for businesses.

The `ballerinax/sap.jco` package exposes the SAP JCo library as ballerina functions.

## Key Features

- Connect to SAP systems via SAP JCo (Java Connector)
- Execute BAPIs and Remote Function Calls (RFC)
- Support for IDoc processing and exchange — both sending and receiving
- Receive and handle inbound RFC calls from SAP systems using the `RfcService` listener type
- Distinct typed error hierarchy (`ConnectionError`, `LogonError`, `ResourceError`, `SystemError`, `AbapApplicationError`, `IDocError`, `ConfigurationError`, `ExecutionError`, and more) for precise error handling
- Singleton destination and server data providers enabling multiple concurrent clients and listeners without JCo provider conflicts
- Compatible with SAP ERP and S/4HANA systems

## Setup Guide

### Obtain SAP Connection Parameters

To connect to an SAP system, you need to obtain the following connection parameters from your SAP administrator. These
are some common
parameters required to establish a client:

- **Host**: The hostname or IP address of the SAP server.
- **System Number**: Identifies the system within the landscape.
- **Client**: Specifies the client ID of the SAP system, which is used for login.
- **User Name**: Your SAP user account name.
- **Password**: Your account password.

There may be additional parameters required based on your organization's SAP configuration. Consult your SAP
administrator for the complete list of parameters needed for your setup.

### Download and Add Middleware Libraries

Download SAP JCo JARs and native libraries from the [SAP Service Marketplace](https://support.sap.com/en/index.html). You need both the `sapjco3.jar` and the platform-specific native library (`sapjco3.dll` on Windows, `libsapjco3.so` on Linux, `libsapjco3.jnilib` on Mac). Add the relevant paths in the **Ballerina.toml** with `provided` scope so they're on the compile-time classpath but not bundled into the final artifact.

```toml
[[platform.java21.dependency]]
path = "<path-to-sapidoc3.jar>"
groupId = "com.sap"
artifactId = "com.sap.conn.idoc"
version = "3.1.*"
scope = "provided"

[[platform.java21.dependency]]
path = "<path-to-sapjco3.jar>"
groupId = "com.sap"
artifactId = "com.sap.conn.jco"
version = "3.1.*"
scope = "provided"
```

The native library needs to be on the system `PATH` (Windows) or `LD_LIBRARY_PATH` (Linux) or `DYLD_LIBRARY_PATH` (Mac) at runtime so the JVM can find it.

### Configure Minimum Version (Optional)

Configure the required minimum version of the SAP JCo connector in your **Ballerina.toml** to avoid accidentally using an incompatible version of JCo:

```toml
[[dependency]]
org = "ballerinax"
name = "sap.jco"
version = "2.0.0"
```

## Quickstart

To use the SAP JCo connector in your Ballerina application, modify the `.bal` file as follows:

### Step 1: Import connector

Import the `ballerinax/sap.jco` module into your Ballerina project.

```ballerina
import ballerinax/sap.jco;
```

### Step 2: Create a new connector instance

#### Initialize a new JCo client instance

Configure the necessary SAP connection parameters in `Config.toml` in the project directory:

```toml
[configurations]
ashost = "localhost"
sysnr = "00"
jcoClient = "000"
user = "JCOTESTER"
passwd = "SECRET"
group = "DEV2"
lang = "EN"
```

Then, create a new JCo client instance for RFC and IDoc operations.

```ballerina
configurable jco:DestinationConfig configurations = ?;
jco:Client jcoClient = check new (configurations);
```

Provide an explicit `destinationId` when the client is referenced by a listener as its `repositoryDestination`:

```ballerina
jco:Client jcoClient = check new (configurations, destinationId = "MY_DESTINATION");
```

#### Initialize a new JCo listener instance

Configure the necessary SAP connection parameters in `Config.toml` in the project directory.

`repositoryDestination` is required — the listener uses it to look up IDoc segment metadata and RFC function module metadata from SAP. It accepts two forms:

**Option 1: Reference an existing Client destination** — provide the `destinationId` of an already-initialized `Client`:

```toml
[configurations]
gwhost = "sapgw.example.com"
gwserv = "sapgw00"
progid = "JCO_PROGRAM_ID"
connectionCount = 2
repositoryDestination = "MY_DESTINATION"
```

**Option 2: Supply SAP credentials directly** — the listener registers an internal JCo destination automatically, so no separate `Client` is required:

```toml
[configurations]
gwhost = "sapgw.example.com"
gwserv = "sapgw00"
progid = "JCO_PROGRAM_ID"
connectionCount = 2

[configurations.repositoryDestination]
ashost = "sap.example.com"
sysnr = "00"
jcoClient = "100"
user = "SAP_USER"
passwd = "SAP_PASSWORD"
```

Then, create a new JCo listener instance for IDoc listener operations.

```ballerina
configurable jco:ServerConfig configurations = ?;
jco:Listener jcoListener = check new (configurations);
```

Alternatively, use `AdvancedConfig` (a flat `map<string>`) to supply raw JCo property keys when you need settings not covered by `ServerConfig` or `DestinationConfig`:

```toml
[configurations]
"jco.server.gwhost" = "sample.gwhost.com"
"jco.server.gwserv" = "sapgw00"
"jco.server.progid" = "SAMPLE_PROG_ID"
"jco.server.connection_count" = "2"
"jco.server.repository_destination" = "MY_DESTINATION"
"jco.client.ashost" = "10.128.0.1"
"jco.client.sysnr" = "00"
"jco.client.client" = "100"
"jco.client.user" = "JCOTESTER"
"jco.client.passwd" = "SECRET"
"jco.client.r3name" = "DEV"
```

### Step 3: Invoke connector operations

Now you can use the operations available within the connector.

#### Execute a Function Module via RFC

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

#### Send IDocs to an SAP system

```ballerina
public function main() returns error? {
    xml iDoc = check io:fileReadXml("resources/sample.xml");
    check jcoClient->sendIDoc(iDoc);
    io:println("IDoc sent successfully.");
}
```

#### Close the client

Call `close` when the client is no longer needed to release the JCo destination registration:

```ballerina
check jcoClient.close();
```

After `close`, any call to `execute` or `sendIDoc` returns a `ConfigurationError`. Calling `close` multiple times is safe.

#### Initialize a listener for incoming IDocs

```ballerina
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

#### Initialize a listener for inbound RFC calls

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

### Step 4: Run the Ballerina application

To run your Ballerina application which interacts with SAP using the SAP JCo Connector, execute:

```bash
bal run
```

## Examples

The `Ballerina SAP JCo Connector` provides practical examples illustrating usage in various scenarios. Explore these
scenarios to understand how to automate processes involving SAP systems and external data sources using Ballerina.

1. [SAP Inventory Update via RFC](https://github.com/ballerina-platform/module-ballerinax-sap.jco/tree/main/examples/sap_inventory_update) - Integrate external inventory data into an SAP system and
   update inventory records through an RFC.

2. [Automate iDoc Dispatch](https://github.com/ballerina-platform/module-ballerinax-sap.jco/tree/main/examples/idoc_automation) - Demonstrate the automation of generating and dispatching iDocs for
   shipment details.

3. [Automated Supplier Order Processing via iDoc Listener](https://github.com/ballerina-platform/module-ballerinax-sap.jco/tree/main/examples/order_idoc_listener) - Set up an iDoc listener to automate
   supplier order processing.

4. [SAP Product Catalog Sync](https://github.com/ballerina-platform/module-ballerinax-sap.jco/tree/main/examples/sap_product_catalog) - Query SAP material master data using RFC table parameters
   (filter criteria and field selection) and sync the results to an external product catalog API.

5. [SAP Real-Time Credit Check Service](https://github.com/ballerina-platform/module-ballerinax-sap.jco/tree/main/examples/sap_credit_check_service) - Expose a Ballerina service as an inbound RFC
   server that SAP calls synchronously during sales order creation to validate customer creditworthiness.

## Overview

[SAP](https://www.sap.com/india/index.html) is a global leader in enterprise resource planning (ERP) software. Beyond
ERP, SAP offers a diverse range of solutions including human capital management (HCM), customer relationship
management (CRM), enterprise performance management (EPM), product lifecycle management (PLM), supplier relationship
management (SRM), supply chain management (SCM), business technology platform (BTP), and the SAP AppGyver programming
environment for businesses.

The `ballerinax/sap.jco` package exposes the SAP JCo library as ballerina functions.

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

To use the SAP JCo connector, you need to download the `sapidoc3.jar` and `sapjco3.jar` middleware libraries from the
SAP
support portal and add the dependencies to your Ballerina project.

#### Step 1: Download Middleware Libraries

1. Go to the [SAP Support Portal](https://support.sap.com/en/index.html).
2. Search for and download the following files:
   - sapjco3.jar
   - sapidoc3.jar

#### Step 2: Setting Up Environment

1. **Install JRE**: Ensure you have Java Runtime Environment (JRE) version 17 installed on your system.

2. **Set CLASSPATH**: Configure the CLASSPATH environment variable to include the JAR files and the following native SAP JCo libraries based on your operating system:

   | Operating System | Native SAP JCo Library  |
   |------------------|-------------------------|
   | Linux            | `libsapjco3.so`         |
   | Windows          | `sapjco3.dll`           |
   | MacOS            | `libsapjco3.dylib`      |

#### Step 3: Add Dependencies to Ballerina.toml

After downloading the libraries, add them to your `Ballerina.toml` file in the Ballerina project by specifying the
paths and relevant details.

```toml
[[platform.java17.dependency]]
path = "../sapidoc3.jar"
groupId = "com.sap"
artifactId = "com.sap.conn.idoc"
version = "3.1.*"

[[platform.java17.dependency]]
path = "../sapjco3.jar"
groupId = "com.sap"
artifactId = "com.sap.conn.jco"
version = "3.1.*"
```

Ensure that the paths to the JAR files are correct and relative to your project directory.

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

#### Initialize a new JCo listener instance

Configure the necessary SAP connection parameters in `Config.toml` in the project directory:

```toml
[configurations]
gwhost = "sapgw.example.com"
gwserv = "3300"
progid = "JCO_PROGRAM_ID"
```

Then, create a new JCo listener instance for IDoc listener operations.

```ballerina
configurable jco:ServerConfig configurations = ?;
jco:Listener jcoListener = check new (configurations);
```

In addition to the `DestinationConfig` and `ServerConfig`, you can also use `AdvancedConfig` to configure a client and/or listener. For example, the `AdvancedConfig` can be used to configure the listener with client as follows:

```toml
[configurations]
"jco.server.gwhost" = "sample.gwhost.com"
"jco.server.gwserv" = "sapgw00"
"jco.server.progid" = "SAMPLE_PROG_ID"
"jco.server.connection_count" = "1"
"jco.server.repository_destination" = "REPOSITORY_DESTINATION"
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

#### Send IDocs to an SAP system

```ballerina
public function main() returns error? {
    xml iDoc = check io:fileReadXml("resources/sample.xml");
    check jcoClient->sendIDoc(iDoc);
    io:println("IDoc sent successfully.");
}
```

#### Initialize a listener for incoming IDocs

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

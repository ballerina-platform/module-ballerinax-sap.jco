# Ballerina SAP Connector

[![Build](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/ci.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/ci.yml)
[![Trivy](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/trivy-scan.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/trivy-scan.yml)
[![GraalVM Check](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/build-with-bal-test-graalvm.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerinax-sap.jco/actions/workflows/build-with-bal-test-graalvm.yml)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/ballerina-platform/module-ballerinax-sap.jco.svg)](https://github.com/ballerina-platform/module-ballerinax-sap.jco/commits/main)
[![GitHub Issues](https://img.shields.io/github/issues/ballerina-platform/ballerina-library/module/sap.svg?label=Open%20Issues)](https://github.com/ballerina-platform/ballerina-library/labels/module%2Fsap)

[SAP](https://www.sap.com/india/index.html) is a global leader in enterprise resource planning (ERP) software. Beyond
ERP, SAP offers a diverse range of solutions including human capital management (HCM), customer relationship
management (CRM), enterprise performance management (EPM), product lifecycle management (PLM), supplier relationship
management (SRM), supply chain management (SCM), business technology platform (BTP), and the SAP AppGyver programming
environment for businesses.

The `ballerinax/sap.jco` package exposes the SAP JCo library as ballerina functions.

## Setup Guide

### Obtain SAP Connection Parameters

To connect to an SAP system, you need to obtain the following connection parameters from your SAP administrator. These
parameters are required to establish a connection to the SAP system:

#### Required Parameters:

- **Host**: The hostname or IP address of the SAP server.
- **System Number**: Identifies the system within the landscape.
- **Client**: Specifies the client ID of the SAP system, which is used for login.
- **User Name**: Your SAP user account name.
- **Password**: Your account password.

There may be additional parameters required based on your organization's SAP configuration. Consult your SAP
administrator
for the complete list of parameters needed for your setup.

## Quickstart

To use the SAP JCo connector in your Ballerina application, modify the `.bal` file as follows:

### Step 1: Import connector

Import the `ballerinax/sap.jco` module into your Ballerina project.

```ballerina
import ballerinax/sap.jco;
```

### Step 2: Create a new connector instance

Configure the necessary SAP connection parameters in `Config.toml` in the project directory:

```toml
[DestinationConfig]
host = "XXXXXXXX"
systemNumber = "XXXXXXXX"
jcoClient = "XXXXXXXX"
user = "XXXXXXXX"
password = "XXXXXXXX"
```

Then, create a new JCo client instance using the configurations.

```ballerina
configurable jco:DestinationConfig configurations = ?;
jco:Client jcoClient = check new (configurations);
```

### Step 3: Invoke connector operations

Now you can use the operations available within the connector.

#### Execute a Function Module

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
    string iDocString = check io:fileReadString("resources/sample.xml");
    check jcoClient->sendIDoc(iDocString);
    io:println("IDoc sent successfully.");
}
```

#### Initialize a listener for incoming IDocs

```ballerina
listener jco:Listener iDocListener = new (configurations);

service jco:Service on iDocListener {
    remote function onIDoc(xml idoc) returns error? {
        check io:fileWriteXml("resources/received_idoc.xml", idoc);
        io:println("IDoc received and saved.");
    }
    remote function onError(error err) returns error? {
        io:println("Error occurred: ", err);
    }
}
```

### Step 4: Run the Ballerina application

To run your Ballerina application which interacts with SAP using the SAP JCo Connector, execute:

```bash
bal run
```

## Issues and projects

The **Issues** and **Projects** tabs are disabled for this repository as this is part of the Ballerina library. To
report bugs, request new features, start new discussions, view project boards, etc., visit the Ballerina
library [parent repository](https://github.com/ballerina-platform/ballerina-library).

This repository only contains the source code for the package.

## Build from the source

### Prerequisites

1. Download and install Java SE Development Kit (JDK) version 17. You can download it from either of the following
   sources:
   
    * [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
    * [OpenJDK](https://adoptium.net/)

   > **Note:** After installation, remember to set the `JAVA_HOME` environment variable to the directory where JDK was
   installed.

2. Download and install [Ballerina Swan Lake](https://ballerina.io/).

3. Download and install [Docker](https://www.docker.com/get-started).

   > **Note**: Ensure that the Docker daemon is running before executing any tests.

4. Download `sapidoc3.jar` and `sapjco3.jar` middleware libraries from the SAP support portal and copy those libraries
   to `native/libs` folder.

5. Download the native SAP JCo library and copy it to the JAVA Class path. (This is for running test cases and examples
   ONLY)

   | OS      | Native SAP jcolibrary |
   |---------|-----------------------|
   | Linux   | `libsapjco3.so`       |
   | Windows | `sapjco3.dll`         |
   | MacOS   | `libsapjco3.dylib`    |

### Build options

Execute the commands below to build from the source.

1. To build the package:

   ```bash
   ./gradlew clean build
   ```

2. To run the tests:

   ```bash
   ./gradlew clean test
   ```

3. To build the without the tests:

   ```bash
   ./gradlew clean build -x test
   ```

4. To run tests against different environment:

   ```bash
   ./gradlew clean test 
   ```

5. To debug package with a remote debugger:

   ```bash
   ./gradlew clean build -Pdebug=<port>
   ```

6. To debug with the Ballerina language:

   ```bash
   ./gradlew clean build -PbalJavaDebug=<port>
   ```

7. Publish the generated artifacts to the local Ballerina Central repository:

    ```bash
    ./gradlew clean build -PpublishToLocalCentral=true
    ```

8. Publish the generated artifacts to the Ballerina Central repository:

   ```bash
   ./gradlew clean build -PpublishToCentral=true
   ```

## Contribute to Ballerina

As an open-source project, Ballerina welcomes contributions from the community.

For more information, go to
the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All the contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).

## Useful links

* For more information go to the [`sap` package](https://lib.ballerina.io/ballerinax/sap/latest).
* For example demonstrations of the usage, go to [Ballerina By Examples](https://ballerina.io/learn/by-example/).
* Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
* Post all technical questions on Stack Overflow with
  the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.

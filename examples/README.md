# Examples

The `Ballerina SAP JCo Connector` provides practical examples illustrating usage in various scenarios. Explore these
scenarios to understand how to automate processes involving SAP systems and external data sources using Ballerina.

1. [SAP Inventory Update via RFC](./sap_inventory_update/) - Integrate external inventory data into an SAP system and
   update inventory records through an RFC.

2. [Automate iDoc Dispatch](./idoc_automation/) - Demonstrate the automation of generating and dispatching iDocs for
   shipment details.

3. [Automated Supplier Order Processing via iDoc Listener](./order_idoc_listener/) - Set up an iDoc listener to automate
   supplier order processing.

## Prerequisites

1. Refer to the [Setup Guide](../../README.md) to configure the Ballerina SAP JCo Connector.

2. For each example, create a `Config.toml` file in the example directory with your SAP connection parameters and any
   required API endpoints.

   For examples that use a `jco:Client` (RFC execution and IDoc send):

    ```toml
    apiEndpoint = "https://api.example.com/inventory"

    [sapConfig]
    ashost = "sap.example.com"
    sysnr = "00"
    jcoClient = "000"
    user = "JCOTESTER"
    passwd = "SECRET"
    group = "DEVGROUP"
    lang = "EN"
    ```

   For examples that use a `jco:Listener` (IDoc receive):

    ```toml
    [sapConfig]
    gwhost = "sapgw.example.com"
    gwserv = "sapgw00"
    progid = "JCO_PROGRAM_ID"
    connectionCount = 2
    ```

   If the listener requires IDoc metadata resolution, also initialise a `jco:Client` with a matching `destinationId` and set `repositoryDestination` in the server config. The `destinationId` of the `jco:Client` must equal the value of `repositoryDestination`:

    ```toml
    [sapConfig]
    gwhost = "sapgw.example.com"
    gwserv = "sapgw00"
    progid = "JCO_PROGRAM_ID"
    connectionCount = 2
    repositoryDestination = "MY_DESTINATION"

    [clientConfig]
    ashost = "sap.example.com"
    sysnr = "00"
    jcoClient = "000"
    user = "JCOTESTER"
    passwd = "SECRET"
    destinationId = "MY_DESTINATION"
    ```

## Running an Example

Execute the following commands to build an example from the source:

* To build an example:

    ```bash
    bal build
    ```

* To run an example:

    ```bash
    bal run
    ```

## Building the Examples with the Local Module

**Warning**: Due to the absence of support for reading local repositories for single Ballerina files, the Bala of the
module is manually written to the central repository as a workaround. Consequently, the bash script may modify your
local Ballerina repositories.

Execute the following commands to build all the examples against the changes you have made to the module locally:

* To build all the examples:

    ```bash
    ./build.sh build
    ```

* To run all the examples:

    ```bash
    ./build.sh run
    ```

## Additional Resources

* [Ballerina Data Mapper](https://ballerina.io/learn/vs-code-extension/implement-the-code/data-mapper/)
* [XML to Record Conversion](https://ballerina.io/learn/by-example/xml-to-record-conversion/)
* [Record to XML Conversion](https://ballerina.io/learn/by-example/xml-from-record-conversion/)

These resources provide essential tools for data transformation and integration tasks, facilitating seamless interaction
between external data sources and SAP systems.

# Examples

The `Ballerina SAP JCo Connector` provides practical examples illustrating usage in various scenarios. Explore these
scenarios to understand how to automate processes involving SAP systems and external data sources using Ballerina.

1. [SAP Inventory Update via RFC](./sap_inventory_update/) - Integrate external inventory data into an SAP system and
   update inventory records through an RFC.

2. [Automate iDoc Dispatch](./idoc_automation/) - Demonstrate the automation of generating and dispatching iDocs for
   shipment details.

3. [Automated Supplier Order Processing via iDoc Listener](./order_idoc_listener/) - Set up an iDoc listener to automate
   supplier order processing.

4. [SAP Product Catalog Sync](./sap_product_catalog/) - Query SAP material master data using RFC table parameters
   (filter criteria and field selection) and sync the results to an external product catalog API.

5. [SAP Real-Time Credit Check Service](./sap_credit_check_service/) - Expose a Ballerina service as an inbound RFC
   server that SAP calls synchronously during sales order creation to validate customer creditworthiness against an
   external credit bureau API.

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

   For examples that use a `jco:Listener` (IDoc receive or inbound RFC):

    `repositoryDestination` is required and must match the `destinationId` of an already-initialized `jco:Client`. The listener uses this connection to look up IDoc segment metadata and RFC function module metadata from SAP. Initialize the `jco:Client` before the `jco:Listener`:

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

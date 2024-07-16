# Example Scenario: SAP Inventory Update for BestWidgets

In this example, we will demonstrate how to integrate external data into an SAP system using the Ballerina SAP JCo
Connector, showcasing Ballerina's data mapping capabilities and the RFC function calling feature. The scenario involves
a fictional company, "BestWidgets Inc." which manufactures and supplies various types of widgets. BestWidgets Inc. uses
a third-party logistics provider for inventory management, which exposes an API for inventory updates.

The process starts with fetching the latest inventory data from the third-party API, which includes details like widget
types, quantities, and locations. This data is received in one format and needs to be transformed to match the SAP
system's expected input format for inventory updates. Once transformed, the data is sent to the SAP system via a Remote
Function Call (RFC) to update the inventory records. The response from SAP, indicating success or failure of the update,
is then processed to finalize the operation.

![Overview](https://raw.githubusercontent.com/RDPerera/module-ballerinax-sap.jco/test/examples/sap_inventory_update/resources/doc_images/diagram.png)

**Step-by-Step Process:**

1. **Fetch Inventory Data**: Retrieve the latest inventory data from the third-party logistics provider's API.
2. **Data Transformation**: Map the fetched data to the format required by the SAP RFC function using Ballerina's
   built-in data mapping capabilities.
3. **Update SAP Inventory**: Call the RFC function to update the SAP system with the transformed data.
4. **Process Response**: Handle the SAP system's response to ensure the inventory update was successful.

**Tip:** The transformation between the ApiInventoryData and SapInventoryInput formats can be done using Ballerina's
built-in [data mapper](https://ballerina.io/learn/vs-code-extension/implement-the-code/data-mapper/). The data mapper
allows you to define the mapping between the source and target data structures using a visual editor in the Ballerina
Composer tool.

![Data Mapper](https://raw.githubusercontent.com/RDPerera/module-ballerinax-sap.jco/test/examples/sap_inventory_update/resources/doc_images/bal_data_mapper.png)

This visual editor provides a drag-and-drop interface to define the mapping between the source and target data
structures, making it easy to transform data between different formats.

## Prerequisites

### 1. Setup SAP JCo Connector

Ensure that the SAP JCo Connector libraries are installed and properly configured on your system. You also need valid
credentials to connect to your SAP system. Refer to the [Setup Guide](../../README.md) for necessary credentials.

### 2. Configuration

Configure the necessary SAP connection parameters in `Config.toml` in the example directory:

```toml
[DestinationConfig]
host = "localhost"
systemNumber = "00"
jcoClient = "000"
user = "JCOTESTER"
password = "SECRET"
group = "DEV2"

[APIConfig]
apiEndpoint = "https://api.example.com/inventory"


```

## Run the Example

Execute the following command to run the example:

```bash
bal run
```

Upon successful execution, the SAP system's inventory will be updated with the latest data, and the results of the
update will be logged to the console, confirming the status of each material update.
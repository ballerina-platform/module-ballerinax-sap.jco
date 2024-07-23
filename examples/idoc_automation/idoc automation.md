# Automating iDoc Dispatch for GlobalExports

This example demonstrates how to automate the generation and dispatching of Intermediate Documents (iDocs) in an SAP
system using the Ballerina SAP JCo Connector.
The scenario involves "GlobalExports Inc.," a company that uses external data sources to get updates on shipment
details. These updates are converted into iDocs and sent to their SAP system for processing in the format of the
DELVRY03 iDoc type, commonly used for delivery and shipment processing.

![Overview](https://raw.githubusercontent.com/RDPerera/module-ballerinax-sap.jco/test/examples/idoc_automation/resources/docs_images/diagram.png)

**Step-by-Step Process:**

1. **Fetch Shipment Data**: Retrieve the latest shipment data from an external API.
2. **Generate iDocs**: Convert the fetched record into the DELVRY03 iDoc format.
3. **Send iDocs to SAP**: Dispatch the generated iDocs to the SAP system for processing.

**Tip:** To convert the fetched record into the DELVRY03 iDoc format, we first generate the necessary records to
represent the iDoc by utilizing the
Ballerina [XML to Record](https://ballerina.io/learn/by-example/xml-to-record-conversion/) VS Code function. This
function helps create a Ballerina record structure that matches the schema of the iDoc. Next, we use
the [Ballerina Data Mapper](https://ballerina.io/learn/vs-code-extension/implement-the-code/data-mapper/) extension to
map the shipment data fields to the corresponding fields in the iDoc record. Once the data mapping is complete, the
record can be converted into XML format, which is the iDoc format that can be sent to the SAP system.

![Data Mapper Screenshot](https://raw.githubusercontent.com/RDPerera/module-ballerinax-sap.jco/test/examples/idoc_automation/resources/docs_images/bal_data_mapper.png)

## Prerequisites

### 1. Setup SAP JCo Connector

Ensure that the SAP JCo Connector libraries are installed and properly configured on your system. This includes having
valid credentials and access to the necessary SAP resources. Refer to the [Setup Guide](../../README.md) for necessary
credentials.

### 2. Configuration

Configure the necessary SAP connection parameters and the external API endpoint in `Config.toml` within the example
directory:

```toml
apiEndpoint = "https://api.example.com/inventory"

[sapConfig]
host = "localhost"
systemNumber = "00"
jcoClient = "000"
user = "JCOTESTER"
password = "SECRET"
group = "DEV2"
```

## Run the Example

Execute the following command to run the example:

```bash
bal run
```

Upon successful execution, check the `generated_iDocs` directory for the XML files and confirm the logs to verify that
the iDocs have been sent to the SAP system.
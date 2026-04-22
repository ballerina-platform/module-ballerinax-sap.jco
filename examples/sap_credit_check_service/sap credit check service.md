# SAP Real-Time Credit Check Service for TechFlow Inc.

In this example, we demonstrate how to expose a Ballerina service as an inbound RFC server that SAP calls
synchronously during business processing. The scenario involves a fictional company, "TechFlow Inc.", a manufacturer
using SAP SD (Sales & Distribution). Before confirming a sales order, TechFlow's ABAP code calls an external RFC
function module `Z_CHECK_CUSTOMER_CREDIT` to validate customer creditworthiness in real time. The Ballerina service
implements that function module, queries an external credit bureau HTTP API, and returns the credit decision back to SAP
as RFC export parameters.

**Step-by-Step Process:**

1. **SAP initiates the credit check**: When a sales order is created, ABAP calls `Z_CHECK_CUSTOMER_CREDIT` via the
   RFC destination registered in SM59, passing `CUSTOMER_ID` and `ORDER_AMOUNT` as import parameters.
2. **Ballerina receives the RFC call**: The `RfcService.onCall` handler receives the function name and the import
   parameters forwarded by JCo.
3. **Query the credit bureau**: Ballerina calls the external credit bureau HTTP API with the customer ID and retrieves
   the customer's credit score, credit limit, and account status.
4. **Evaluate the credit decision**: The service approves the order if the account is active, the credit score is at
   least 600, and the remaining credit limit covers the order amount.
5. **Return the decision to SAP**: The handler returns a `jco:RfcRecord` containing `CREDIT_STATUS` (`"A"` for
   approved, `"R"` for rejected), `CREDIT_SCORE`, `CREDIT_LIMIT`, and a human-readable `MESSAGE`. JCo serializes
   these as RFC export parameters back to the ABAP caller.

## Prerequisites

### 1. Setup SAP JCo Connector

Ensure that the SAP JCo Connector libraries are installed and properly configured on your system. You also need valid
credentials to connect to your SAP system. Refer to the [Setup Guide](../../README.md) for necessary credentials.

### 2. Register the RFC Destination in SAP

In transaction **SM59**, create an RFC destination of type **T (TCP/IP)** that points to the program ID you will
configure below. This is the destination your ABAP code uses when calling `Z_CHECK_CUSTOMER_CREDIT`.

### 3. Configuration

Configure the SAP connection parameters and the credit bureau API endpoint in `Config.toml` in the example directory.

`repositoryDestination` must match the destination ID passed to the `jco:Client`. The listener uses this client
connection to look up RFC function module metadata from SAP:

```toml
creditBureauApiEndpoint = "https://api.creditbureau.example.com"

[clientConfig]
destinationId = "CREDIT_CHECK_DEST"
ashost = "sap.example.com"
sysnr = "00"
jcoClient = "000"
user = "JCOTESTER"
passwd = "SECRET"

[sapConfig]
gwhost = "sapgw.example.com"
gwserv = "sapgw00"
progid = "CREDIT_CHECK_SERVER"
connectionCount = 2
repositoryDestination = "CREDIT_CHECK_DEST"
```

## Run the Example

Execute the following command to run the example:

```bash
bal run
```

The service starts and registers with the SAP gateway under the configured program ID. When SAP calls
`Z_CHECK_CUSTOMER_CREDIT`, the connector logs the credit decision to the console and returns the result to the ABAP
caller. Gateway connectivity errors (e.g., SAP is temporarily unreachable) are logged by `onError` — JCo retries
the connection automatically and the service recovers without a restart.

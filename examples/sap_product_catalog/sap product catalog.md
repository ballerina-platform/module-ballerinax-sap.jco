# SAP Product Catalog Sync for GlobalTech Corp

In this example, we demonstrate how to use table parameters with the Ballerina SAP JCo Connector to query data from
SAP and sync it to an external system. The scenario involves a fictional company, "GlobalTech Corp.", which manufactures
finished goods and maintains its material master data in SAP. GlobalTech Corp. needs to periodically sync its SAP
material catalog to an external product catalog API used by its e-commerce platform.

The process uses `RFC_READ_TABLE` — a generic SAP function module available on all systems — to query the `MARA`
material master table for finished goods. The query uses two types of table parameters: `OPTIONS` to filter rows
(equivalent to a SQL WHERE clause) and `FIELDS` to select specific columns. The RFC returns the matching rows in the
`DATA` table parameter as pipe-delimited strings. These rows are then parsed and pushed to the external product
catalog API.

**Step-by-Step Process:**

1. **Execute RFC with table parameters**: Call `RFC_READ_TABLE` with `OPTIONS` (filter: finished goods only) and
   `FIELDS` (columns: material number, type, industry sector, base unit) as table parameters.
2. **Receive table output**: The RFC returns matching rows in the `DATA` table parameter, merged alongside any export
   parameters into the flat `ReadTableResponse` record.
3. **Parse and transform**: Split each pipe-delimited `WA` string into individual field values and map them to
   `ProductCatalogItem` records.
4. **Sync to catalog API**: POST the transformed items to the external product catalog API in bulk.

## Prerequisites

### 1. Setup SAP JCo Connector

Ensure that the SAP JCo Connector libraries are installed and properly configured on your system. You also need valid
credentials to connect to your SAP system. Refer to the [Setup Guide](../../README.md) for necessary credentials.

### 2. Configuration

Configure the necessary SAP connection parameters and the catalog API endpoint in `Config.toml` in the example
directory:

```toml
catalogApiEndpoint = "https://api.example.com/catalog"

[sapConfig]
ashost = "sap.example.com"
sysnr = "00"
jcoClient = "000"
user = "JCOTESTER"
passwd = "SECRET"
group = "DEVGROUP"
lang = "EN"
```

## Run the Example

Execute the following command to run the example:

```bash
bal run
```

Upon successful execution, the finished goods from SAP's material master will be synced to the product catalog API,
and the number of synced items will be logged to the console.

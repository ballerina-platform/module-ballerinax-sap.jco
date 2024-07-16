import ballerina/http;
import ballerina/io;
import ballerinax/sap.jco;

// Configurable variables to hold the configuration values from Config.toml
configurable jco:DestinationConfig sapConfig = ?;
configurable string apiEndpoint = ?;

public function main() returns error? {

    // Create SAP and HTTP clients
    jco:Client sapClient = check new (sapConfig);
    http:Client logisticsApi = check new (apiEndpoint);

    // Fetch inventory data from the API
    ApiInventoryData[] inventoryData = check logisticsApi->get("/latest");

    // Data mapping from API response to SAP input format
    SapInventoryInput[] sapInputs = from var data in inventoryData
        select {
            materialId: data.widgetId,
            stockLevel: data.quantity,
            plant: data.location
        };

    // Execute RFC function to update inventory
    foreach var input in sapInputs {
        SapUpdateResponse? result = check sapClient->execute("UPDATE_INVENTORY", input);
        io:println("Update Status for Material ", input.materialId, ": ", result?.status);
    }
}

function transform(ApiInventoryData apiInventoryData) returns SapInventoryInput => {
    materialId: apiInventoryData.widgetId,
    stockLevel: apiInventoryData.quantity,
    plant: apiInventoryData.location
};

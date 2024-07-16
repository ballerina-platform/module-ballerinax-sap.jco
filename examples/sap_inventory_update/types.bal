// Define the record to hold the API response
type ApiInventoryData record {
    string widgetId;
    int quantity;
    string location;
    string lastUpdated;
};

// Define the record for SAP RFC function input
type SapInventoryInput record {
    string materialId;
    int stockLevel;
    string plant;
};

// Define the record for RFC function output
type SapUpdateResponse record {
    string status;
    string message;
};

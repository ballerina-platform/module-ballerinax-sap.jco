/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.sap;

import com.sap.conn.idoc.IDocDocumentList;
import com.sap.conn.idoc.IDocException;
import com.sap.conn.idoc.IDocFactory;
import com.sap.conn.idoc.IDocRepository;
import com.sap.conn.idoc.IDocXMLProcessor;
import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRepository;
import com.sap.conn.jco.JCoStructure;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.StructureType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static Object initializeClient(BObject client, BMap<BString, Object> jcoDestinationConfig,
                                          BString destinationId) {
        try {
            BallerinaDestinationDataProvider dp = new BallerinaDestinationDataProvider();
            com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(dp);
            dp.addDestination(jcoDestinationConfig, destinationId);
            JCoDestination destination = JCoDestinationManager.getDestination(destinationId.toString());
            logger.debug("JCo Client initialized");
            client.addNativeData(SAPConstants.RFC_DESTINATION, destination);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Exception e) {
            logger.error("Client initialization failed.");
            return SAPErrorCreator.createError("Client initialization failed.", e);
        }
    }

    public static Object execute(BObject client, BString functionName,
                                  BMap<BString, Object> inputParams, BTypedesc outputParamType) {
            try {
                StructureType outputParamsStructType = (StructureType) outputParamType.getDescribingType();

                JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
                JCoRepository repository = destination.getRepository();
                if (functionName.toString().isEmpty()) {
                    return SAPErrorCreator.fromBError("Function name is empty", null);
                }
                JCoFunction function = repository.getFunction(functionName.toString());
                if (function == null) {
                    return SAPErrorCreator.fromBError("RFC function '" + functionName + "' not found in SAP."
                            , null);
                }

                JCoParameterList importParams = function.getImportParameterList();
                setImportParams(importParams, inputParams);

                function.execute(destination);

                JCoParameterList exportParams = function.getExportParameterList();
                JCoStructure exportStructure = exportParams.getStructure(SAPConstants.RETURN);

                // This checks if the "TYPE" field value is not "S" (Success), "I" (Information), "W" (Warning),
                // or an empty string. If the "TYPE" is any other value, it implies an erroneous response.
                if (!exportStructure.getString(SAPConstants.TYPE).isEmpty() &&
                        !exportStructure.getString(SAPConstants.TYPE).equals(SAPConstants.S)) {
                    return SAPErrorCreator.fromBError(exportStructure.getString(SAPConstants.MESSAGE), null);
                }
                return populateOutputMap(exportStructure, outputParamsStructType);
            } catch (JCoException e) {
                logger.error("JCoException occurred. Error: " + e.getMessage());
                return SAPErrorCreator.fromJCoException(e);
            }
    }

    public static Object sendIDoc(BObject client, BXml iDoc, BString iDocType) {
        try {
            JCoDestination destination = (JCoDestination) client.getNativeData(SAPConstants.RFC_DESTINATION);
            String iDocXML = iDoc.toString();
            char version = iDocType.toString().charAt(0);

            String tid = destination.createTID();
            logger.debug("TID created: " + tid);

            IDocFactory iDocFactory = JCoIDoc.getIDocFactory();
            IDocRepository iDocRepository = JCoIDoc.getIDocRepository(destination);
            IDocXMLProcessor processor = iDocFactory.getIDocXMLProcessor();
            IDocDocumentList iDocList = processor.parse(iDocRepository, iDocXML);

            JCoIDoc.send(iDocList, version, destination, tid);
            destination.confirmTID(tid);
            logger.debug("IDoc sent successfully with TID: " + tid);
            return null;
        } catch (JCoException e) {
            logger.error("JCoException occurred");
            return SAPErrorCreator.fromJCoException(e);
        } catch (IDocException e) {
            logger.error("IDocException occurred");
            return SAPErrorCreator.fromIDocException(e);
        }
    }

    private static BMap<BString, Object> populateOutputMap(JCoStructure exportStructure,
                                                           StructureType outputParamType) {
        BMap<BString, Object> outputMap = ValueCreator.createRecordValue((RecordType) outputParamType);
        for (int i = 0; i < exportStructure.getMetaData().getFieldCount(); i++) {
            String fieldName = exportStructure.getMetaData().getName(i);
            if (outputParamType.getFields().containsKey(fieldName)) {
                int type = outputParamType.getFields().get(fieldName).getFieldType().getTag();
                switch (type) {
                    case TypeTags.STRING_TAG:
                        String value = exportStructure.getString(i);
                        outputMap.put(StringUtils.fromString(fieldName), StringUtils.fromString(value));
                        break;
                    case TypeTags.INT_TAG:
                        int intValue = exportStructure.getInt(i);
                        outputMap.put(StringUtils.fromString(fieldName), intValue);
                        break;
                    case TypeTags.FLOAT_TAG:
                        double doubleValue = exportStructure.getDouble(i);
                        outputMap.put(StringUtils.fromString(fieldName), doubleValue);
                        break;
                    case TypeTags.DECIMAL_TAG:
                        BigDecimal decimalValue = exportStructure.getBigDecimal(i);
                        outputMap.put(StringUtils.fromString(fieldName), ValueCreator.createDecimalValue(decimalValue));
                        break;
                    // Add support for other types including structs as required.
                    default:
                        throw SAPErrorCreator.fromBError("Error while retrieving output parameter for field: " +
                                fieldName + ". Unsupported type " + type, null);
                }
            }
        }
        return outputMap;
    }

    private static void setImportParams(JCoParameterList jcoPramList, BMap<BString, Object> inputParams) {
        inputParams.entrySet().forEach(entry -> {
            int type = TypeUtils.getType(entry.getValue()).getTag();
            switch (type) {
                case TypeTags.STRING_TAG:
                    jcoPramList.setValue(entry.getKey().toString(), entry.getValue().toString());
                    break;
                case TypeTags.INT_TAG:
                    jcoPramList.setValue(entry.getKey().toString(), Integer.parseInt(entry.getValue().toString()));
                    break;
                case TypeTags.FLOAT_TAG:
                    jcoPramList.setValue(entry.getKey().toString(), Double.parseDouble(entry.getValue().toString()));
                    break;
                case TypeTags.DECIMAL_TAG:
                    jcoPramList.setValue(entry.getKey().toString(), new BigDecimal(entry.getValue().toString()));
                    break;
                    // Add support for other types including structs as required.
                default:
                    throw SAPErrorCreator.fromBError("Error while setting input parameter for field: " +
                            entry.getKey().toString() + ". Unsupported type " + type + " Supported types are: " +
                            "string, int, float, decimal", null);
            }
        });
    }

}

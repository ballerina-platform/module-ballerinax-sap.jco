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
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoParameterList;
import io.ballerina.lib.sap.parameterprocessor.ExportParameterProcessor;
import io.ballerina.lib.sap.parameterprocessor.ImportParameterProcessor;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static Object initializeClient(BObject client, BMap<BString, Object> jcoDestinationConfig,
                                          BString destinationId) {
        return null;
    }

    public static Object execute(BObject client, BString functionName,
                                 BMap<BString, Object> inputParams, BTypedesc outputParamType) {
            try {
                if (functionName.toString().isEmpty()) {
                    return SAPErrorCreator.fromBError("Function name is empty", null);
                }

                JCoParameterList importParams = Testers.createList();

                ImportParameterProcessor.setImportParams(importParams, inputParams);
                int outputType = outputParamType.getDescribingType().getTag();
                if (outputType == TypeTags.XML_TAG) {
                    return ValueCreator.createXmlValue(importParams.toXML());
                } else if (outputType == TypeTags.JSON_TAG) {
                    return JsonUtils.parse(importParams.toJSON());
                } else if (outputType == TypeTags.RECORD_TYPE_TAG) {
                    return ExportParameterProcessor.getExportParams(importParams,
                            (RecordType) outputParamType.getDescribingType());
                } else {
                    throw SAPErrorCreator.fromBError("Unsupported output parameter type: " +
                            outputParamType.getType().getName(), null);
                }
            } catch (Throwable e) {
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
            logger.debug("TID created: {}", tid);

            IDocFactory iDocFactory = JCoIDoc.getIDocFactory();
            IDocRepository iDocRepository = JCoIDoc.getIDocRepository(destination);
            IDocXMLProcessor processor = iDocFactory.getIDocXMLProcessor();
            IDocDocumentList iDocList = processor.parse(iDocRepository, iDocXML);

            JCoIDoc.send(iDocList, version, destination, tid);
            destination.confirmTID(tid);
            logger.debug("IDoc sent successfully with TID: {}", tid);
            return null;
        } catch (JCoException e) {
            logger.error("JCoException occurred");
            return SAPErrorCreator.fromJCoException(e);
        } catch (IDocException e) {
            logger.error("IDocException occurred");
            return SAPErrorCreator.fromIDocException(e);
        }
    }

}

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

import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.idoc.jco.JCoIDocServer;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    private static final ArrayList<BObject> startedServices = new ArrayList<>();
    private static final boolean started = false;
    private static ArrayList<BObject> services = new ArrayList<>();
    private static Runtime runtime;

    public static Object init(BObject listenerBObject, BMap<BString, Object> jcoDestinationConfig) {
        try {
            String destinationName = jcoDestinationConfig.getStringValue(
                    StringUtils.fromString(SAPConstants.DESTINATION_ID)).getValue();
            BallerinaDestinationDataProvider dp = new BallerinaDestinationDataProvider();
            com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(dp);
            dp.addDestination(jcoDestinationConfig);
            JCoDestination destination = JCoDestinationManager.getDestination(destinationName);
            JCoIDocServer server = JCoIDoc.getServer(destination.getDestinationName());
            listenerBObject.addNativeData(SAPConstants.JCO_SERVER, server);
            listenerBObject.addNativeData(SAPConstants.JCO_SERVICES, services);
            listenerBObject.addNativeData(SAPConstants.JCO_STARTED_SERVICES, startedServices);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed!!!!");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Exception e) {
            logger.error("Server initialization failed!!!!");
            return SAPErrorCreator.createError("Server initialization failed!!!!", e);
        }
    }

    public static Object attach(Environment environment, BObject listenerBObject, BObject service,
                                Object name) {
        runtime = environment.getRuntime();
        try {
            JCoIDocServer server = (JCoIDocServer) listenerBObject.getNativeData(SAPConstants.JCO_SERVER);
            if (service == null) {
                return null;
            }
            if (name != null && TypeUtils.getType(name).getTag() == TypeTags.STRING_TAG) {
                service.addNativeData(SAPConstants.SERVICE_NAME, ((BString) name).getValue());
            }
            if (isStarted()) {
                services =
                        (ArrayList<BObject>) listenerBObject.getNativeData(SAPConstants.JCO_SERVICES);
                startReceivingIDocs(service, server, listenerBObject);
            }
            services.add(service);
            return null;

        } catch (Exception e) {
            logger.error("Server attach failed!!!!");
            return SAPErrorCreator.createError("Server attach failed!!!!", e);
        }
    }

    public static Object start(BObject client) {
        try {
            JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
            server.start();
        } catch (Exception e) {
            logger.error("Server start failed!!!!");
            return SAPErrorCreator.createError("Server start failed!!!!", e);
        }
        return null;
    }

    public static Object gracefulStop(BObject client) {
        return stopListener(client);
    }

    public static Object immediateStop(BObject client) {
        return stopListener(client);
    }

    public static Object detach(BObject listener, BObject service) {
        ArrayList<BObject> startedServices = (ArrayList<BObject>) listener.getNativeData(
                SAPConstants.JCO_STARTED_SERVICES);
        ArrayList<BObject> services = (ArrayList<BObject>) listener.getNativeData(SAPConstants.JCO_SERVICES);
        try {
            startedServices.remove(service);
            services.remove(service);
            return null;
        } catch (Exception e) {
            logger.error("Server detach failed!!!!");
            return SAPErrorCreator.createError("Server detach failed!!!!", e);
        }
    }

    public static Object stopListener(BObject client) {
        try {
            JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
            server.stop();
        } catch (Exception e) {
            logger.error("Server start failed!!!!");
            return SAPErrorCreator.createError("Server start failed!!!!", e);
        }
        return null;
    }

    private static boolean isStarted() {
        return started;
    }

    private static void startReceivingIDocs(BObject service, JCoIDocServer server, BObject listener) {
        IDocDispatcher iDocDispatcher =
                new IDocDispatcher(service, server, runtime);
        iDocDispatcher.receiveIDoc(listener);
    }

}

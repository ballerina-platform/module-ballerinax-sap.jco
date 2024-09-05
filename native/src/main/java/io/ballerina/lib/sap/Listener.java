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
import com.sap.conn.jco.JCoException;
import io.ballerina.lib.sap.dataproviders.SAPDestinationDataProvider;
import io.ballerina.lib.sap.dataproviders.SAPServerDataProvider;
import io.ballerina.lib.sap.idoc.BallerinaIDocHandlerFactory;
import io.ballerina.lib.sap.idoc.BallerinaThrowableListener;
import io.ballerina.lib.sap.idoc.BallerinaTidHandler;
import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    public static Object init(BObject listenerBObject, BMap<BString, Object> serverConfig, BString serverName) {
        try {
            SAPServerDataProvider sp = new SAPServerDataProvider();
            if (serverConfig.getType().getName().equals(SAPConstants.JCO_SERVER_CONFIG_NAME)) {
                sp.addServerConfig(serverConfig, serverName.getValue());
                com.sap.conn.jco.ext.Environment.registerServerDataProvider(sp);
            } else {
                if (!serverConfig.isEmpty()) {
                    Map<String, String> advancedServerConfig = new HashMap<>();
                    Map<String, String> advancedDestinationConfig = new HashMap<>();
                    serverConfig.entrySet().forEach(entry -> {
                        String rawKey = entry.getKey().toString();
                        String key = rawKey.substring(1, rawKey.length() - 1);
                        String value = entry.getValue().toString();
                        if (key.startsWith(SAPConstants.JCO_SERVER_PREFIX)) {
                            advancedServerConfig.put(key, value);
                        } else {
                            advancedDestinationConfig.put(key, value);
                        }
                    });
                    if (!advancedDestinationConfig.isEmpty()) {
                        SAPDestinationDataProvider dp = new SAPDestinationDataProvider();
                        String destinationName = advancedServerConfig.
                                containsKey(SAPServerDataProvider.JCO_REP_DEST) ?
                                advancedServerConfig.get(SAPServerDataProvider.JCO_REP_DEST) :
                                serverName.getValue();
                        dp.addAdvancedDestinationConfig(advancedDestinationConfig, destinationName);
                        com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(dp);
                    }
                    sp.addAdvancedServerConfig(advancedServerConfig, serverName.getValue());
                    com.sap.conn.jco.ext.Environment.registerServerDataProvider(sp);
                } else {
                    throw new RuntimeException("Provided a empty advanced configuration for server");
                }
            }
            JCoIDocServer server = JCoIDoc.getServer(serverName.getValue());
            listenerBObject.addNativeData(SAPConstants.JCO_SERVER, server);
            listenerBObject.addNativeData(SAPConstants.IS_SERVICE_ATTACHED, false);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Throwable e) {
            logger.error("Server initialization failed.");
            return SAPErrorCreator.createError("Server initialization failed.", e);
        }
    }

    public static Object attach(Environment environment, BObject listenerBObject, BObject service, Object name) {
        Runtime runtime = environment.getRuntime();
        JCoIDocServer server = (JCoIDocServer) listenerBObject.getNativeData(SAPConstants.JCO_SERVER);
        boolean isServiceAttached = (boolean) listenerBObject.getNativeData(SAPConstants.IS_SERVICE_ATTACHED);
        if (isServiceAttached) {
            return SAPErrorCreator.createError("One service is already attached to the listener. Only one service " +
                    "can be attached to a listener.");
        }
        try {
            server.setIDocHandlerFactory(new BallerinaIDocHandlerFactory(service, runtime));
            server.setTIDHandler(new BallerinaTidHandler());
            BallerinaThrowableListener listener = new BallerinaThrowableListener();
            server.addServerErrorListener(listener);
            server.addServerExceptionListener(listener);
            listenerBObject.addNativeData(SAPConstants.IS_SERVICE_ATTACHED, true);
            return null;
        } catch (Throwable e) {
            // We are catching Throwable here because, underlying JCo library throws throwable in certain cases.
            logger.error("Server attach failed.");
            return SAPErrorCreator.createError("Server attach failed.", e);
        }
    }

    public static Object start(BObject client) {
        try {
            JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
            server.start();
        } catch (Throwable e) {
            logger.error("Server start failed.");
            return SAPErrorCreator.createError("Server start failed.", e);
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
        try {
            listener.addNativeData(SAPConstants.JCO_SERVER, null);
            listener.addNativeData(SAPConstants.IS_SERVICE_ATTACHED, false);
        } catch (Throwable e) {
            logger.error("Server detach failed.");
            return SAPErrorCreator.createError("Server detach failed.", e);
        }
        return null;
    }

    public static Object stopListener(BObject client) {
        try {
            JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
            server.stop();
        } catch (Throwable e) {
            logger.error("Server stop failed.");
            return SAPErrorCreator.createError("Server start failed.", e);
        }
        return null;
    }
}

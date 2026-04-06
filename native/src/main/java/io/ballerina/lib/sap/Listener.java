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
import com.sap.conn.jco.server.JCoServerState;
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
import java.util.concurrent.ConcurrentHashMap;

public final class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    // JCo allows only one server per (gwhost|gwserv|progid) combination per JVM.
    // We reuse the same JCoIDocServer object for repeated Listener creations with
    // the same connection configuration so that subsequent JCoIDoc.getServer() calls
    // with a new UUID name do not fail with "already used for a running server".
    private static final Map<String, JCoIDocServer> serverRegistry = new ConcurrentHashMap<>();

    public static Object init(BObject listenerBObject, BMap<BString, Object> serverConfig, BString serverName) {
        try {
            JCoIDocServer server;
            if (serverConfig.getType().getName().equals(SAPConstants.JCO_SERVER_CONFIG)) {
                String gwhost = serverConfig.getStringValue(SAPConstants.JCO_GWHOST).toString();
                String gwserv = serverConfig.getStringValue(SAPConstants.JCO_GWSERV).toString();
                String progid = serverConfig.getStringValue(SAPConstants.JCO_PROGID).toString();
                String serverKey = gwhost + "|" + gwserv + "|" + progid;
                if (serverRegistry.containsKey(serverKey)) {
                    server = serverRegistry.get(serverKey);
                } else {
                    Object repDestObj = serverConfig.get(SAPConstants.JCO_REPOSITORY_DESTINATION);
                    String repositoryDestination = (repDestObj != null) ? repDestObj.toString() : null;
                    SAPServerDataProvider sp = SAPServerDataProvider.getInstance();
                    sp.addServerConfig(serverConfig, serverName.getValue(), repositoryDestination);
                    SAPServerDataProvider.registerIfAbsent();
                    server = JCoIDoc.getServer(serverName.getValue());
                    serverRegistry.put(serverKey, server);
                }
            } else {
                if (!serverConfig.isEmpty()) {
                    Map<String, String> advancedServerConfig = new HashMap<>();
                    Map<String, String> advancedDestinationConfig = new HashMap<>();
                    serverConfig.entrySet().forEach(entry -> {
                        String key = entry.getKey().toString();
                        String value = entry.getValue().toString();
                        if (key.startsWith(SAPConstants.JCO_SERVER_PREFIX)) {
                            advancedServerConfig.put(key, value);
                        } else {
                            advancedDestinationConfig.put(key, value);
                        }
                    });
                    String gwhost = advancedServerConfig.getOrDefault("jco.server.gwhost", "");
                    String gwserv = advancedServerConfig.getOrDefault("jco.server.gwserv", "");
                    String progid = advancedServerConfig.getOrDefault("jco.server.progid", "");
                    String serverKey = gwhost + "|" + gwserv + "|" + progid;
                    if (serverRegistry.containsKey(serverKey)) {
                        server = serverRegistry.get(serverKey);
                    } else {
                        if (!advancedDestinationConfig.isEmpty()) {
                            String destinationName = advancedServerConfig.
                                    containsKey(SAPServerDataProvider.JCO_REP_DEST) ?
                                    advancedServerConfig.get(SAPServerDataProvider.JCO_REP_DEST) :
                                    serverName.getValue();
                            SAPDestinationDataProvider dp = SAPDestinationDataProvider.getInstance();
                            dp.addAdvancedDestinationConfig(advancedDestinationConfig, destinationName);
                            SAPDestinationDataProvider.registerIfAbsent();
                        }
                        SAPServerDataProvider sp = SAPServerDataProvider.getInstance();
                        sp.addAdvancedServerConfig(advancedServerConfig, serverName.getValue());
                        SAPServerDataProvider.registerIfAbsent();
                        server = JCoIDoc.getServer(serverName.getValue());
                        serverRegistry.put(serverKey, server);
                    }
                } else {
                    throw new RuntimeException("Provided a empty advanced configuration for server");
                }
            }
            listenerBObject.addNativeData(SAPConstants.JCO_SERVER, server);
            listenerBObject.addNativeData(SAPConstants.IS_SERVICE_ATTACHED, false);
            listenerBObject.addNativeData(SAPConstants.IS_STARTED, false);
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
        JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
        if (server == null) {
            return SAPErrorCreator.createError("Server start failed: listener is not initialized.");
        }
        boolean isStarted = (boolean) client.getNativeData(SAPConstants.IS_STARTED);
        if (isStarted) {
            return SAPErrorCreator.createError("Server start failed: listener is already started.");
        }
        try {
            server.start();
            client.addNativeData(SAPConstants.IS_STARTED, true);
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
        JCoIDocServer server = (JCoIDocServer) client.getNativeData(SAPConstants.JCO_SERVER);
        if (server == null) {
            return null;
        }
        boolean isStarted = (boolean) client.getNativeData(SAPConstants.IS_STARTED);
        if (!isStarted) {
            return null;
        }
        try {
            server.stop();
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("already stopped")) {
                logger.debug("Server was already stopped.");
            } else {
                logger.error("Server stop failed.");
                return SAPErrorCreator.createError("Server stop failed.", e);
            }
        }
        // JCo's graceful stop is asynchronous: block until the server fully leaves
        // the STOPPING state so that the next start() call on a reused server succeeds.
        long deadline = System.currentTimeMillis() + 15_000;
        while (server.getState() == JCoServerState.STOPPING
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        client.addNativeData(SAPConstants.IS_STARTED, false);
        return null;
    }
}

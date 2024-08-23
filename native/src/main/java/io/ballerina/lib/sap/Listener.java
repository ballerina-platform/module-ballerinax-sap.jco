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

public class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);
    private static boolean isServiceAttached = false;
    private static String jcoServerName;

    public static Object init(BObject listenerBObject, BMap<BString, Object> serverDataConfig,
                              BString serverName) {
        try {
            SAPServerDataProvider dp = new SAPServerDataProvider();
            dp.addServerData(serverDataConfig, serverName);
            com.sap.conn.jco.ext.Environment.registerServerDataProvider(dp);
            JCoIDocServer server = JCoIDoc.getServer(serverName.getValue());
            listenerBObject.addNativeData(SAPConstants.JCO_SERVER, server);
            jcoServerName = serverName.getValue();
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Throwable e) {
            logger.error("Server initialization failed.");
            return SAPErrorCreator.createError("Server initialization failed.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object attach(Environment environment, BObject listenerBObject, BObject service, Object name) {
        Runtime runtime = environment.getRuntime();
        JCoIDocServer server = (JCoIDocServer) listenerBObject.getNativeData(SAPConstants.JCO_SERVER);
        if (isServiceAttached) {
            return SAPErrorCreator.createError("Service is already attached to the server.");
        }
        try {
            server.setIDocHandlerFactory(new BallerinaIDocHandlerFactory(service, runtime));
            server.setTIDHandler(new BallerinaTidHandler());
            BallerinaThrowableListener listener = new BallerinaThrowableListener();
            server.addServerErrorListener(listener);
            server.addServerExceptionListener(listener);
            isServiceAttached = true;
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
            JCoIDocServer server = JCoIDoc.getServer(jcoServerName);
            listener.addNativeData(SAPConstants.JCO_SERVER, server);
            isServiceAttached = false;
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

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
import com.sap.conn.idoc.IDocXMLProcessor;
import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.idoc.jco.JCoIDocHandler;
import com.sap.conn.idoc.jco.JCoIDocHandlerFactory;
import com.sap.conn.idoc.jco.JCoIDocServer;
import com.sap.conn.idoc.jco.JCoIDocServerContext;
import com.sap.conn.jco.server.JCoServer;
import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerContextInfo;
import com.sap.conn.jco.server.JCoServerErrorListener;
import com.sap.conn.jco.server.JCoServerExceptionListener;
import com.sap.conn.jco.server.JCoServerTIDHandler;
import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.async.StrandMetadata;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.utils.XmlUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static io.ballerina.runtime.api.utils.TypeUtils.getReferredType;

public class IDocDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private final JCoIDocServer server;
    private final BObject service;
    private final Runtime runtime;

    public IDocDispatcher(BObject service, JCoIDocServer server, Runtime runtime) {
        this.server = server;
        this.service = service;
        this.runtime = runtime;

    }

    public void receiveIDoc(BObject listenerObject) {
        try {
            server.setIDocHandlerFactory(new BallerinaIDocHandlerFactory());
            server.setTIDHandler(new BallerinaTidHandler());
            BallerinaThrowableListener listener = new BallerinaThrowableListener();
            server.addServerErrorListener(listener);
            server.addServerExceptionListener(listener);
            @SuppressWarnings("unchecked")
            ArrayList<BObject> startedServices =
                    (ArrayList<BObject>) listenerObject.getNativeData(SAPConstants.JCO_STARTED_SERVICES);
            startedServices.add(service);
        } catch (Throwable e) {
            logger.error("Error while processing IDoc", e);
        }
    }

    public void invokeOnIDoc(Callback callback, Object... args) {
        Module module = ModuleUtils.getModule();
        StrandMetadata metadata = new StrandMetadata(
                module.getOrg(), module.getName(), module.getMajorVersion(), SAPConstants.ON_IDOC);
        ObjectType serviceType = (ObjectType) getReferredType(TypeUtils.getType(service));
        if (serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_IDOC)) {
            runtime.invokeMethodAsyncConcurrently(service, SAPConstants.ON_IDOC, null, metadata, callback, null,
                    PredefinedTypes.TYPE_NULL, args);
        } else {
            runtime.invokeMethodAsyncSequentially(service, SAPConstants.ON_IDOC, null, metadata, callback, null,
                    PredefinedTypes.TYPE_NULL, args);
        }
    }

    public void invokeOnError(Type returnType, Object... args) {
        Module module = ModuleUtils.getModule();
        StrandMetadata metadata = new StrandMetadata(
                module.getOrg(), module.getName(), module.getMajorVersion(), SAPConstants.ON_ERROR);
        ObjectType serviceType = (ObjectType) getReferredType(TypeUtils.getType(service));
        if (serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR)) {
            runtime.invokeMethodAsyncConcurrently(service, SAPConstants.ON_ERROR, null, metadata, null, null,
                    returnType, args);
        } else {
            runtime.invokeMethodAsyncSequentially(service, SAPConstants.ON_ERROR, null, metadata, null, null,
                    returnType, args);
        }
    }

    static class BallerinaTidHandler implements JCoServerTIDHandler {

        public boolean checkTID(JCoServerContext serverCtx, String tid) {
            logger.info("checkTID called for TID=" + tid);
            return true;
        }

        public void confirmTID(JCoServerContext serverCtx, String tid) {
            logger.info("confirmTID called for TID=" + tid);
        }

        public void commit(JCoServerContext serverCtx, String tid) {
            logger.info("commit called for TID=" + tid);
        }

        public void rollback(JCoServerContext serverCtx, String tid) {
            logger.info("rollback called for TID=" + tid);
        }
    }

    static class BallerinaThrowableListener implements JCoServerErrorListener, JCoServerExceptionListener {

        private static final Logger logger = LoggerFactory.getLogger(BallerinaThrowableListener.class);

        @Override
        public void serverErrorOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                        Error error) {
            logger.error("Server error occurred: " + error.getMessage());
        }

        @Override
        public void serverExceptionOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                            Exception e) {
            logger.error("Server exception occurred: " + e.getMessage());
        }
    }

    class BallerinaIDocHandler implements JCoIDocHandler {

        public void handleRequest(JCoServerContext serverCtx, IDocDocumentList idocList) {

            StringWriter stringWriter = new StringWriter();
            try {
                IDocXMLProcessor xmlProcessor =
                        JCoIDoc.getIDocFactory().getIDocXMLProcessor();
                xmlProcessor.render(idocList, stringWriter,
                        IDocXMLProcessor.RENDER_WITH_TABS_AND_CRLF);
                String xmlContent = stringWriter.toString();
                CountDownLatch countDownLatch = new CountDownLatch(1);
                Callback callback = new SAPResourceCallback(countDownLatch);
                try {
                    BXml xmlContentValue = XmlUtils.parse(xmlContent);
                    Object[] args = {xmlContentValue, true};
                    invokeOnIDoc(callback, args);
                    countDownLatch.await();
                } catch (InterruptedException | BError exception) {
                    MethodType onErrorFunction = null;
                    MethodType[] resourceFunctions = ((ObjectType) TypeUtils.getType(service)).getMethods();

                    for (MethodType resourceFunction : resourceFunctions) {
                        if (SAPConstants.ON_ERROR.equals(resourceFunction.getName())) {
                            onErrorFunction = resourceFunction;
                            break;
                        }
                    }

                    Type returnType = onErrorFunction != null ? onErrorFunction.getReturnType() : null;
                    if (returnType == null) {
                        returnType = PredefinedTypes.TYPE_NULL;
                    }
                    Object[] args = new Object[]{
                            (exception instanceof BError) ? exception : SAPErrorCreator.createError(
                                    exception.getMessage(), exception), true
                    };
                    invokeOnError(returnType, args);
                }

            } catch (Throwable thr) {
                logger.error("Error while processing IDoc", thr);
            } finally {
                try {
                    stringWriter.close();
                } catch (IOException e) {
                    logger.error("Error while closing the string writer", e);
                }
            }
        }
    }

    class BallerinaIDocHandlerFactory implements JCoIDocHandlerFactory {

        private final JCoIDocHandler handler = new BallerinaIDocHandler();

        public JCoIDocHandler getIDocHandler(JCoIDocServerContext serverCtx) {
            return handler;
        }
    }
}

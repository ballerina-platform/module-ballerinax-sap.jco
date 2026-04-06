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

package io.ballerina.lib.sap.idoc;

import com.sap.conn.idoc.IDocDocumentList;
import com.sap.conn.idoc.IDocXMLProcessor;
import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.idoc.jco.JCoIDocHandler;
import com.sap.conn.jco.server.JCoServerContext;
import io.ballerina.lib.sap.ModuleUtils;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.lib.sap.SAPResourceCallback;
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
import java.util.concurrent.CountDownLatch;

import static io.ballerina.runtime.api.utils.TypeUtils.getReferredType;

/**
 * JCo IDoc handler that bridges incoming SAP IDoc documents to the Ballerina service layer.
 * When JCo delivers an {@link IDocDocumentList}, this handler serialises it to XML, invokes
 * the Ballerina {@code onReceive} resource function, and waits synchronously for the strand
 * to finish before returning control to the JCo worker thread.
 * If processing fails, the Ballerina {@code onError} remote method is invoked asynchronously.
 */
public class BallerinaIDocHandler implements JCoIDocHandler {
    private static final Logger logger = LoggerFactory.getLogger(BallerinaIDocHandler.class);
    private final BObject service;
    private final Runtime runtime;

    public BallerinaIDocHandler(BObject service, Runtime runtime) {
        this.service = service;
        this.runtime = runtime;
    }

    /**
     * Receives an IDoc document list from JCo, converts it to an XML string, and dispatches it
     * to the Ballerina {@code onReceive} resource function.
     * <p>
     * The calling thread blocks on a {@link java.util.concurrent.CountDownLatch} until the
     * Ballerina strand completes. Any exception that escapes (parse error, interrupted await,
     * or a Ballerina panic surfaced as a {@link BError}) is forwarded to the service's
     * {@code onError} remote method.
     *
     * @param serverCtx JCo server context for the current request (not used directly)
     * @param idocList  the list of IDoc documents delivered by SAP
     */
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
                invokeOnReceive(callback, args);
                countDownLatch.await();
            } catch (InterruptedException | BError exception) {
                if (exception instanceof InterruptedException) {
                    // Restore the interrupt status so that callers up the stack can observe it.
                    Thread.currentThread().interrupt();
                }
                Object[] args = new Object[] {
                        (exception instanceof BError) ? exception : SAPErrorCreator.createError(
                                exception.getMessage(), exception), true
                };
                invokeOnError(args);
            }
        } catch (Throwable thr) {
            logger.error("Error while processing IDoc", thr);
            Object[] args = new Object[] {
                    SAPErrorCreator.createError(thr.getMessage(), thr), true
            };
            invokeOnError(args);
        } finally {
            try {
                stringWriter.close();
            } catch (IOException e) {
                logger.error("Error while closing the string writer", e);
            }
        }
    }

    /**
     * Invokes the Ballerina {@code onReceive} resource function on the attached service.
     * Chooses concurrent or sequential dispatch based on whether the service and the method
     * are both declared {@code isolated}.
     *
     * @param callback the callback that will be notified when the strand completes
     * @param args     the arguments to pass to the resource function (IDoc XML value + a {@code true} sentinel)
     */
    public void invokeOnReceive(Callback callback, Object... args) {
        Module module = ModuleUtils.getModule();
        StrandMetadata metadata = new StrandMetadata(
                module.getOrg(), module.getName(), module.getMajorVersion(), SAPConstants.ON_RECEIVE);
        ObjectType serviceType = (ObjectType) getReferredType(TypeUtils.getType(service));
        if (serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_RECEIVE)) {
            runtime.invokeMethodAsyncConcurrently(service, SAPConstants.ON_RECEIVE, null, metadata, callback,
                    null, PredefinedTypes.TYPE_NULL, args);
        } else {
            runtime.invokeMethodAsyncSequentially(service, SAPConstants.ON_RECEIVE, null, metadata, callback,
                    null, PredefinedTypes.TYPE_NULL, args);
        }
    }

    /**
     * Invokes the Ballerina {@code onError} remote method on the attached service if it exists.
     * The invocation is fire-and-forget (no callback): errors that occur during error handling
     * are not propagated further.
     * <p>
     * If the service does not declare an {@code onError} method the runtime call will fail
     * silently; the return type defaults to {@code nil} so no type mismatch is raised.
     *
     * @param args the arguments to pass (Ballerina {@code Error} value + a {@code true} sentinel)
     */
    public void invokeOnError(Object... args) {
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
        Module module = ModuleUtils.getModule();
        StrandMetadata metadata = new StrandMetadata(
                module.getOrg(), module.getName(), module.getMajorVersion(), SAPConstants.ON_ERROR);
        ObjectType serviceType = (ObjectType) getReferredType(TypeUtils.getType(service));
        if (serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR)) {
            runtime.invokeMethodAsyncConcurrently(service, SAPConstants.ON_ERROR, null, metadata, null,
                    null, returnType, args);
        } else {
            runtime.invokeMethodAsyncSequentially(service, SAPConstants.ON_ERROR, null, metadata, null,
                    null, returnType, args);
        }
    }
}

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
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.concurrent.StrandMetadata;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.utils.XmlUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;


/**
 * JCo IDoc handler that bridges incoming SAP IDoc documents to the Ballerina service layer.
 * When JCo delivers an {@link IDocDocumentList}, this handler serialises it to XML, invokes
 * the Ballerina {@code onReceive} resource function, and waits synchronously for the strand
 * to finish before returning control to the JCo worker thread.
 * <p>
 * Framework faults that prevent {@code onReceive} from being dispatched — such as IDoc XML
 * rendering or parsing failures — are routed to the service's {@code onError} remote method.
 * Errors returned or thrown from {@code onReceive} itself are logged and <em>not</em>
 * dispatched to {@code onError}: IDoc delivery is fire-and-forget and SAP has no return
 * channel for per-call errors.
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
     * Framework faults during rendering or parsing are forwarded to the service's
     * {@code onError} remote method. Errors returned or panics thrown from {@code onReceive}
     * itself are logged only.
     *
     * @param serverCtx JCo server context for the current request (not used directly)
     * @param idocList  the list of IDoc documents delivered by SAP
     */
    public void handleRequest(JCoServerContext serverCtx, IDocDocumentList idocList) {
        StringWriter stringWriter = new StringWriter();
        try {
            // (1) Pre-dispatch: render and parse IDoc XML. Framework fault — route to onError.
            BXml xmlContentValue;
            try {
                IDocXMLProcessor xmlProcessor = JCoIDoc.getIDocFactory().getIDocXMLProcessor();
                xmlProcessor.render(idocList, stringWriter, IDocXMLProcessor.RENDER_WITH_TABS_AND_CRLF);
                xmlContentValue = XmlUtils.parse(stringWriter.toString());
            } catch (Throwable thr) {
                BError err = (thr instanceof BError bErr)
                        ? SAPErrorCreator.createIDocError("IDoc rendering failed.", bErr)
                        : SAPErrorCreator.createIDocError("IDoc rendering failed.", thr);
                invokeOnError(new Object[]{err, true});
                return;
            }

            // (2) Dispatch: invoke onReceive. User-method errors are NOT routed to onError.
            Object result;
            try {
                result = invokeOnReceive(xmlContentValue);
            } catch (Throwable thr) {
                logger.error("onReceive() threw an unexpected error", thr);
                return;
            }
            if (result instanceof BError returnedError) {
                logger.error("onReceive() returned an error", returnedError);
            }
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
     * Uses concurrent or sequential dispatch based on whether the service and the method
     * are both declared {@code isolated}.
     *
     * @param args the arguments to pass to the resource function (IDoc XML value)
     * @return the return value from the Ballerina method (may be a {@link BError})
     */
    public Object invokeOnReceive(Object... args) {
        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_RECEIVE);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, Map.of());
        return runtime.callMethod(service, SAPConstants.ON_RECEIVE, metadata, args);
    }

    /**
     * Invokes the Ballerina {@code onError} remote method on the attached service if it exists.
     * Errors that occur during error handling are not propagated further.
     * <p>
     * If the service does not declare an {@code onError} method the call is skipped.
     *
     * @param args the arguments to pass (Ballerina {@code Error} value)
     */
    public void invokeOnError(Object... args) {
        MethodType onErrorFunction = null;
        MethodType[] resourceFunctions = ((ObjectType) TypeUtils.getImpliedType(service.getOriginalType()))
                .getMethods();

        for (MethodType resourceFunction : resourceFunctions) {
            if (SAPConstants.ON_ERROR.equals(resourceFunction.getName())) {
                onErrorFunction = resourceFunction;
                break;
            }
        }

        if (onErrorFunction == null) {
            BError bError = (BError) args[0];
            logger.error("No onError method found on service; dropping error", bError);
            return;
        }

        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, Map.of());
        try {
            Object result = runtime.callMethod(service, SAPConstants.ON_ERROR, metadata, args);
            if (result instanceof BError onErrorResult) {
                logger.error("onError handler returned an error", onErrorResult);
            }
        } catch (Throwable thr) {
            logger.error("onError handler threw an unexpected error; suppressing to avoid re-entry.", thr);
        }
    }
}

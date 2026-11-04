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

/**
 * JCo IDoc handler that bridges incoming SAP IDoc documents to the Ballerina service layer.
 * When JCo delivers an {@link IDocDocumentList}, this handler serialises it to XML, invokes
 * the Ballerina {@code onReceive} remote method, and waits synchronously for the strand
 * to finish before returning control to the JCo worker thread. SAP waits for this method to
 * return before the transactional RFC (tRFC) call that carries the IDoc completes.
 * <p>
 * Returning normally acknowledges the IDoc: JCo commits the transaction ID (TID) and SAP marks
 * the IDoc as delivered. Throwing rejects it: JCo fails the tRFC call, rolls back the TID, and
 * the call stays in error on the SAP side (SM58) so the IDoc can be reprocessed. This handler
 * rejects the IDoc in every case where it was not processed:
 * <ul>
 *   <li>Framework faults that prevent {@code onReceive} from being dispatched, such as IDoc XML
 *       rendering or parsing failures, are routed to the service's {@code onError} remote
 *       method for observability and then rejected.</li>
 *   <li>An error returned from, or a panic raised in, {@code onReceive} is logged and rejected.
 *       It is <em>not</em> dispatched to {@code onError}, since the application has full control
 *       of error handling inside {@code onReceive}: handle the failure there and return
 *       {@code ()} to acknowledge the IDoc anyway.</li>
 * </ul>
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
     * Receives an IDoc document list from JCo, converts it to an XML value, and dispatches it
     * to the Ballerina {@code onReceive} remote method.
     * <p>
     * Framework faults during rendering or parsing are forwarded to the service's
     * {@code onError} remote method and then rejected. Errors returned or panics thrown from
     * {@code onReceive} itself are logged and rejected without invoking {@code onError}.
     *
     * @param serverCtx JCo server context for the current request (not used directly)
     * @param idocList  the list of IDoc documents delivered by SAP
     * @throws IDocRejectedException if the IDoc could not be rendered, or if {@code onReceive}
     *                               returned an error or panicked, so that JCo fails the tRFC
     *                               call and rolls back the TID
     */
    public void handleRequest(JCoServerContext serverCtx, IDocDocumentList idocList) {
        // (1) Pre-dispatch: render and parse IDoc XML. Framework fault — route to onError, then reject.
        BXml xmlContentValue;
        try {
            xmlContentValue = renderIDoc(idocList);
        } catch (Throwable thr) {
            String causeMsg = thr.getMessage() != null ? thr.getMessage() : thr.getClass().getName();
            invokeOnError(createRenderingError(thr));
            throw new IDocRejectedException("IDoc rendering failed: " + causeMsg, thr);
        }

        // (2) Dispatch: invoke onReceive. User-method errors are NOT routed to onError, but they
        // still reject the IDoc so that SAP keeps it for reprocessing.
        Object result;
        try {
            result = invokeOnReceive(xmlContentValue);
        } catch (Throwable thr) {
            String causeMsg = thr.getMessage() != null ? thr.getMessage() : "onReceive() panicked";
            logger.error("onReceive() threw an unexpected error; rejecting the IDoc so SAP can reprocess it", thr);
            throw new IDocRejectedException("onReceive() panicked: " + causeMsg, thr);
        }
        if (result instanceof BError returnedError) {
            logger.warn("onReceive() returned an error; rejecting the IDoc so SAP can reprocess it: {}",
                    returnedError.getMessage());
            throw new IDocRejectedException("onReceive() returned an error: " + returnedError.getMessage());
        }
    }

    /**
     * Wraps a rendering or parsing failure in a Ballerina {@code IDocError} for {@code onError}.
     * A Ballerina error cause is attached as-is; any other throwable is converted first.
     *
     * @param thr the failure raised while rendering or parsing the IDoc
     * @return the {@code IDocError} to dispatch to {@code onError}
     */
    protected BError createRenderingError(Throwable thr) {
        return (thr instanceof BError bErr)
                ? SAPErrorCreator.createIDocError("IDoc rendering failed.", bErr)
                : SAPErrorCreator.createIDocError("IDoc rendering failed.", thr);
    }

    /**
     * Renders the IDoc document list to XML and parses it into a Ballerina {@code xml} value.
     *
     * @param idocList the list of IDoc documents delivered by SAP
     * @return the IDoc as a Ballerina {@code xml} value
     * @throws IOException if the XML could not be written
     */
    protected BXml renderIDoc(IDocDocumentList idocList) throws IOException {
        try (StringWriter stringWriter = new StringWriter()) {
            IDocXMLProcessor xmlProcessor = JCoIDoc.getIDocFactory().getIDocXMLProcessor();
            xmlProcessor.render(idocList, stringWriter, IDocXMLProcessor.RENDER_WITH_TABS_AND_CRLF);
            return XmlUtils.parse(stringWriter.toString());
        }
    }

    /**
     * Invokes the Ballerina {@code onReceive} remote method on the attached service.
     * Uses concurrent or sequential dispatch based on whether the service and the method
     * are both declared {@code isolated}.
     *
     * @param args the arguments to pass to the remote method (IDoc XML value)
     * @return the return value from the Ballerina method (may be a {@link BError})
     */
    public Object invokeOnReceive(Object... args) {
        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_RECEIVE);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, null);
        return runtime.callMethod(service, SAPConstants.ON_RECEIVE, metadata, args);
    }

    /**
     * Invokes the Ballerina {@code onError} remote method on the attached service if it exists.
     * Errors that occur during error handling are not propagated further.
     * <p>
     * If the service does not declare an {@code onError} method the call is skipped.
     *
     * @param error the Ballerina {@code Error} to dispatch
     */
    public void invokeOnError(BError error) {
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
            logger.error("No onError method found on service; dropping error", error);
            return;
        }

        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, null);
        try {
            Object result = runtime.callMethod(service, SAPConstants.ON_ERROR, metadata, new Object[]{error});
            if (result instanceof BError onErrorResult) {
                logger.error("onError handler returned an error", onErrorResult);
            }
        } catch (Throwable thr) {
            logger.error("onError handler threw an unexpected error; suppressing to avoid re-entry.", thr);
        }
    }
}

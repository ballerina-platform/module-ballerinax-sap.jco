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

import com.sap.conn.jco.server.JCoServer;
import com.sap.conn.jco.server.JCoServerContextInfo;
import com.sap.conn.jco.server.JCoServerErrorListener;
import com.sap.conn.jco.server.JCoServerExceptionListener;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.lib.sap.SAPErrorCreator;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.concurrent.StrandMetadata;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Dispatches JCo server-level errors and exceptions to the {@code onError()} handler of every
 * attached Ballerina service.
 * <p>
 * Implements both {@link JCoServerErrorListener} (for {@link Error}-level JVM problems) and
 * {@link JCoServerExceptionListener} (for checked/runtime exceptions raised by the JCo server
 * framework). Server errors are gateway-level events (not tied to a specific IDoc or RFC call),
 * so all currently attached services ({@code IDocService}, {@code RfcService}) are notified.
 * <p>
 * <strong>Async gateway connectivity:</strong> {@code Listener.start()} returns immediately
 * after submitting the server to JCo's scheduler. Gateway connectivity is established in the
 * background. If the gateway is unreachable, JCo retries automatically and calls
 * {@link #serverExceptionOccurred} on every failed attempt. This listener forwards each such
 * failure to the service's {@code onError()} handler, which is the application's primary signal
 * for gateway connectivity problems. When the gateway becomes reachable again, JCo reconnects
 * silently and the exceptions stop.
 */
public class BallerinaThrowableListener implements JCoServerErrorListener, JCoServerExceptionListener {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaThrowableListener.class);

    private final Runtime runtime;
    private final BObject idocService;
    private final BObject rfcService;

    /**
     * Creates a listener that will dispatch server errors to the given services.
     * Either service may be {@code null} if that type is not currently attached.
     *
     * @param runtime     the Ballerina runtime used to invoke service methods
     * @param idocService the attached {@code IDocService}, or {@code null} if none
     * @param rfcService  the attached {@code RfcService}, or {@code null} if none
     */
    public BallerinaThrowableListener(Runtime runtime, BObject idocService, BObject rfcService) {
        this.runtime = runtime;
        this.idocService = idocService;
        this.rfcService = rfcService;
    }

    /**
     * Called by JCo when a {@link Error}-level throwable is raised on the server.
     * Wraps the error as a Ballerina {@code ExecutionError} and dispatches it to every
     * attached service's {@code onError()} handler.
     *
     * @param jCoServer            the server that raised the error
     * @param s                    a session/connection identifier string supplied by JCo
     * @param jCoServerContextInfo additional context about the server state
     * @param error                the JVM {@link Error} that was raised
     */
    @Override
    public void serverErrorOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                    Error error) {
        logger.error("Server error occurred: {}", error.getMessage());
        BError bError = SAPErrorCreator.fromExecutionThrowable("Server error occurred.", error);
        dispatchOnError(bError);
    }

    /**
     * Called by JCo when a checked or runtime {@link Exception} is raised on the server.
     * Wraps the exception as a Ballerina {@code ExecutionError} and dispatches it to every
     * attached service's {@code onError()} handler.
     *
     * @param jCoServer            the server that raised the exception
     * @param s                    a session/connection identifier string supplied by JCo
     * @param jCoServerContextInfo additional context about the server state
     * @param e                    the exception that was raised
     */
    @Override
    public void serverExceptionOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                        Exception e) {
        logger.error("Server exception occurred: {}", e.getMessage());
        BError bError = SAPErrorCreator.fromExecutionThrowable("Server exception occurred.", e);
        dispatchOnError(bError);
    }

    /**
     * Invokes the Ballerina {@code onError()} method on each attached service.
     *
     * @param bError the Ballerina error to dispatch
     */
    private void dispatchOnError(BError bError) {
        if (idocService != null) {
            invokeOnError(idocService, bError);
        }
        if (rfcService != null) {
            invokeOnError(rfcService, bError);
        }
    }

    /**
     * Invokes the {@code onError()} method on the given service. Errors returned or thrown by
     * the handler are logged and suppressed to avoid re-entry.
     *
     * @param service the Ballerina service object
     * @param bError  the error to pass to the handler
     */
    private void invokeOnError(BObject service, BError bError) {
        ObjectType serviceType = (ObjectType) TypeUtils.getImpliedType(service.getOriginalType());
        MethodType onErrorMethod = null;
        for (MethodType method : serviceType.getMethods()) {
            if (SAPConstants.ON_ERROR.equals(method.getName())) {
                onErrorMethod = method;
                break;
            }
        }
        if (onErrorMethod == null) {
            logger.error("No onError method found on service {}; dropping error: {}",
                    serviceType.getName(), bError.getMessage());
            return;
        }
        boolean isConcurrent = serviceType.isIsolated() && serviceType.isIsolated(SAPConstants.ON_ERROR);
        StrandMetadata metadata = new StrandMetadata(isConcurrent, Map.of());
        try {
            Object result = runtime.callMethod(service, SAPConstants.ON_ERROR, metadata,
                    new Object[]{bError});
            if (result instanceof BError onErrorResult) {
                logger.error("onError handler on {} returned an error; suppressing.",
                        serviceType.getName(), onErrorResult);
            }
        } catch (Throwable thr) {
            logger.error("onError handler on {} threw an unexpected error; suppressing.",
                    serviceType.getName(), thr);
        }
    }
}

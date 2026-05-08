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

package io.ballerina.lib.sap.rfc;

import com.sap.conn.jco.server.JCoServer;
import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerFunctionHandler;
import com.sap.conn.jco.server.JCoServerFunctionHandlerFactory;
import com.sap.conn.jco.server.JCoServerState;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.values.BObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that supplies a single shared {@link BallerinaRfcHandler} instance for all incoming
 * RFC calls on a given JCo server connection.
 * <p>
 * A single handler is reused for every call to {@link #getCallHandler} because the handler is
 * stateless with respect to individual requests — all request state is captured on the stack
 * inside {@link BallerinaRfcHandler#handleRequest}.
 */
public class BallerinaRfcHandlerFactory implements JCoServerFunctionHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaRfcHandlerFactory.class);

    private final JCoServerFunctionHandler handler;

    /**
     * Creates a factory that wraps the given Ballerina service and runtime in a single
     * {@link BallerinaRfcHandler}.
     *
     * @param service the Ballerina {@code RfcService} object to dispatch RFC calls to
     * @param runtime the Ballerina runtime used to invoke service methods
     */
    public BallerinaRfcHandlerFactory(BObject service, Runtime runtime) {
        this.handler = new BallerinaRfcHandler(service, runtime);
    }

    /**
     * Returns the shared {@link BallerinaRfcHandler} for the given server context and function
     * name. The same handler instance is returned for every call; JCo is responsible for ensuring
     * thread safety by serialising calls per TID for tRFC/qRFC.
     *
     * @param serverCtx    the current JCo server context (not used here)
     * @param functionName the name of the RFC function module being called (forwarded to
     *                     {@code onCall()} by the handler at request time)
     * @return the shared RFC handler instance
     */
    @Override
    public JCoServerFunctionHandler getCallHandler(JCoServerContext serverCtx, String functionName) {
        return handler;
    }

    /**
     * Called by JCo when a server session is closed (after tRFC commit or session timeout).
     * No cleanup is required because the handler holds no per-session state.
     *
     * @param serverCtx the server context for the closed session
     * @param tid       the Transaction ID associated with the session
     * @param isCommit  {@code true} if the session ended with a commit; {@code false} on rollback
     */
    @Override
    public void sessionClosed(JCoServerContext serverCtx, String tid, boolean isCommit) {
        logger.debug("RFC session closed: tid={}, commit={}", tid, isCommit);
    }

    /**
     * Called by JCo when the server transitions between lifecycle states.
     * No action is required.
     *
     * @param jCoServer the server whose state changed
     * @param oldState  the previous state
     * @param newState  the new state
     */
    public void serverStateChanged(JCoServer jCoServer, JCoServerState oldState, JCoServerState newState) {
        logger.debug("RFC server state changed: {} -> {}", oldState, newState);
    }
}

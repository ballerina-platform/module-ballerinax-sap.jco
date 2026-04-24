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

import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.server.JCoServer;
import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerFunctionHandler;
import com.sap.conn.jco.server.JCoServerFunctionHandlerFactory;
import com.sap.conn.jco.server.JCoServerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A no-op {@link JCoServerFunctionHandlerFactory} installed on a {@link com.sap.conn.idoc.jco.JCoIDocServer}
 * when an {@code RfcService} is detached.
 * <p>
 * Replaces the live {@link BallerinaRfcHandlerFactory} so that any RFC calls arriving after
 * detach are silently discarded rather than dispatched to a service that is no longer registered.
 * When an {@code RfcService} is re-attached via {@code Listener.attach()}, the real
 * {@link BallerinaRfcHandlerFactory} replaces this stub.
 */
public class NoOpRfcHandlerFactory implements JCoServerFunctionHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(NoOpRfcHandlerFactory.class);

    private static final JCoServerFunctionHandler NO_OP_HANDLER = new JCoServerFunctionHandler() {
        @Override
        public void handleRequest(JCoServerContext serverCtx, JCoFunction function) {
            logger.debug("NoOpRfcHandler: received RFC call '{}' but no RfcService is attached; discarding.",
                    function.getName());
        }
    };

    @Override
    public JCoServerFunctionHandler getCallHandler(JCoServerContext serverCtx, String functionName) {
        return NO_OP_HANDLER;
    }

    @Override
    public void sessionClosed(JCoServerContext serverCtx, String tid, boolean isCommit) {
        // No session state to clean up.
    }

    public void serverStateChanged(JCoServer jCoServer, JCoServerState oldState, JCoServerState newState) {
        // No state-change handling required.
    }
}

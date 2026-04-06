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

import com.sap.conn.idoc.jco.JCoIDocHandler;
import com.sap.conn.idoc.jco.JCoIDocHandlerFactory;
import com.sap.conn.idoc.jco.JCoIDocServerContext;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.values.BObject;

/**
 * Factory that supplies a single shared {@link BallerinaIDocHandler} instance for all incoming
 * IDoc deliveries on a given JCo server connection.
 * <p>
 * A single handler is reused for every call to {@link #getIDocHandler} because the handler
 * is stateless with respect to individual requests — all request state is captured on the
 * stack inside {@link BallerinaIDocHandler#handleRequest}.
 */
public class BallerinaIDocHandlerFactory implements JCoIDocHandlerFactory {

    private final JCoIDocHandler handler;

    /**
     * Creates a factory that wraps the given Ballerina service and runtime in a single
     * {@link BallerinaIDocHandler}.
     *
     * @param service the Ballerina service object to dispatch IDoc events to
     * @param runtime the Ballerina runtime used to invoke service methods asynchronously
     */
    public BallerinaIDocHandlerFactory(BObject service, Runtime runtime) {
        this.handler = new BallerinaIDocHandler(service, runtime);
    }

    /**
     * Returns the shared {@link BallerinaIDocHandler} for the given server context.
     * The same handler instance is returned for every context; JCo is responsible for
     * ensuring thread safety by serialising calls per TID.
     *
     * @param serverCtx the current JCo IDoc server context (not used)
     * @return the shared handler instance
     */
    public JCoIDocHandler getIDocHandler(JCoIDocServerContext serverCtx) {
        return handler;
    }
}

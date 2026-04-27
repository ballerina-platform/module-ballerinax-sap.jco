/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org).
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
import com.sap.conn.idoc.jco.JCoIDocHandler;
import com.sap.conn.idoc.jco.JCoIDocHandlerFactory;
import com.sap.conn.idoc.jco.JCoIDocServerContext;
import com.sap.conn.jco.server.JCoServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A no-op {@link JCoIDocHandlerFactory} installed on every {@link com.sap.conn.idoc.jco.JCoIDocServer}
 * at initialisation time.
 * <p>
 * {@code JCoIDocServer} requires an {@code IDocHandlerFactory} to be present before the server
 * can start, even when only an {@code RfcService} is attached and IDoc delivery is not expected.
 * This factory satisfies that requirement by supplying a handler that silently discards any IDoc
 * documents it receives. When an {@code IDocService} is later attached via
 * {@code Listener.attach()}, the real {@link BallerinaIDocHandlerFactory} replaces this stub.
 */
public class NoOpIDocHandlerFactory implements JCoIDocHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(NoOpIDocHandlerFactory.class);

    private static final JCoIDocHandler NO_OP_HANDLER = new JCoIDocHandler() {
        @Override
        public void handleRequest(JCoServerContext serverCtx, IDocDocumentList idocList) {
            logger.debug("NoOpIDocHandler: received IDoc but no IDocService is attached; discarding.");
        }
    };

    @Override
    public JCoIDocHandler getIDocHandler(JCoIDocServerContext serverCtx) {
        return NO_OP_HANDLER;
    }
}

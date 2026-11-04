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
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BXml;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the acknowledge/reject contract of {@link BallerinaIDocHandler#handleRequest}:
 * the IDoc is acknowledged only when {@code onReceive} returns {@code ()}, and every other
 * outcome throws so that JCo fails the tRFC call and rolls back the TID.
 */
public class BallerinaIDocHandlerTest {

    private static final BXml IDOC_XML = io.ballerina.runtime.api.utils.XmlUtils.parse("<ORDERS05/>");

    /**
     * Test double that replaces the JCo rendering step and the Ballerina runtime dispatch with
     * scripted outcomes, and records what was dispatched to {@code onError}.
     */
    private static final class ScriptedHandler extends BallerinaIDocHandler {
        private final Throwable renderFailure;
        private final Object onReceiveResult;
        private final Throwable onReceivePanic;
        final List<BError> onErrorCalls = new ArrayList<>();
        final List<Object[]> onReceiveCalls = new ArrayList<>();
        final List<Throwable> renderingErrorCauses = new ArrayList<>();

        ScriptedHandler(Throwable renderFailure, Object onReceiveResult, Throwable onReceivePanic) {
            super(null, null);
            this.renderFailure = renderFailure;
            this.onReceiveResult = onReceiveResult;
            this.onReceivePanic = onReceivePanic;
        }

        @Override
        protected BXml renderIDoc(IDocDocumentList idocList) throws IOException {
            if (renderFailure instanceof IOException ioe) {
                throw ioe;
            }
            if (renderFailure instanceof RuntimeException re) {
                throw re;
            }
            if (renderFailure instanceof Error err) {
                throw err;
            }
            return IDOC_XML;
        }

        @Override
        public Object invokeOnReceive(Object... args) {
            onReceiveCalls.add(args);
            if (onReceivePanic instanceof RuntimeException re) {
                throw re;
            }
            if (onReceivePanic instanceof Error err) {
                throw err;
            }
            return onReceiveResult;
        }

        @Override
        public void invokeOnError(BError error) {
            onErrorCalls.add(error);
        }

        // SAPErrorCreator needs the Ballerina module to be initialised, which it is not in a unit
        // test; record the cause and hand back a plain error instead.
        @Override
        protected BError createRenderingError(Throwable thr) {
            renderingErrorCauses.add(thr);
            return ballerinaError("IDoc rendering failed.");
        }
    }

    private static BError ballerinaError(String message) {
        return ErrorCreator.createError(StringUtils.fromString(message));
    }

    @Test
    public void onReceiveReturningNilAcknowledgesTheIDoc() {
        ScriptedHandler handler = new ScriptedHandler(null, null, null);
        handler.handleRequest(null, null);
        Assert.assertEquals(handler.onReceiveCalls.size(), 1, "onReceive should be dispatched once");
        Assert.assertSame(handler.onReceiveCalls.get(0)[0], IDOC_XML, "onReceive should get the rendered IDoc");
        Assert.assertTrue(handler.onErrorCalls.isEmpty(), "onError must not be invoked on success");
    }

    @Test
    public void onReceiveReturningErrorRejectsTheIDocWithoutInvokingOnError() {
        ScriptedHandler handler = new ScriptedHandler(null, ballerinaError("downstream unavailable"), null);
        IDocRejectedException rejected = Assert.expectThrows(IDocRejectedException.class,
                () -> handler.handleRequest(null, null));
        Assert.assertTrue(rejected.getMessage().contains("downstream unavailable"),
                "rejection message should carry the onReceive error: " + rejected.getMessage());
        Assert.assertEquals(handler.onReceiveCalls.size(), 1);
        Assert.assertTrue(handler.onErrorCalls.isEmpty(), "onReceive errors are not routed to onError");
    }

    @Test
    public void onReceivePanicRejectsTheIDocWithoutInvokingOnError() {
        BError panic = ballerinaError("index out of range");
        ScriptedHandler handler = new ScriptedHandler(null, null, panic);
        IDocRejectedException rejected = Assert.expectThrows(IDocRejectedException.class,
                () -> handler.handleRequest(null, null));
        Assert.assertSame(rejected.getCause(), panic, "rejection should carry the panic as its cause");
        Assert.assertTrue(handler.onErrorCalls.isEmpty(), "onReceive panics are not routed to onError");
    }

    @Test
    public void renderingFailureInvokesOnErrorAndRejectsTheIDoc() {
        IOException renderFailure = new IOException("invalid XML character in segment E1EDK01");
        ScriptedHandler handler = new ScriptedHandler(renderFailure, null, null);
        IDocRejectedException rejected = Assert.expectThrows(IDocRejectedException.class,
                () -> handler.handleRequest(null, null));
        Assert.assertSame(rejected.getCause(), renderFailure);
        Assert.assertTrue(rejected.getMessage().contains("IDoc rendering failed"));
        Assert.assertTrue(handler.onReceiveCalls.isEmpty(), "onReceive must not be dispatched when rendering fails");
        Assert.assertEquals(handler.onErrorCalls.size(), 1, "rendering failures are routed to onError");
        Assert.assertEquals(handler.onErrorCalls.get(0).getMessage(), "IDoc rendering failed.");
        Assert.assertEquals(handler.renderingErrorCauses, List.of(renderFailure),
                "the original failure should be the cause of the IDocError sent to onError");
    }

    @Test
    public void parseFailureRaisedAsBallerinaErrorIsAlsoRejected() {
        BError parseFailure = ballerinaError("xml parse error");
        ScriptedHandler handler = new ScriptedHandler(parseFailure, null, null);
        IDocRejectedException rejected = Assert.expectThrows(IDocRejectedException.class,
                () -> handler.handleRequest(null, null));
        Assert.assertSame(rejected.getCause(), parseFailure);
        Assert.assertEquals(handler.onErrorCalls.size(), 1);
        Assert.assertTrue(handler.onReceiveCalls.isEmpty());
    }
}

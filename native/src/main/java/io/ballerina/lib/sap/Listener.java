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

import com.sap.conn.idoc.jco.JCoIDoc;
import com.sap.conn.idoc.jco.JCoIDocServer;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.server.JCoServerState;
import io.ballerina.lib.sap.dataproviders.SAPDestinationDataProvider;
import io.ballerina.lib.sap.dataproviders.SAPServerDataProvider;
import io.ballerina.lib.sap.idoc.BallerinaIDocHandlerFactory;
import io.ballerina.lib.sap.idoc.BallerinaThrowableListener;
import io.ballerina.lib.sap.idoc.BallerinaTidHandler;
import io.ballerina.lib.sap.idoc.NoOpIDocHandlerFactory;
import io.ballerina.lib.sap.rfc.BallerinaRfcHandlerFactory;
import io.ballerina.lib.sap.rfc.NoOpRfcHandlerFactory;
import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ServiceType;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native function implementations for the Ballerina SAP JCo {@code Listener} object.
 * Each public static method in this class corresponds to a Ballerina extern function and is
 * invoked by the Ballerina runtime at specific points in the listener lifecycle
 * ({@code init → attach → start → gracefulStop/immediateStop → detach}).
 * <p>
 * A single listener supports attaching at most one {@code IDocService} and one {@code RfcService}
 * simultaneously. Both share the same underlying {@link JCoIDocServer} instance.
 * <p>
 * JCo restricts each JVM to one server per {@code (gwhost, gwserv, progid)} triplet. All
 * {@code Listener} objects with the same triplet share a single {@link ServerEntry} that holds
 * the {@link JCoIDocServer} and all mutable attachment state. Modifications to attachment state
 * are guarded by {@code synchronized(ServerEntry)} to prevent concurrent listeners from
 * overwriting each other's handler factories or service references.
 */
public final class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    // Native data key for the repository destination name stored at init time.
    static final String NATIVE_REPO_DEST = "nativeRepoDest";
    // Native data key for the server-registry lookup key stored at init time.
    private static final String NATIVE_SERVER_KEY = "nativeServerKey";

    /**
     * Holds the shared state for all {@link Listener} instances that resolve to the same
     * {@code (gwhost, gwserv, progid)} triplet. All mutable fields are guarded by
     * {@code synchronized(this)}.
     */
    private static final class ServerEntry {
        final JCoIDocServer server;
        // Guarded by synchronized(this):
        boolean isStarted = false;
        boolean isIDocServiceAttached = false;
        boolean isRfcServiceAttached = false;
        boolean isTidHandlerSet = false;
        BObject idocService = null;
        BObject rfcService = null;
        BallerinaThrowableListener throwableListener = null;
        Runtime runtime = null;
        final String repoDest;

        ServerEntry(JCoIDocServer server, String repoDest) {
            this.server = server;
            this.repoDest = repoDest;
        }
    }

    // JCo allows only one server per (gwhost|gwserv|progid) combination per JVM.
    // We reuse the same JCoIDocServer object for repeated Listener creations with
    // the same connection configuration so that subsequent JCoIDoc.getServer() calls
    // with a new UUID name do not fail with "already used for a running server".
    private static final Map<String, ServerEntry> serverRegistry = new ConcurrentHashMap<>();

    /**
     * Initializes the JCo server from a flat string map of JCo properties.
     * <p>
     * JCo restricts each JVM to a single server per {@code (gwhost, gwserv, progid)} triplet.
     * To honour that constraint this method checks an in-process {@link #serverRegistry} and
     * reuses an existing {@link ServerEntry} when one for the same triplet was already created.
     *
     * @param listenerBObject the Ballerina {@code Listener} object being initialized
     * @param serverConfig    a map holding JCo connection properties
     * @param serverName      a unique name registered with the {@link SAPServerDataProvider}
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object init(BObject listenerBObject, BMap<BString, Object> serverConfig, BString serverName) {
        try {
            JCoIDocServer server;
            String repositoryDestination = null;
            if (!serverConfig.isEmpty()) {
                Map<String, String> advancedServerConfig = new HashMap<>();
                Map<String, String> advancedDestinationConfig = new HashMap<>();
                serverConfig.entrySet().forEach(entry -> {
                    String key = entry.getKey().toString();
                    String value = entry.getValue().toString();
                    if (key.startsWith(SAPConstants.JCO_SERVER_PREFIX)) {
                        advancedServerConfig.put(key, value);
                    } else {
                        advancedDestinationConfig.put(key, value);
                    }
                });
                String gwhost = advancedServerConfig.getOrDefault("jco.server.gwhost", "");
                String gwserv = advancedServerConfig.getOrDefault("jco.server.gwserv", "");
                String progid = advancedServerConfig.getOrDefault("jco.server.progid", "");
                String serverKey = gwhost + "|" + gwserv + "|" + progid;
                repositoryDestination = advancedServerConfig.get(SAPServerDataProvider.JCO_REP_DEST);
                // Register inline destination config before the server registry check so that
                // it is available even when the server object is reused for the same (gwhost, gwserv, progid).
                if (!advancedDestinationConfig.isEmpty()) {
                    String destinationName = (repositoryDestination != null)
                            ? repositoryDestination : serverName.getValue();
                    SAPDestinationDataProvider dp = SAPDestinationDataProvider.getInstance();
                    if (!dp.hasDestination(destinationName)) {
                        dp.addAdvancedDestinationConfig(advancedDestinationConfig, destinationName);
                    }
                    SAPDestinationDataProvider.registerIfAbsent();
                }
                ServerEntry entry = serverRegistry.get(serverKey);
                if (entry != null) {
                    if (!Objects.equals(entry.repoDest, repositoryDestination)) {
                        return SAPErrorCreator.createConfigError(
                                "Server configuration mismatch: repositoryDestination '"
                                + repositoryDestination + "' does not match the existing server's "
                                + "repositoryDestination '" + entry.repoDest + "' for ("
                                + gwhost + ", " + gwserv + ", " + progid + ").");
                    }
                    server = entry.server;
                } else {
                    SAPServerDataProvider sp = SAPServerDataProvider.getInstance();
                    sp.addAdvancedServerConfig(advancedServerConfig, serverName.getValue());
                    SAPServerDataProvider.registerIfAbsent();
                    server = JCoIDoc.getServer(serverName.getValue());
                    // JCoIDocServer requires an IDocHandlerFactory before start() is called,
                    // even when only an RfcService is attached. Install a no-op factory now;
                    // attach() replaces it with BallerinaIDocHandlerFactory when an IDocService
                    // is attached.
                    server.setIDocHandlerFactory(new NoOpIDocHandlerFactory());
                    entry = new ServerEntry(server, repositoryDestination);
                    serverRegistry.put(serverKey, entry);
                }
                listenerBObject.addNativeData(NATIVE_SERVER_KEY, serverKey);
            } else {
                return SAPErrorCreator.createConfigError("Provided an empty advanced configuration for server");
            }
            listenerBObject.addNativeData(SAPConstants.JCO_SERVER, server);
            listenerBObject.addNativeData(NATIVE_REPO_DEST, repositoryDestination);
            return null;
        } catch (JCoException e) {
            logger.error("Destination lookup failed.");
            return SAPErrorCreator.fromJCoException(e);
        } catch (Throwable e) {
            logger.error("Server initialization failed.");
            return SAPErrorCreator.createConfigError("Server initialization failed. " + e.getMessage(), e);
        }
    }

    /**
     * Attaches a Ballerina service to the listener.
     * <p>
     * At most one {@code IDocService} and one {@code RfcService} may be attached at a time across
     * all listeners that share the same {@link JCoIDocServer}. The service type is resolved from
     * the Ballerina object type name. On the first {@code attach()} call the
     * {@link BallerinaTidHandler} and a new {@link BallerinaThrowableListener} are registered on
     * the server. Subsequent calls update the throwable listener with the newly added service.
     * <p>
     * Attachment state is guarded by {@code synchronized(ServerEntry)} so that two listeners
     * initialised with the same {@code (gwhost, gwserv, progid)} cannot overwrite each other's
     * handler factories or service references.
     * <p>
     * Attaching either service type requires {@code repositoryDestination} to be set in the
     * {@code ServerConfig}. The destination must already be registered — either by creating a
     * {@code Client} with a matching {@code destinationId}, or by supplying a
     * {@code DestinationConfig} directly as {@code repositoryDestination} in {@code ServerConfig}
     * (which registers the destination automatically at listener init time).
     *
     * @param environment     the Ballerina runtime environment
     * @param listenerBObject the Ballerina {@code Listener} object
     * @param service         the Ballerina service object; must be an {@code IDocService} or
     *                        {@code RfcService}
     * @param name            optional service path name (unused by the JCo transport)
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object attach(Environment environment, BObject listenerBObject, BObject service, Object name) {
        Runtime runtime = environment.getRuntime();
        JCoIDocServer server = (JCoIDocServer) listenerBObject.getNativeData(SAPConstants.JCO_SERVER);
        String serverKey = (String) listenerBObject.getNativeData(NATIVE_SERVER_KEY);
        ServerEntry entry = serverRegistry.get(serverKey);
        if (entry == null) {
            return SAPErrorCreator.createConfigError("Listener is not properly initialized: server entry not found.");
        }
        ServiceType serviceType = (ServiceType) TypeUtils.getImpliedType(service.getOriginalType());
        boolean hasOnReceive = false;
        boolean hasOnCall = false;
        for (MethodType method : serviceType.getRemoteMethods()) {
            String methodName = method.getName();
            if (SAPConstants.ON_RECEIVE.equals(methodName)) {
                hasOnReceive = true;
            } else if (SAPConstants.ON_CALL.equals(methodName)) {
                hasOnCall = true;
            }
        }
        try {
            if (hasOnReceive && hasOnCall) {
                return SAPErrorCreator.createConfigError(
                        "Ambiguous service type: service declares both 'onReceive' (IDocService) and 'onCall'"
                        + " (RfcService) methods. Implement either IDocService or RfcService, not both.");
            } else if (!hasOnReceive && !hasOnCall) {
                return SAPErrorCreator.createConfigError(
                        "Unsupported service type: must declare either 'onReceive' (IDocService)"
                        + " or 'onCall' (RfcService) as a remote method.");
            }
            synchronized (entry) {
                if (hasOnReceive) {
                    if (entry.isIDocServiceAttached) {
                        return SAPErrorCreator.createConfigError(
                                "An IDocService is already attached to this server.");
                    }
                    String repDestIdoc = (String) listenerBObject.getNativeData(NATIVE_REPO_DEST);
                    if (repDestIdoc == null || repDestIdoc.isEmpty()) {
                        return SAPErrorCreator.createConfigError(
                                "repositoryDestination is required in ServerConfig when attaching an IDocService.");
                    }
                    if (!Objects.equals(repDestIdoc, entry.repoDest)) {
                        return SAPErrorCreator.createConfigError(
                                "repositoryDestination mismatch: listener has '" + repDestIdoc
                                + "' but the shared server was configured with '" + entry.repoDest + "'.");
                    }
                    if (!SAPDestinationDataProvider.getInstance().hasDestination(repDestIdoc)) {
                        return SAPErrorCreator.createConfigError(
                                "The repositoryDestination '" + repDestIdoc + "' has not been registered. "
                                + "Either create a Client with destinationId = \"" + repDestIdoc
                                + "\", or use a DestinationConfig as repositoryDestination in ServerConfig.");
                    }
                    server.setIDocHandlerFactory(new BallerinaIDocHandlerFactory(service, runtime));
                    entry.idocService = service;
                    entry.isIDocServiceAttached = true;
                } else {
                    if (entry.isRfcServiceAttached) {
                        return SAPErrorCreator.createConfigError(
                                "An RfcService is already attached to this server.");
                    }
                    String repDest = (String) listenerBObject.getNativeData(NATIVE_REPO_DEST);
                    if (repDest == null || repDest.isEmpty()) {
                        return SAPErrorCreator.createConfigError(
                                "repositoryDestination is required in ServerConfig when attaching an RfcService.");
                    }
                    if (!Objects.equals(repDest, entry.repoDest)) {
                        return SAPErrorCreator.createConfigError(
                                "repositoryDestination mismatch: listener has '" + repDest
                                + "' but the shared server was configured with '" + entry.repoDest + "'.");
                    }
                    if (!SAPDestinationDataProvider.getInstance().hasDestination(repDest)) {
                        return SAPErrorCreator.createConfigError(
                                "The repositoryDestination '" + repDest + "' has not been registered. "
                                + "Either create a Client with destinationId = \"" + repDest
                                + "\", or use a DestinationConfig as repositoryDestination in ServerConfig.");
                    }
                    // JCoIDocServer extends JCoServer; JCoServerFunctionHandlerFactory extends
                    // JCoServerCallHandlerFactory, so setCallHandlerFactory() accepts it directly.
                    server.setCallHandlerFactory(new BallerinaRfcHandlerFactory(service, runtime));
                    entry.rfcService = service;
                    entry.isRfcServiceAttached = true;
                }
                // Register TID handler once on the first attach(); shared by both service types.
                if (!entry.isTidHandlerSet) {
                    server.setTIDHandler(new BallerinaTidHandler());
                    entry.isTidHandlerSet = true;
                }
                // Update the shared runtime and rebuild the throwable listener with all currently
                // attached services so that server-level errors reach every service's onError() handler.
                entry.runtime = runtime;
                refreshThrowableListener(entry, server);
            }
            return null;
        } catch (Throwable e) {
            logger.error("Server attach failed.");
            return SAPErrorCreator.createConfigError("Server attach failed. " + e.getMessage(), e);
        }
    }

    /**
     * Launches the JCo server and returns immediately.
     * <p>
     * <strong>Gateway connectivity is established asynchronously.</strong>
     * {@link JCoIDocServer#start()} spawns background connection threads that register this
     * server with the SAP gateway. A successful return from this method means the server has
     * been submitted to JCo's internal scheduler — it does <em>not</em> mean the gateway
     * handshake is complete.
     * <p>
     * Connectivity outcomes are delivered through the server error/exception listeners
     * registered at {@code attach()} time ({@link BallerinaThrowableListener}):
     * <ul>
     *   <li><strong>Connection failure</strong> (unreachable host, refused port, etc.) — JCo
     *       retries automatically at a built-in interval and calls
     *       {@link BallerinaThrowableListener#serverExceptionOccurred} on every failed attempt.
     *       The attached service's {@code onError()} handler receives an {@code ExecutionError}
     *       for each retry so the application can log, alert, or implement its own backoff.</li>
     *   <li><strong>Self-healing</strong> — if the gateway becomes reachable after earlier
     *       failures, JCo reconnects without any intervention and stops firing exceptions.</li>
     * </ul>
     * The only synchronous errors this method returns are pre-flight failures: the listener
     * not being initialised, or it already being in the started state.
     *
     * @param client the Ballerina {@code Listener} object
     * @return {@code null} on success, or a {@code ConfigurationError} if the listener is not
     *         initialised or is already running
     */
    public static Object start(BObject client) {
        String serverKey = (String) client.getNativeData(NATIVE_SERVER_KEY);
        if (serverKey == null) {
            return SAPErrorCreator.createConfigError("Server start failed: listener is not initialized.");
        }
        ServerEntry entry = serverRegistry.get(serverKey);
        if (entry == null) {
            return SAPErrorCreator.createConfigError("Server start failed: listener is not initialized.");
        }
        synchronized (entry) {
            if (entry.isStarted) {
                return SAPErrorCreator.createConfigError("Server start failed: listener is already started.");
            }
            try {
                entry.server.start();
                entry.isStarted = true;
            } catch (Throwable e) {
                logger.error("Server start failed.");
                return SAPErrorCreator.createConfigError("Server start failed. " + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Requests a graceful stop of the JCo server. Delegates to {@link #stopServer(BObject)}.
     *
     * @param client the Ballerina {@code Listener} object
     * @return {@code null} on success, or a Ballerina {@code Error} if the stop fails
     */
    public static Object gracefulStop(BObject client) {
        return stopServer(client);
    }

    /**
     * Immediately stops the JCo server. Delegates to {@link #stopServer(BObject)}.
     * <p>
     * JCo exposes a single {@code stop()} method with no abort-in-flight variant, so the
     * behaviour is identical to {@link #gracefulStop(BObject)} at the JCo layer.
     *
     * @param client the Ballerina {@code Listener} object
     * @return {@code null} on success, or a Ballerina {@code Error} if the stop fails
     */
    public static Object immediateStop(BObject client) {
        return stopServer(client);
    }

    /**
     * Stops the JCo server and blocks until it fully leaves the {@link JCoServerState#STOPPING}
     * state (up to 15 seconds), ensuring the shared server instance in {@link #serverRegistry}
     * is ready for a subsequent {@code start()} call.
     *
     * @param client the Ballerina {@code Listener} object
     * @return {@code null} on success, or a Ballerina {@code Error} if the stop fails
     */
    private static Object stopServer(BObject client) {
        String serverKey = (String) client.getNativeData(NATIVE_SERVER_KEY);
        if (serverKey == null) {
            return null;
        }
        ServerEntry entry = serverRegistry.get(serverKey);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!entry.isStarted) {
                return null;
            }
        }
        JCoIDocServer server = entry.server;
        try {
            server.stop();
        } catch (Throwable e) {
            if (server.getState() == JCoServerState.STOPPED) {
                logger.debug("Server was already stopped.");
            } else {
                logger.error("Server stop failed.");
                synchronized (entry) {
                    entry.isStarted = false;
                }
                return SAPErrorCreator.createConfigError("Server stop failed. " + e.getMessage(), e);
            }
        }
        long deadline = System.currentTimeMillis() + 15_000;
        while (server.getState() == JCoServerState.STOPPING && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (server.getState() == JCoServerState.STOPPING) {
            logger.warn("Server did not finish stopping within the deadline; server may still be active.");
            synchronized (entry) {
                entry.isStarted = false;
            }
            return SAPErrorCreator.createConfigError(
                    "Server stop timed out: the server is still in STOPPING state after 15 seconds.");
        }
        synchronized (entry) {
            entry.isStarted = false;
        }
        return null;
    }

    /**
     * Detaches a service from the listener by clearing the corresponding attachment flag and
     * stored service reference in the shared {@link ServerEntry}.
     * <p>
     * The other service type, if attached, continues to operate. The server object is always
     * retained so that services can be re-attached after a detach. Does not stop the underlying
     * server.
     *
     * @param listener the Ballerina {@code Listener} object
     * @param service  the Ballerina service object to detach
     * @return {@code null} on success, or a Ballerina {@code Error} on failure
     */
    public static Object detach(BObject listener, BObject service) {
        try {
            ServiceType serviceType = (ServiceType) TypeUtils.getImpliedType(service.getOriginalType());
            boolean hasOnReceive = false;
            boolean hasOnCall = false;
            for (MethodType method : serviceType.getRemoteMethods()) {
                String methodName = method.getName();
                if (SAPConstants.ON_RECEIVE.equals(methodName)) {
                    hasOnReceive = true;
                } else if (SAPConstants.ON_CALL.equals(methodName)) {
                    hasOnCall = true;
                }
            }
            String serverKey = (String) listener.getNativeData(NATIVE_SERVER_KEY);
            ServerEntry entry = serverRegistry.get(serverKey);
            JCoIDocServer server = (JCoIDocServer) listener.getNativeData(SAPConstants.JCO_SERVER);
            if (entry != null && server != null) {
                synchronized (entry) {
                    if (hasOnReceive) {
                        if (entry.idocService != service) {
                            return SAPErrorCreator.createConfigError(
                                    "Cannot detach IDocService: the provided service is not the currently"
                                    + " attached instance.");
                        }
                        entry.isIDocServiceAttached = false;
                        entry.idocService = null;
                        // Reset to no-op so arriving IDocs are discarded rather than
                        // dispatched to the now-detached service.
                        server.setIDocHandlerFactory(new NoOpIDocHandlerFactory());
                    } else if (hasOnCall) {
                        if (entry.rfcService != service) {
                            return SAPErrorCreator.createConfigError(
                                    "Cannot detach RfcService: the provided service is not the currently"
                                    + " attached instance.");
                        }
                        entry.isRfcServiceAttached = false;
                        entry.rfcService = null;
                        // Reset to no-op so arriving RFC calls are discarded rather than
                        // dispatched to the now-detached service.
                        server.setCallHandlerFactory(new NoOpRfcHandlerFactory());
                    } else {
                        return SAPErrorCreator.createConfigError(
                                "Unsupported service type: expected IDocService or RfcService.");
                    }
                    // Refresh the throwable listener so it no longer dispatches to the detached service.
                    if (entry.runtime != null) {
                        refreshThrowableListener(entry, server);
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("Server detach failed.");
            return SAPErrorCreator.createConfigError("Server detach failed. " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Creates a fresh {@link BallerinaThrowableListener} populated with all currently attached
     * services and registers it on the JCo server so that server-level errors are dispatched to
     * every service's {@code onError()} handler.
     * <p>
     * <strong>Must be called while holding {@code synchronized(entry)}.</strong>
     * Called after each successful {@link #attach} and {@link #detach} so the listener always
     * reflects the current set of attached services without accumulating stale registrations.
     */
    private static void refreshThrowableListener(ServerEntry entry, JCoIDocServer server) {
        if (entry.throwableListener != null) {
            server.removeServerErrorListener(entry.throwableListener);
            server.removeServerExceptionListener(entry.throwableListener);
        }
        BallerinaThrowableListener throwableListener = new BallerinaThrowableListener(
                entry.runtime, entry.idocService, entry.rfcService);
        entry.throwableListener = throwableListener;
        server.addServerErrorListener(throwableListener);
        server.addServerExceptionListener(throwableListener);
    }
}

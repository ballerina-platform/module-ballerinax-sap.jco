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

package io.ballerina.lib.sap.dataproviders;

import com.sap.conn.jco.ext.Environment;
import com.sap.conn.jco.ext.ServerDataEventListener;
import com.sap.conn.jco.ext.ServerDataProvider;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton {@link ServerDataProvider} that supplies JCo server properties from an in-memory
 * map populated at listener initialisation time.
 * <p>
 * JCo's {@link com.sap.conn.jco.ext.Environment} only allows one server data provider per JVM.
 * {@link #registerIfAbsent()} uses an {@link AtomicBoolean} to ensure the provider is
 * registered exactly once regardless of how many Ballerina listeners are created concurrently.
 */
public class SAPServerDataProvider implements ServerDataProvider {

    public static final String JCO_REP_DEST = ServerDataProvider.JCO_REP_DEST;

    private static final SAPServerDataProvider INSTANCE = new SAPServerDataProvider();
    private static final AtomicBoolean registered = new AtomicBoolean(false);

    private final Map<String, Properties> serverProperties = new ConcurrentHashMap<>();

    private SAPServerDataProvider() {}

    /**
     * Returns the singleton provider instance.
     *
     * @return the single {@link SAPServerDataProvider} for this JVM
     */
    public static SAPServerDataProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Registers this provider with the JCo {@link Environment} if it has not been registered yet.
     * Subsequent calls are no-ops. Thread-safe via {@link AtomicBoolean#compareAndSet}.
     */
    public static void registerIfAbsent() {
        if (registered.compareAndSet(false, true)) {
            Environment.registerServerDataProvider(INSTANCE);
        }
    }

    /**
     * Returns the JCo server properties for the named server.
     *
     * @param serverName the server name previously registered via
     *                   {@link #addServerConfig} or {@link #addAdvancedServerConfig}
     * @return the {@link Properties} for the server
     * @throws DataProviderException if no properties have been registered for {@code serverName}
     */
    @Override
    public Properties getServerProperties(String serverName) {
        if (serverProperties.containsKey(serverName)) {
            return serverProperties.get(serverName);
        } else {
            throw new DataProviderException("Server " + serverName + " not found");
        }
    }

    /**
     * No-op: this provider does not support runtime server configuration changes.
     * The event listener supplied by JCo is intentionally discarded; see {@link #supportsEvents()}.
     */
    @Override
    public void setServerDataEventListener(ServerDataEventListener serverDataEventListener) {
    }

    /**
     * Returns {@code false} because this provider does not propagate server configuration
     * change events back to JCo. All server properties are loaded once at initialisation and
     * remain static for the lifetime of the JVM.
     *
     * @return {@code false}
     */
    @Override
    public boolean supportsEvents() {
        return false;
    }

    /**
     * Registers server properties derived from a structured {@code ServerConfig} Ballerina record.
     * Maps {@code gwhost}, {@code gwserv}, {@code progid}, and {@code connectionCount} to the
     * corresponding {@link ServerDataProvider} constants. If a repository destination is provided,
     * it is also stored so that JCo can look up RFC metadata.
     *
     * @param jcoServerConfig       the Ballerina {@code ServerConfig} record
     * @param serverName            the name under which the properties are stored
     * @param repositoryDestination destination name used for IDoc and RFC metadata look-ups;
     *                              should not be {@code null} when called from the {@code ServerConfig} path
     * @throws RuntimeException if a required property cannot be applied
     */
    public void addServerConfig(BMap<BString, Object> jcoServerConfig, String serverName,
                                String repositoryDestination) {
        Properties properties = new Properties();
        try {
            properties.setProperty(ServerDataProvider.JCO_GWHOST,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_GWHOST).toString());
            properties.setProperty(ServerDataProvider.JCO_GWSERV,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_GWSERV).toString());
            properties.setProperty(ServerDataProvider.JCO_PROGID,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_PROGID).toString());
            properties.setProperty(ServerDataProvider.JCO_CONNECTION_COUNT,
                    jcoServerConfig.getIntValue(SAPConstants.JCO_CONNECTION_COUNT).toString());
            if (repositoryDestination != null) {
                properties.setProperty(ServerDataProvider.JCO_REP_DEST, repositoryDestination);
            }
            serverProperties.put(serverName, properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding server config: " + e.getMessage());
        }
    }

    /**
     * Registers server properties from a pre-parsed {@code Map<String, String>} of JCo server
     * property key-value pairs. Used when the listener is configured via an advanced flat map
     * and server keys have already been separated from destination keys.
     *
     * @param jcoAdvancedServerConfig the raw JCo server property map
     * @param serverName              the name under which the properties are stored
     * @throws RuntimeException if a property cannot be applied
     */
    public void addAdvancedServerConfig(Map<String, String> jcoAdvancedServerConfig, String serverName) {
        Properties properties = new Properties();
        jcoAdvancedServerConfig.forEach((key, value) -> {
            try {
                properties.setProperty(key, value);
            } catch (Exception e) {
                throw new RuntimeException("Error while adding server property " + key + " : " + e.getMessage());
            }
        });
        serverProperties.put(serverName, properties);
    }
}

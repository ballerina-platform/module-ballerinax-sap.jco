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

import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import com.sap.conn.jco.ext.Environment;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton {@link DestinationDataProvider} that supplies JCo destination properties from an
 * in-memory map populated at client/listener initialisation time.
 * <p>
 * JCo's {@link com.sap.conn.jco.ext.Environment} only allows one destination data provider per
 * JVM. {@link #registerIfAbsent()} uses an {@link AtomicBoolean} to ensure the provider is
 * registered exactly once regardless of how many Ballerina clients are created concurrently.
 */
public class SAPDestinationDataProvider implements DestinationDataProvider {

    private static final SAPDestinationDataProvider INSTANCE = new SAPDestinationDataProvider();
    private static final AtomicBoolean registered = new AtomicBoolean(false);

    private final Map<String, Properties> destinationProperties = new ConcurrentHashMap<>();
    private volatile DestinationDataEventListener eventListener;

    private SAPDestinationDataProvider() {}

    /**
     * Returns the singleton provider instance.
     *
     * @return the single {@link SAPDestinationDataProvider} for this JVM
     */
    public static SAPDestinationDataProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Registers this provider with the JCo {@link Environment} if it has not been registered yet.
     * Subsequent calls are no-ops. Thread-safe via {@link AtomicBoolean#compareAndSet}.
     */
    public static void registerIfAbsent() {
        if (registered.compareAndSet(false, true)) {
            try {
                Environment.registerDestinationDataProvider(INSTANCE);
            } catch (Exception e) {
                registered.set(false);
                throw e;
            }
        }
    }

    /**
     * Returns {@code true} if a destination with the given name has been registered.
     * Used by {@code Listener.attach()} to validate the {@code repositoryDestination} before
     * starting the RFC server.
     *
     * @param destinationName the destination name to check; {@code null} returns {@code false}
     * @return {@code true} if the destination is registered, {@code false} otherwise
     */
    public boolean hasDestination(String destinationName) {
        return destinationName != null && destinationProperties.containsKey(destinationName);
    }

    /**
     * Returns the JCo connection properties for the named destination.
     *
     * @param destinationName the destination name previously registered via
     *                        {@link #addDestinationConfig} or {@link #addAdvancedDestinationConfig}
     * @return the {@link Properties} for the destination
     * @throws DataProviderException if no properties have been registered for {@code destinationName}
     */
    @Override
    public Properties getDestinationProperties(String destinationName) {
        if (destinationProperties.containsKey(destinationName)) {
            return destinationProperties.get(destinationName);
        } else {
            throw new DataProviderException("Destination " + destinationName + " not found");
        }
    }

    /**
     * Saves the JCo event listener so that deletion events can be fired when a destination is
     * removed via {@link #removeDestinationConfig}.
     */
    @Override
    public void setDestinationDataEventListener(DestinationDataEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * Returns {@code true} so that JCo knows this provider will fire deletion events when a
     * destination is removed, allowing JCo to invalidate its internal destination cache.
     *
     * @return {@code true}
     */
    @Override
    public boolean supportsEvents() {
        return true;
    }

    /**
     * Removes the destination properties registered under {@code destinationId}, freeing the entry
     * and allowing the ID to be reclaimed.
     * <p>
     * Call this after the associated client has been fully shut down. Thread-safe: delegates to
     * {@link ConcurrentHashMap#remove}.
     *
     * @param destinationId the destination name to remove
     */
    public void removeDestinationConfig(String destinationId) {
        destinationProperties.remove(destinationId);
        DestinationDataEventListener listener = this.eventListener;
        if (listener != null) {
            listener.deleted(destinationId);
        }
    }

    /**
     * Registers destination properties from a {@code BMap} of JCo property key-value pairs.
     * All entries are copied verbatim; structured-field mapping is performed upstream in
     * {@code ballerina/client.bal} before this method is called.
     * <p>
     * The registration is performed atomically via {@link ConcurrentHashMap#putIfAbsent} so that
     * concurrent calls with the same {@code destinationName} cannot silently overwrite each other.
     * A {@link RuntimeException} is thrown if the destination is already registered.
     *
     * @param jcoDestinationConfig the Ballerina configuration map with JCo property key-value pairs
     * @param destinationName      the name under which the properties are stored and later retrieved
     *                             by {@link #getDestinationProperties(String)}
     * @throws RuntimeException if the destination is already registered, any property value is null
     *                          or cannot be applied, or the map is empty
     */
    public void addDestinationConfig(BMap<BString, Object> jcoDestinationConfig, BString destinationName) {
        Properties properties = new Properties();
        try {
            if (!jcoDestinationConfig.isEmpty()) {
                jcoDestinationConfig.entrySet().forEach(entry -> {
                    BString key = entry.getKey();
                    Object rawValue = entry.getValue();
                    if (rawValue == null) {
                        throw new RuntimeException("Null value for destination property " + key);
                    }
                    String value = (rawValue instanceof BString bStr) ? bStr.getValue() : rawValue.toString();
                    try {
                        properties.setProperty(key.toString(), value);
                    } catch (Exception e) {
                        throw new RuntimeException("Error while adding destination property " + key.toString()
                                + " : " + e.getMessage());
                    }
                });
            } else {
                throw new RuntimeException("Provided a empty advanced configuration for destination");
            }
            if (destinationProperties.putIfAbsent(destinationName.toString(), properties) != null) {
                throw new RuntimeException("Destination '" + destinationName
                        + "' is already registered. Use a unique destination ID for each client.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }

    /**
     * Registers destination properties from a pre-parsed {@code Map<String, String>} of JCo property
     * key-value pairs. Used by the {@code Listener} init path when server and destination configuration
     * are supplied together as a single advanced map and destination keys have already been separated
     * from server keys.
     * <p>
     * The registration is performed atomically via {@link ConcurrentHashMap#putIfAbsent} so that
     * concurrent calls with the same {@code destinationName} cannot silently overwrite each other.
     * A {@link RuntimeException} is thrown if the destination is already registered.
     *
     * @param jcoAdvancedDestinationConfig the raw JCo property map (must not be empty)
     * @param destinationName              the name under which the properties are stored
     * @throws RuntimeException if the destination is already registered, the map is empty, or a
     *                          property cannot be applied
     */
    public void addAdvancedDestinationConfig(Map<String, String> jcoAdvancedDestinationConfig,
                                             String destinationName) {
        Properties properties = new Properties();
        try {
            if (!jcoAdvancedDestinationConfig.isEmpty()) {
                jcoAdvancedDestinationConfig.forEach((key, value) -> {
                    try {
                        properties.setProperty(key, value);
                    } catch (Exception e) {
                        throw new RuntimeException("Error while adding destination property " + key + " : "
                                + e.getMessage());
                    }
                });
            } else {
                throw new RuntimeException("Provided a empty advanced configuration for destination");
            }
            if (destinationProperties.putIfAbsent(destinationName, properties) != null) {
                throw new RuntimeException("Destination '" + destinationName
                        + "' is already registered. Use a unique destination ID for each listener.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }
}

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
import io.ballerina.lib.sap.SAPConstants;
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
            Environment.registerDestinationDataProvider(INSTANCE);
        }
    }

    /**
     * Returns the JCo connection properties for the named destination.
     *
     * @param destinationName the destination name previously registered via
     *                        {@link #addDestinationConfig} or {@link #addAdvancedDestinationConfig}
     * @return the {@link Properties} for the destination
     * @throws RuntimeException if no properties have been registered for {@code destinationName}
     */
    @Override
    public Properties getDestinationProperties(String destinationName) {
        if (destinationProperties.containsKey(destinationName)) {
            return destinationProperties.get(destinationName);
        } else {
            throw new RuntimeException("Destination " + destinationName + " not found");
        }
    }

    /**
     * No-op: this provider does not support runtime destination configuration changes.
     * The event listener supplied by JCo is intentionally discarded; see {@link #supportsEvents()}.
     */
    @Override
    public void setDestinationDataEventListener(DestinationDataEventListener eventListener) {
    }

    /**
     * Returns {@code false} because this provider does not propagate destination configuration
     * change events back to JCo. All destination properties are loaded once at initialisation and
     * remain static for the lifetime of the JVM.
     *
     * @return {@code false}
     */
    @Override
    public boolean supportsEvents() {
        return false;
    }

    /**
     * Registers destination properties derived from a structured {@code DestinationConfig} Ballerina record
     * or an advanced flat key-value map.
     * <p>
     * When {@code jcoDestinationConfig} is a {@code DestinationConfig} record, the well-known fields
     * ({@code jcoClient}, {@code user}, {@code passwd}, etc.) are mapped to the corresponding
     * {@link DestinationDataProvider} constants. For any other record/map type, all entries are
     * copied verbatim as JCo property key-value pairs, enabling advanced configuration not covered
     * by the structured type.
     *
     * @param jcoDestinationConfig the Ballerina configuration record or advanced map
     * @param destinationName      the name under which the properties are stored and later retrieved
     *                             by {@link #getDestinationProperties(String)}
     * @throws RuntimeException if any property cannot be applied or the advanced map is empty
     */
    public void addDestinationConfig(BMap<BString, Object> jcoDestinationConfig, BString destinationName) {
        Properties properties = new Properties();
        try {
            if (jcoDestinationConfig.getType().getName().equals(SAPConstants.JCO_DESTINATION_CONFIG)) {
                properties.setProperty(DestinationDataProvider.JCO_CLIENT,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_CLIENT).toString());
                properties.setProperty(DestinationDataProvider.JCO_USER,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_USER).toString());
                properties.setProperty(DestinationDataProvider.JCO_PASSWD,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_PASSWD).toString());
                properties.setProperty(DestinationDataProvider.JCO_LANG,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_LANG).toString());
                properties.setProperty(DestinationDataProvider.JCO_ASHOST,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_ASHOST).toString());
                properties.setProperty(DestinationDataProvider.JCO_SYSNR,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_SYSNR).toString());
                properties.setProperty(DestinationDataProvider.JCO_GROUP,
                        jcoDestinationConfig.getStringValue(SAPConstants.JCO_GROUP).toString());
            } else {
                if (!jcoDestinationConfig.isEmpty()) {
                    jcoDestinationConfig.entrySet().forEach(entry -> {
                        BString key = entry.getKey();
                        BString value = (BString) entry.getValue();
                        try {
                            properties.setProperty(key.toString(), value.toString());
                        } catch (Exception e) {
                            throw new RuntimeException("Error while adding destination property " + key.toString()
                                    + " : " + e.getMessage());
                        }
                    });
                } else {
                    throw new RuntimeException("Provided a empty advanced configuration for destination");
                }
            }
            destinationProperties.put(destinationName.toString(), properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }

    /**
     * Registers destination properties from a pre-parsed {@code Map<String, String>} of JCo property
     * key-value pairs. Used by the {@code Listener} init path when server and destination configuration
     * are supplied together as a single advanced map and destination keys have already been separated
     * from server keys.
     *
     * @param jcoAdvancedDestinationConfig the raw JCo property map (must not be empty)
     * @param destinationName              the name under which the properties are stored
     * @throws RuntimeException if the map is empty or a property cannot be applied
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
            destinationProperties.put(destinationName, properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }
}

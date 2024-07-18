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

import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class BallerinaDestinationDataProvider implements DestinationDataProvider {

    private final Map<String, Properties> destinationProperties = new HashMap<>();

    @Override
    public Properties getDestinationProperties(String destinationName) {
        if (destinationProperties.containsKey(destinationName)) {
            return destinationProperties.get(destinationName);
        } else {
            throw new RuntimeException("Destination " + destinationName + " not found");
        }
    }

    @Override
    public void setDestinationDataEventListener(DestinationDataEventListener eventListener) {
    }

    @Override
    public boolean supportsEvents() {
        return true;
    }

    public void addDestination(BMap<BString, Object> jcoDestinationConfig, BString destinationName) {
        Properties properties = new Properties();
        jcoDestinationConfig.entrySet().forEach(entry -> {
            BString key = entry.getKey();
            BString value = (BString) entry.getValue();
            properties.setProperty(SAPConstants.CONFIG_KEYS.get(key.toString()), value.toString());
        });
        destinationProperties.put(destinationName.toString(), properties);
    }
}

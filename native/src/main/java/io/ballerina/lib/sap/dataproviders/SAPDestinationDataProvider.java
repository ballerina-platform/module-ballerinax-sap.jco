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
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SAPDestinationDataProvider implements DestinationDataProvider {

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

    @SuppressWarnings("unchecked")
    public void addDestination(BMap<BString, Object> jcoDestinationConfig, BString destinationName) {
        Properties properties = new Properties();
        try {
            properties.setProperty(DestinationDataProvider.JCO_CLIENT,
                    jcoDestinationConfig.get(SAPConstants.JCO_CLIENT).toString());
            properties.setProperty(DestinationDataProvider.JCO_USER,
                    jcoDestinationConfig.get(SAPConstants.JCO_USER).toString());
            properties.setProperty(DestinationDataProvider.JCO_PASSWD,
                    jcoDestinationConfig.get(SAPConstants.JCO_PASSWD).toString());
            properties.setProperty(DestinationDataProvider.JCO_LANG,
                    jcoDestinationConfig.get(SAPConstants.JCO_LANG).toString());
            properties.setProperty(DestinationDataProvider.JCO_ASHOST,
                    jcoDestinationConfig.get(SAPConstants.JCO_ASHOST).toString());
            properties.setProperty(DestinationDataProvider.JCO_SYSNR,
                    jcoDestinationConfig.get(SAPConstants.JCO_SYSNR).toString());
            properties.setProperty(DestinationDataProvider.JCO_GROUP,
                    jcoDestinationConfig.get(SAPConstants.JCO_GROUP).toString());
            BMap<BString, Object> advancedConfigs = (BMap<BString, Object>) jcoDestinationConfig.get(
                    SAPConstants.ADVANCED_CONFIGS);
            if (advancedConfigs != null) {
                if (!advancedConfigs.isEmpty()) {
                    advancedConfigs.entrySet().forEach(entry -> {
                        BString key = entry.getKey();
                        BString value = (BString) entry.getValue();
                        try {
                            properties.setProperty(key.toString(), value.toString());
                        } catch (Exception e) {
                            throw new RuntimeException("Error while adding destination property " + key.toString()
                                    + " : " + e.getMessage());
                        }
                    });
                }
            }
            destinationProperties.put(destinationName.toString(), properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }
}

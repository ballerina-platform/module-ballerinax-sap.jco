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

import com.sap.conn.jco.ext.ServerDataEventListener;
import com.sap.conn.jco.ext.ServerDataProvider;
import io.ballerina.lib.sap.SAPConstants;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SAPServerDataProvider implements ServerDataProvider {

    private final Map<String, Properties> serverProperties = new HashMap<>();

    @Override
    public Properties getServerProperties(String serverName) {
        if (serverProperties.containsKey(serverName)) {
            return serverProperties.get(serverName);
        } else {
            throw new RuntimeException("Server " + serverName + " not found");
        }
    }

    @Override
    public void setServerDataEventListener(ServerDataEventListener serverDataEventListener) {
    }

    @Override
    public boolean supportsEvents() {
        return true;
    }

    @SuppressWarnings("unchecked")
    public void addServer(BMap<BString, Object> jcoServerConfig, BString serverName) {
        Properties properties = new Properties();
        try {
            if (jcoServerConfig.getType().getName().equals(SAPConstants.JCO_SERVER_CONFIG_NAME)) {
                properties.setProperty(ServerDataProvider.JCO_GWHOST,
                        jcoServerConfig.getStringValue(SAPConstants.JCO_GWHOST).toString());
                properties.setProperty(ServerDataProvider.JCO_GWSERV,
                        jcoServerConfig.getStringValue(SAPConstants.JCO_GWSERV).toString());
                properties.setProperty(ServerDataProvider.JCO_PROGID,
                        jcoServerConfig.getStringValue(SAPConstants.JCO_PROGID).toString());
            } else {
                if (!jcoServerConfig.isEmpty()) {
                    jcoServerConfig.entrySet().forEach(entry -> {
                        BString key = entry.getKey();
                        BString value = (BString) entry.getValue();
                        try {
                            properties.setProperty(key.toString(), value.toString());
                        } catch (Exception e) {
                            throw new RuntimeException("Error while adding destination property " + key.toString()
                                    + " : " + e.getMessage());
                        }
                        properties.setProperty(key.toString(), value.toString());
                    });
                } else {
                    throw new RuntimeException("Provided a empty advanced configuration for server");
                }
            }
            serverProperties.put(serverName.toString(), properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }
}

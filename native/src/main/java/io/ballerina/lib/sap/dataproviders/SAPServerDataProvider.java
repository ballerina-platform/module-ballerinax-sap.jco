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

    public void addServerConfig(BMap<BString, Object> jcoServerConfig, String serverName) {
        Properties properties = new Properties();
        try {
            properties.setProperty(ServerDataProvider.JCO_GWHOST,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_GWHOST).toString());
            properties.setProperty(ServerDataProvider.JCO_GWSERV,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_GWSERV).toString());
            properties.setProperty(ServerDataProvider.JCO_PROGID,
                    jcoServerConfig.getStringValue(SAPConstants.JCO_PROGID).toString());
            serverProperties.put(serverName, properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding server config: " + e.getMessage());
        }
    }

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

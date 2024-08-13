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
    public void addServerData(BMap<BString, Object> serverDataConfig, BString serverName) {
        Properties properties = new Properties();
        try {
            properties.setProperty(ServerDataProvider.JCO_GWHOST,
                    serverDataConfig.get(SAPConstants.JCO_GWHOST).toString());
            properties.setProperty(ServerDataProvider.JCO_GWSERV,
                    serverDataConfig.get(SAPConstants.JCO_GWSERV).toString());
            properties.setProperty(ServerDataProvider.JCO_PROGID,
                    serverDataConfig.get(SAPConstants.JCO_PROGID).toString());
            properties.setProperty(ServerDataProvider.JCO_REP_DEST,
                    serverDataConfig.get(SAPConstants.JCO_REP_DEST).toString());
            properties.setProperty(ServerDataProvider.JCO_CONNECTION_COUNT,
                    serverDataConfig.get(SAPConstants.JCO_CONNECTION_COUNT).toString());
            BMap<BString, Object> advancedConfigs = (BMap<BString, Object>) serverDataConfig.get(
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
                        properties.setProperty(key.toString(), value.toString());
                    });
                }
            }
            serverProperties.put(serverName.toString(), properties);
        } catch (Exception e) {
            throw new RuntimeException("Error while adding destination: " + e.getMessage());
        }
    }
}

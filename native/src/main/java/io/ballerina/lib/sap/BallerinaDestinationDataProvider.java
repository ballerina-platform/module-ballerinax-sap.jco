package io.ballerina.lib.sap;

import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import io.ballerina.runtime.api.utils.StringUtils;
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

    public void addDestination(BMap<BString, Object> jcoDestinationConfig) {
        Properties properties = new Properties();
        Map<String, String> configKeys = new HashMap<>();

        configKeys.put(SAPConstants.JCO_CLIENT, DestinationDataProvider.JCO_CLIENT);
        configKeys.put(SAPConstants.USER, DestinationDataProvider.JCO_USER);
        configKeys.put(SAPConstants.PASSWORD, DestinationDataProvider.JCO_PASSWD);
        configKeys.put(SAPConstants.LANGUAGE, DestinationDataProvider.JCO_LANG);
        configKeys.put(SAPConstants.HOST, DestinationDataProvider.JCO_ASHOST);
        configKeys.put(SAPConstants.SYSTEM_NUMBER, DestinationDataProvider.JCO_SYSNR);
        configKeys.put(SAPConstants.GROUP, DestinationDataProvider.JCO_GROUP);
        configKeys.put(SAPConstants.AUTH_TYPE, DestinationDataProvider.JCO_AUTH_TYPE);
        configKeys.put(SAPConstants.CODE_PAGE, DestinationDataProvider.JCO_CODEPAGE);
        configKeys.put(SAPConstants.ALIAS_USER, DestinationDataProvider.JCO_ALIAS_USER);
        configKeys.put(SAPConstants.PCS, DestinationDataProvider.JCO_PCS);
        configKeys.put(SAPConstants.MS_HOST, DestinationDataProvider.JCO_MSHOST);
        configKeys.put(SAPConstants.MS_SERVER, DestinationDataProvider.JCO_MSSERV);
        configKeys.put(SAPConstants.R3_NAME, DestinationDataProvider.JCO_R3NAME);
        configKeys.put(SAPConstants.STICKY, DestinationDataProvider.JCO_STICKY_ASHOST);
        configKeys.put(SAPConstants.SAP_ROUTER, DestinationDataProvider.JCO_SAPROUTER);
        configKeys.put(SAPConstants.MY_SAP_SSO2, DestinationDataProvider.JCO_MYSAPSSO2);
        configKeys.put(SAPConstants.GET_SSO2, DestinationDataProvider.JCO_GETSSO2);
        configKeys.put(SAPConstants.X509_CERT, DestinationDataProvider.JCO_X509CERT);
        configKeys.put(SAPConstants.OIDC_BEARER_TOKEN, DestinationDataProvider.JCO_OIDC_BEARER_TOKEN);
        configKeys.put(SAPConstants.EXT_ID_DATA, DestinationDataProvider.JCO_EXTID_DATA);
        configKeys.put(SAPConstants.EXT_ID_TYPE, DestinationDataProvider.JCO_EXTID_TYPE);
        configKeys.put(SAPConstants.L_CHECK, DestinationDataProvider.JCO_LCHECK);
        configKeys.put(SAPConstants.USE_BAS_XML, DestinationDataProvider.JCO_USE_BASXML);
        configKeys.put(SAPConstants.NETWORK, DestinationDataProvider.JCO_CLIENT);
        configKeys.put(SAPConstants.SERIALIZATION_FORMAT, DestinationDataProvider.JCO_SERIALIZATION_FORMAT);
        configKeys.put(SAPConstants.DELTA, DestinationDataProvider.JCO_DELTA);
        configKeys.put(SAPConstants.SNC_MODE, DestinationDataProvider.JCO_SNC_MODE);
        configKeys.put(SAPConstants.SNC_SSO, DestinationDataProvider.JCO_SNC_SSO);
        configKeys.put(SAPConstants.SNC_PARTNER_NAME, DestinationDataProvider.JCO_SNC_PARTNERNAME);
        configKeys.put(SAPConstants.SNC_QOP, DestinationDataProvider.JCO_SNC_QOP);
        configKeys.put(SAPConstants.SNC_MY_NAME, DestinationDataProvider.JCO_SNC_MYNAME);
        configKeys.put(SAPConstants.PEAK_LIMIT, DestinationDataProvider.JCO_PEAK_LIMIT);
        configKeys.put(SAPConstants.POOL_CAPACITY, DestinationDataProvider.JCO_POOL_CAPACITY);
        configKeys.put(SAPConstants.EXPIRATION_TIME, DestinationDataProvider.JCO_EXPIRATION_TIME);
        configKeys.put(SAPConstants.EXPIRATION_CHECK_PERIOD, DestinationDataProvider.JCO_EXPIRATION_PERIOD);
        configKeys.put(SAPConstants.MAX_GET_CLIENT_TIME, DestinationDataProvider.JCO_MAX_GET_TIME);
        configKeys.put(SAPConstants.POOL_CHECK_CONNECTION, DestinationDataProvider.JCO_POOL_CHECK_CONNECTION);
        configKeys.put(SAPConstants.REPOSITORY_DESTINATION, DestinationDataProvider.JCO_REPOSITORY_DEST);
        configKeys.put(SAPConstants.REPOSITORY_USER, DestinationDataProvider.JCO_REPOSITORY_USER);
        configKeys.put(SAPConstants.REPOSITORY_PASSWORD, DestinationDataProvider.JCO_REPOSITORY_PASSWD);
        configKeys.put(SAPConstants.REPOSITORY_SNC_MODE, DestinationDataProvider.JCO_REPOSITORY_SNC);
        configKeys.put(SAPConstants.REPOSITORY_CHECK_INTERVAL, DestinationDataProvider.JCO_REPOSITORY_CHECK_INTERVAL);
        configKeys.put(SAPConstants.TRACE, DestinationDataProvider.JCO_TRACE);
        configKeys.put(SAPConstants.GW_HOST, DestinationDataProvider.JCO_GWHOST);
        configKeys.put(SAPConstants.GW_SERV, DestinationDataProvider.JCO_GWSERV);
        configKeys.put(SAPConstants.TP_HOST, DestinationDataProvider.JCO_TPHOST);
        configKeys.put(SAPConstants.TP_NAME, DestinationDataProvider.JCO_TPNAME);
        configKeys.put(SAPConstants.WS_HOST, DestinationDataProvider.JCO_WSHOST);
        configKeys.put(SAPConstants.WS_PORT, DestinationDataProvider.JCO_WSPORT);
        configKeys.put(SAPConstants.USE_TLS, DestinationDataProvider.JCO_USE_TLS);
        configKeys.put(SAPConstants.TLS_TRUST_ALL, DestinationDataProvider.JCO_TLS_TRUST_ALL);
        configKeys.put(SAPConstants.TLS_P12_FILE, DestinationDataProvider.JCO_TLS_P12FILE);
        configKeys.put(SAPConstants.TLS_P12_PASSWORD, DestinationDataProvider.JCO_TLS_P12PASSWD);
        configKeys.put(SAPConstants.TLS_CLIENT_CERTIFICATE_LOGON,
                DestinationDataProvider.JCO_TLS_CLIENT_CERTIFICATE_LOGON);
        configKeys.put(SAPConstants.PROXY_HOST, DestinationDataProvider.JCO_PROXY_HOST);
        configKeys.put(SAPConstants.PROXY_PORT, DestinationDataProvider.JCO_PROXY_PORT);
        configKeys.put(SAPConstants.PROXY_USER, DestinationDataProvider.JCO_PROXY_USER);
        configKeys.put(SAPConstants.PROXY_PASSWORD, DestinationDataProvider.JCO_PROXY_PASSWD);
        configKeys.put(SAPConstants.WS_PING_CHECK_INTERVAL, DestinationDataProvider.JCO_PING_CHECK_INTERVAL);
        configKeys.put(SAPConstants.WS_PING_PERIOD, DestinationDataProvider.JCO_PING_PERIOD);
        configKeys.put(SAPConstants.WS_PONG_TIMEOUT, DestinationDataProvider.JCO_PONG_TIMEOUT);
        configKeys.put(SAPConstants.JCO_TYPE, DestinationDataProvider.JCO_TYPE);
        configKeys.put(SAPConstants.USE_SAP_GUI, DestinationDataProvider.JCO_USE_SAPGUI);
        configKeys.put(SAPConstants.DENY_INITIAL_PASSWORD, DestinationDataProvider.JCO_DENY_INITIAL_PASSWORD);
        configKeys.put(SAPConstants.REPOSITORY_ROUNDTRIP_OPTIMIZATION,
                DestinationDataProvider.JCO_REPOSITORY_ROUNDTRIP_OPTIMIZATION);

        configKeys.forEach((key, destKey) -> {
            BString value = jcoDestinationConfig.getStringValue(StringUtils.fromString(key));
            if (value != null) {
                properties.setProperty(destKey, value.toString());
            }
        });
        destinationProperties.put(jcoDestinationConfig.getStringValue(
                StringUtils.fromString("destinationId")).toString(), properties);
    }
}

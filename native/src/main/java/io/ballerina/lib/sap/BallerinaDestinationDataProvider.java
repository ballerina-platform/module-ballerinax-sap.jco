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
        properties.setProperty(DestinationDataProvider.JCO_CLIENT, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("jcoClient")).toString());
        properties.setProperty(DestinationDataProvider.JCO_USER, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("user")).toString());
        properties.setProperty(DestinationDataProvider.JCO_PASSWD, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("password")).toString());
        properties.setProperty(DestinationDataProvider.JCO_LANG, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("language")).toString());
        properties.setProperty(DestinationDataProvider.JCO_ASHOST, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("host")).toString());
        properties.setProperty(DestinationDataProvider.JCO_SYSNR, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("systemNumber")).toString());
        properties.setProperty(DestinationDataProvider.JCO_GROUP, jcoDestinationConfig.getStringValue(
                StringUtils.fromString("group")).toString());
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("authType")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_AUTH_TYPE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("authType")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("codePage")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_CODEPAGE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("codePage")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("aliasUser")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_ALIAS_USER, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("aliasUser")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("pcs")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PCS, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("pcs")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("msHost")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_MSHOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("msHost")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("msServer")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_MSSERV, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("msServer")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("r3Name")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_R3NAME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("r3Name")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sticky")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_STICKY_ASHOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sticky")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sapRouter")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SAPROUTER, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sapRouter")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("mySapSso2")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_MYSAPSSO2, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("mySapSso2")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("getSso2")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_GETSSO2, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("getSso2")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("x509Cert")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_X509CERT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("x509Cert")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("oidcBearerToken")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_OIDC_BEARER_TOKEN, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("oidcBearerToken")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("extIdData")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_EXTID_DATA, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("extIdData")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("extIdType")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_EXTID_TYPE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("extIdType")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("lCheck")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_LCHECK, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("lCheck")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("useBasXml")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_USE_BASXML, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("useBasXml")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("network")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_CLIENT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("network")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("serializationFormat")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SERIALIZATION_FORMAT,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("serializationFormat")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("delta")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_DELTA, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("delta")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sncMode")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SNC_MODE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sncMode")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sncSso")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SNC_SSO, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sncSso")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sncPartnerName")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SNC_PARTNERNAME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sncPartnerName")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sncQop")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SNC_QOP, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sncQop")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("sncMyName")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_SNC_MYNAME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("sncMyName")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("peakLimit")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("peakLimit")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("poolCapacity")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("poolCapacity")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("expirationTime")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_EXPIRATION_TIME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("expirationTime")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("expirationCheckPeriod")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_EXPIRATION_PERIOD, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("expirationCheckPeriod")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("maxGetClientTime")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_MAX_GET_TIME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("maxGetClientTime")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("poolCheckConnection")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_POOL_CHECK_CONNECTION,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("poolCheckConnection")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositoryDestination")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_DEST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("repositoryDestination")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositoryUser")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_USER, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("repositoryUser")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositoryPassword")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_PASSWD, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("repositoryPassword")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositorySncMode")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_SNC, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("repositorySncMode")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositoryCheckInterval")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_CHECK_INTERVAL,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("repositoryCheckInterval")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("trace")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TRACE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("trace")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("gwHost")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_GWHOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("gwHost")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("gwServ")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_GWSERV, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("gwServ")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tpHost")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TPHOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("tpHost")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tpName")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TPNAME, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("tpName")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("wsHost")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_WSHOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("wsHost")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("wsPort")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_WSPORT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("wsPort")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("useTls")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_USE_TLS, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("useTls")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tlsTrustAll")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TLS_TRUST_ALL, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("tlsTrustAll")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tlsP12File")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TLS_P12FILE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("tlsP12File")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tlsP12Password")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TLS_P12PASSWD, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("tlsP12Password")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("tlsClientCertificateLogon")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TLS_CLIENT_CERTIFICATE_LOGON,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("tlsClientCertificateLogon")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("proxyHost")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PROXY_HOST, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("proxyHost")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("proxyPort")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PROXY_PORT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("proxyPort")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("proxyUser")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PROXY_USER, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("proxyUser")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("proxyPassword")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PROXY_PASSWD, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("proxyPassword")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("wsPingCheckInterval")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PING_CHECK_INTERVAL,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("wsPingCheckInterval")).toString());
        }

        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("wsPingPeriod")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PING_PERIOD, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("wsPingPeriod")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("wsPongTimeout")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_PONG_TIMEOUT, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("wsPongTimeout")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("jcoType")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_TYPE, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("jcoType")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("useSapGui")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_USE_SAPGUI, jcoDestinationConfig.getStringValue(
                    StringUtils.fromString("useSapGui")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("denyInitialPassword")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_DENY_INITIAL_PASSWORD,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("denyInitialPassword")).toString());
        }
        if (jcoDestinationConfig.getStringValue(StringUtils.fromString("repositoryRoundtripOptimization")) != null) {
            properties.setProperty(DestinationDataProvider.JCO_REPOSITORY_ROUNDTRIP_OPTIMIZATION,
                    jcoDestinationConfig.getStringValue(
                            StringUtils.fromString("repositoryRoundtripOptimization")).toString());
        }
        destinationProperties.put(jcoDestinationConfig.getStringValue(
                StringUtils.fromString("destinationId")).toString(), properties);
    }
}

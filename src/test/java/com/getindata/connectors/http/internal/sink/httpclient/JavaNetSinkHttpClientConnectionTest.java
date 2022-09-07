package com.getindata.connectors.http.internal.sink.httpclient;

import java.io.File;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.getindata.connectors.http.internal.HttpsConnectionTestBase;
import com.getindata.connectors.http.internal.SinkHttpClientResponse;
import com.getindata.connectors.http.internal.config.HttpConnectorConfigConstants;
import com.getindata.connectors.http.internal.sink.HttpSinkRequestEntry;

class JavaNetSinkHttpClientConnectionTest extends HttpsConnectionTestBase {

    @BeforeEach
    public void setUp() {
        super.setUp();
    }

    @AfterEach
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void testHttpConnection() throws ExecutionException, InterruptedException {

        wireMockServer = new WireMockServer(SERVER_PORT);
        wireMockServer.start();
        mockEndPoint(wireMockServer);

        testSinkClientForConnection(new Properties(), "http://localhost:", SERVER_PORT);
    }

    @Test
    public void testHttpsConnectionWithSelfSignedCert() throws Exception {

        File keyStoreFile = new File(SERVER_KEYSTORE_PATH);

        wireMockServer = new WireMockServer(options()
            .httpsPort(HTTPS_SERVER_PORT)
            .httpDisabled(true)
            .keystorePath(keyStoreFile.getAbsolutePath())
            .keystorePassword("password")
            .keyManagerPassword("password")
        );

        wireMockServer.start();
        mockEndPoint(wireMockServer);

        properties.setProperty(HttpConnectorConfigConstants.ALLOW_SELF_SIGNED, "true");

        testSinkClientForConnection(properties, "https://localhost:", HTTPS_SERVER_PORT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ca.crt", "server.crt", "ca_server_bundle.cert.pem"})
    public void testHttpsConnectionWithAddedCerts(String certName) throws Exception {

        File keyStoreFile = new File(SERVER_KEYSTORE_PATH);
        File trustedCert = new File(CERTS_PATH + certName);

        wireMockServer = new WireMockServer(options()
            .httpsPort(HTTPS_SERVER_PORT)
            .httpDisabled(true)
            .keystorePath(keyStoreFile.getAbsolutePath())
            .keystorePassword("password")
            .keyManagerPassword("password")
        );

        wireMockServer.start();
        mockEndPoint(wireMockServer);

        properties.setProperty(
            HttpConnectorConfigConstants.SERVER_TRUSTED_CERT,
            trustedCert.getAbsolutePath()
        );

        testSinkClientForConnection(properties, "https://localhost:", HTTPS_SERVER_PORT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"clientPrivateKey.pem", "clientPrivateKey.der"})
    public void testMTlsConnection(String clientPrivateKeyName) throws Exception {

        File keyStoreFile = new File(SERVER_KEYSTORE_PATH);
        File trustStoreFile = new File(SERVER_TRUSTSTORE_PATH);
        File serverTrustedCert = new File(CERTS_PATH + "ca.crt");

        File clientCert = new File(CERTS_PATH + "client.crt");
        File clientPrivateKey = new File(CERTS_PATH + clientPrivateKeyName);

        this.wireMockServer = new WireMockServer(options()
            .httpDisabled(true)
            .httpsPort(HTTPS_SERVER_PORT)
            .keystorePath(keyStoreFile.getAbsolutePath())
            .keystorePassword("password")
            .keyManagerPassword("password")
            .needClientAuth(true)
            .trustStorePath(trustStoreFile.getAbsolutePath())
            .trustStorePassword("password")
        );

        wireMockServer.start();
        mockEndPoint(wireMockServer);

        properties.setProperty(
            HttpConnectorConfigConstants.SERVER_TRUSTED_CERT,
            serverTrustedCert.getAbsolutePath()
        );
        properties.setProperty(
            HttpConnectorConfigConstants.CLIENT_CERT,
            clientCert.getAbsolutePath()
        );
        properties.setProperty(
            HttpConnectorConfigConstants.CLIENT_PRIVATE_KEY,
            clientPrivateKey.getAbsolutePath()
        );

        testSinkClientForConnection(properties, "https://localhost:", HTTPS_SERVER_PORT);
    }

    @Test
    public void testMTlsConnectionUsingKeyStore() throws Exception {
        String password = "password";

        String clientKeyStoreName = "client_keyStore.p12";
        String serverKeyStoreName = "serverKeyStore.jks";
        String serverTrustStoreName = "serverTrustStore.jks";

        File clientKeyStoreFile = new File(CERTS_PATH + clientKeyStoreName);
        File serverKeyStoreFile = new File(CERTS_PATH + serverKeyStoreName);
        File serverTrustStoreFile = new File(CERTS_PATH + serverTrustStoreName);
        File serverTrustedCert = new File(CERTS_PATH + "ca_server_bundle.cert.pem");

        this.wireMockServer = new WireMockServer(options()
            .httpDisabled(true)
            .httpsPort(HTTPS_SERVER_PORT)
            .keystorePath(serverKeyStoreFile.getAbsolutePath())
            .keystorePassword(password)
            .keyManagerPassword(password)
            .needClientAuth(true)
            .trustStorePath(serverTrustStoreFile.getAbsolutePath())
            .trustStorePassword(password)
        );

        wireMockServer.start();
        mockEndPoint(wireMockServer);

        properties.setProperty(
            HttpConnectorConfigConstants.KEY_STORE_PASSWORD,
            password
        );
        properties.setProperty(
            HttpConnectorConfigConstants.KEY_STORE_PATH,
            clientKeyStoreFile.getAbsolutePath()
        );
        properties.setProperty(
            HttpConnectorConfigConstants.SERVER_TRUSTED_CERT,
            serverTrustedCert.getAbsolutePath()
        );

        testSinkClientForConnection(properties, "https://localhost:", HTTPS_SERVER_PORT);
    }


    @ParameterizedTest
    @CsvSource(value = {
        "invalid.crt, client.crt, clientPrivateKey.pem",
        "ca.crt, invalid.crt, clientPrivateKey.pem",
        "ca.crt, client.crt, invalid.pem"
    })
    public void shouldThrowOnInvalidPath(
            String serverCertName,
            String clientCertName,
            String clientKeyName) {

        File serverTrustedCert = new File(CERTS_PATH + serverCertName);
        File clientCert = new File(CERTS_PATH + clientCertName);
        File clientPrivateKey = new File(CERTS_PATH + clientKeyName);

        properties.setProperty(
            HttpConnectorConfigConstants.SERVER_TRUSTED_CERT,
            serverTrustedCert.getAbsolutePath()
        );
        properties.setProperty(
            HttpConnectorConfigConstants.CLIENT_CERT,
            clientCert.getAbsolutePath()
        );
        properties.setProperty(
            HttpConnectorConfigConstants.CLIENT_PRIVATE_KEY,
            clientPrivateKey.getAbsolutePath()
        );

        assertThrows(RuntimeException.class, () -> new JavaNetSinkHttpClient(properties));
    }

    private void testSinkClientForConnection(Properties properties, String x,
        int httpsServerPort) throws InterruptedException, ExecutionException {
        JavaNetSinkHttpClient client = new JavaNetSinkHttpClient(properties);
        HttpSinkRequestEntry requestEntry = new HttpSinkRequestEntry("GET", new byte[0]);
        SinkHttpClientResponse response =
            client.putRequests(
                Collections.singletonList(requestEntry),
                x + httpsServerPort + ENDPOINT
            ).get();

        assertThat(response.getSuccessfulRequests()).isNotEmpty();
        assertThat(response.getFailedRequests()).isEmpty();
    }

    private void mockEndPoint(WireMockServer wireMockServer) {
        wireMockServer.stubFor(any(urlPathEqualTo(ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{}"))
        );
    }
}

package com.streamx.blueprints.resource.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DisableCertificateValidationTest {

  private static final String PASSWORD = "password";
  private static final String HOSTNAME = "127.0.0.1.nip.io";
  private static final int PORT = 8443;
  private static final String NIP_IO_URL = "https://" + HOSTNAME + ":" + PORT + "/test";
  private HttpsServer server;

  private final WebClientsFactory webClientsFactory = new WebClientsFactory();
  private final Configuration configuration = mock();

  @BeforeEach
  void setupServer() throws Exception {
    server = HttpsServer.create(new InetSocketAddress(PORT), 0);
    SSLContext sslContext = createTestSslContext();

    server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
    server.createContext("/test", exchange -> {
      byte[] response = "Success".getBytes();
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void setupMocks() {
    webClientsFactory.configuration = configuration;
  }

  @Test
  void shouldFailDownloadingSelfSignedHttpsUrl() throws Exception {
    // given
    doReturn(false).when(configuration).disableCertificateValidation();

    // when & then
    try (CloseableHttpClient client = webClientsFactory.httpClient()) {
      assertThatThrownBy(() -> client.execute(new HttpGet(NIP_IO_URL)))
          .isInstanceOf(SSLHandshakeException.class);
    }
  }

  @Test
  void shouldAllowDownloadingSelfSignedHttpsUrl_WhenConfigFlagSet() throws Exception {
    // given
    doReturn(true).when(configuration).disableCertificateValidation();

    try (CloseableHttpClient client = webClientsFactory.httpClient()) {
      // when
      var response = client.execute(new HttpGet(NIP_IO_URL));

      // then
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }
  }

  private SSLContext createTestSslContext() throws Exception {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    InputStream is = new ByteArrayInputStream(generateKeystore());
    ks.load(is, PASSWORD.toCharArray());

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, PASSWORD.toCharArray());

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);
    return sslContext;
  }

  public byte[] generateKeystore() throws Exception {
    File tempKeystore = new File("target/test-keystore-.p12");
    FileUtils.deleteQuietly(tempKeystore);

    ProcessBuilder pb = new ProcessBuilder(
        "keytool",
        "-genkeypair",
        "-alias", "testcert",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-keystore", tempKeystore.getAbsolutePath(),
        "-storetype", "PKCS12",
        "-validity", "36500",
        "-storepass", PASSWORD,
        "-keypass", PASSWORD,
        "-dname", "CN=" + HOSTNAME,
        "-noprompt"
    ).inheritIO();

    Process process = pb.start();
    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(process.exitValue()).isEqualTo(0);

    return FileUtils.readFileToByteArray(tempKeystore);
  }
}

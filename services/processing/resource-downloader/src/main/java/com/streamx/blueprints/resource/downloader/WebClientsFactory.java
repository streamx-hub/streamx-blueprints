package com.streamx.blueprints.resource.downloader;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

@Dependent
public class WebClientsFactory {

  @Inject
  Configuration configuration;

  @Produces
  @DefaultBean
  public CloseableHttpClient httpClient() throws Exception {
    if (configuration.disableCertificateValidation()) {
      return createTrustAllClient();
    }
    return HttpClients.createDefault();
  }

  private static CloseableHttpClient createTrustAllClient() throws Exception {
    SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial(null, (chain, authType) -> true)
        .build();

    SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
        sslContext,
        NoopHostnameVerifier.INSTANCE);

    return HttpClients.custom()
        .setSSLSocketFactory(socketFactory)
        .build();
  }
}

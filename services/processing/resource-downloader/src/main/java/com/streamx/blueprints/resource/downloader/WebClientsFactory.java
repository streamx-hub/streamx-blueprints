package com.streamx.blueprints.resource.downloader;

import io.quarkus.arc.DefaultBean;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class WebClientsFactory {

  @Inject
  Configuration configuration;

  @Inject
  Vertx vertx;

  @Produces
  @DefaultBean
  public WebClient webClient() {

    WebClientOptions options = new WebClientOptions();

    if (configuration.disableCertificateValidation()) {
      options
          .setTrustAll(true)
          .setVerifyHost(false);
    }

    options.setDecompressionSupported(true);

    return WebClient.create(vertx, options);
  }
}

package com.streamx.blueprints.resourcedownloader;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

@Dependent
public class WebClientsFactory {

  @Produces
  @DefaultBean
  public CloseableHttpClient httpClient() {
    return HttpClients.createDefault();
  }
}

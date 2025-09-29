package com.streamx.blueprints.resourcedownloader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

@Dependent
public class WebClientsFactory {

  @ApplicationScoped
  public CloseableHttpClient httpClient() {
    return HttpClients.createDefault();
  }
}

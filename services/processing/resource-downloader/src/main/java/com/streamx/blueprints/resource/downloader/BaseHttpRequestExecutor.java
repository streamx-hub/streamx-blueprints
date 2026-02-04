package com.streamx.blueprints.resource.downloader;

import jakarta.inject.Inject;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;

abstract class BaseHttpRequestExecutor {

  @Inject
  CloseableHttpClient httpClient;

  protected CloseableHttpResponse executeHead(HttpHead request) throws IOException {
    return httpClient.execute(request);
  }

  protected CloseableHttpResponse executeGet(HttpGet request) throws IOException {
    return httpClient.execute(request);
  }

}

package com.streamx.blueprints.test.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class HttpRequestor {

  private HttpRequestor() {
    // no instances
  }

  public static void post(String url, String body) {
    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost(url);
      post.setEntity(new StringEntity(body));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    } catch (IOException | AssertionError ex) {
      DockerLogsRetriever.printDockerContainerLogs();
      fail(ex);
    }
  }

  public static String getUrlContent(String url) {
    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpGet get = new HttpGet(url);
      CloseableHttpResponse response = http.execute(get);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
      return new String(response.getEntity().getContent().readAllBytes());
    } catch (IOException | AssertionError ex) {
      return fail(ex);
    }
  }

}
package com.streamx.blueprints.opensearch.sink.opensearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class OpenSearchHealthCheckServiceTest {

  @InjectSpy
  OpenSearchHealthCheckService service;

  private final RestClient restClient = mock();

  private final Response response = mock();
  private final StatusLine statusLine = mock();

  @BeforeEach
  void setupCommonMocks() throws Exception {
    service.restClient = restClient;

    doReturn(statusLine).when(response).getStatusLine();
    doReturn(response).when(restClient).performRequest(any(Request.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"green", "yellow"})
  void shouldFinishWaiting_WhenGreenOrYellowStatus(String status) throws Exception {
    // when
    setResponseStatus(200);
    setResponseEntity("""
        {
          "status": "%s",
          "active_shards": 2
        }""".formatted(status));

    service.waitForClusterHealth();

    // then
    verify(restClient).performRequest(any(Request.class));
  }

  @Test
  void shouldRetry_WhenNotGreenNorYellowStatus() throws Exception {
    // when
    setResponseStatus(200);
    setResponseEntities("red", "green");

    service.waitForClusterHealth();

    // then
    verify(restClient, times(2)).performRequest(any(Request.class));
  }

  @Test
  void shouldRetry_WhenNot200HttpStatus() throws Exception {
    // when
    doReturn(503, 200).when(statusLine).getStatusCode();
    setResponseEntity("""
        {
          "status": "green",
          "active_shards": 2
        }""");

    service.waitForClusterHealth();

    // then
    verify(restClient, times(2)).performRequest(any(Request.class));
  }

  @Test
  void shouldRetry_WhenResponseNotJson() throws Exception {
    // when
    setResponseStatus(200);
    doReturn(
        new StringEntity("Unexpected response format"),
        new StringEntity("""
            {
              "status": "green",
              "active_shards": 2
            }"""))
        .when(response).getEntity();

    service.waitForClusterHealth();

    // then
    verify(restClient, times(2)).performRequest(any(Request.class));
  }

  @Test
  void shouldRetry_WhenIoException() throws Exception {
    // when
    doThrow(new IOException("I/O Error"))
        .doReturn(response)
        .when(restClient).performRequest(any(Request.class));
    setResponseStatus(200);
    setResponseEntity("""
        {
          "status": "green",
          "active_shards": 2
        }""");

    service.waitForClusterHealth();

    // then
    verify(restClient, times(2)).performRequest(any(Request.class));
  }

  private void setResponseEntities(String firstStatus, String secondStatus) throws Exception {
    doReturn(createResponseEntity(firstStatus), createResponseEntity(secondStatus))
        .when(response).getEntity();
  }

  private HttpEntity createResponseEntity(String status) throws Exception {
    return new StringEntity("""
        {
          "status": "%s",
          "active_shards": 2
        }""".formatted(status));
  }

  private void setResponseEntity(String responseJson) throws Exception {
    HttpEntity entity = new StringEntity(responseJson);
    doReturn(entity).when(response).getEntity();
  }

  private void setResponseStatus(int status) {
    doReturn(status).when(statusLine).getStatusCode();
  }

}

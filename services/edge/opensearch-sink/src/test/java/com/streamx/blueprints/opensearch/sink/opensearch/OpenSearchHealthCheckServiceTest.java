package com.streamx.blueprints.opensearch.sink.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.streamx.blueprints.opensearch.sink.config.Configuration;
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
  private final Configuration configuration = mock();

  private final Response response = mock();
  private final StatusLine statusLine = mock();

  @BeforeEach
  void setupCommonMocks() throws Exception {
    service.restClient = restClient;
    service.configuration = configuration;

    doReturn(1).when(configuration).opensearchHealthCheckWaitTimeoutSeconds();
    doReturn(statusLine).when(response).getStatusLine();
    doReturn(response).when(restClient).performRequest(any(Request.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"green", "yellow"})
  void shouldReturnHealthy_WhenGreenOrYellowStatus(String status) throws Exception {
    // when
    setResponseStatus(200);
    setResponseEntity("""
        {
          "status": "%s",
          "active_shards": 2
        }""".formatted(status));

    // then
    assertThat(service.waitForClusterHealth()).isTrue();
  }

  @Test
  void shouldReturnUnhealthy_WhenNotGreenNorYellowStatus() throws Exception {
    // when
    setResponseStatus(200);
    setResponseEntity("""
        {
          "status": "red",
          "active_shards": 2
        }""");

    // then
    assertThat(service.waitForClusterHealth()).isFalse();
  }

  @Test
  void shouldReturnUnhealthy_WhenNot200HttpStatus() throws Exception {
    // when
    setResponseStatus(503);
    setResponseEntity("""
        {
          "status": "green",
          "active_shards": 2
        }""");

    // then
    assertThat(service.waitForClusterHealth()).isFalse();
  }

  @Test
  void shouldReturnUnhealthy_WhenResponseNotJson() throws Exception {
    // when
    setResponseStatus(200);
    setResponseEntity("Unexpected response format");

    // then
    assertThat(service.waitForClusterHealth()).isFalse();
  }

  @Test
  void shouldReturnUnhealthy_WhenIoException() throws Exception {
    // when
    doThrow(new IOException("I/O Error"))
        .when(restClient).performRequest(any(Request.class));

    // then
    assertThat(service.waitForClusterHealth()).isFalse();
  }

  private void setResponseEntity(String responseJson) throws Exception {
    HttpEntity entity = new StringEntity(responseJson);
    doReturn(entity).when(response).getEntity();
  }

  private void setResponseStatus(int status) {
    doReturn(status).when(statusLine).getStatusCode();
  }

}
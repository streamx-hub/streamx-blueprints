package com.streamx.blueprints.opensearch.sink.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.opensearch.sink.config.Configuration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OpenSearchHealthCheckService {

  private static final String ENDPOINT = "/_cluster/health";
  private static final Set<String> HEALTHY_STATUSES = Set.of("green", "yellow");

  @Inject
  Logger log;

  @Inject
  RestClient restClient;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  Configuration configuration;

  boolean waitForClusterHealth() {
    log.info("Start waiting for Cluster Health");
    Request request = createRequest();
    int timeoutSeconds = configuration.opensearchHealthCheckWaitTimeoutSeconds();

    for (int i = 0; i < timeoutSeconds; i++) {
      if (verifyClusterHealth(request)) {
        log.infof("Cluster is healthy");
        return true;
      }
      log.infof("Waiting for Cluster Health to be in %s", HEALTHY_STATUSES);
      ThreadUtils.sleepQuietly(Duration.ofSeconds(1));
    }

    log.warnf("Cluster Health was not in %s within %d seconds", HEALTHY_STATUSES, timeoutSeconds);
    return false;
  }

  private boolean verifyClusterHealth(Request request) {
    Response response = performRequest(request);
    return response != null
           && validateResponseHttpStatus(response)
           && validateResponseJsonStatus(response);
  }

  private Request createRequest() {
    return new Request(HttpMethod.GET, ENDPOINT);
  }

  private Response performRequest(Request request) {
    try {
      return restClient.performRequest(request);
    } catch (IOException ex) {
      log.warn("Error retrieving Cluster Health", ex);
      return null;
    }
  }

  private boolean validateResponseHttpStatus(Response response) {
    int httpStatus = response.getStatusLine().getStatusCode();
    if (httpStatus == HttpStatus.SC_OK) {
      return true;
    }
    log.debugf("Error retrieving Cluster Health. HTTP status: %s", httpStatus);
    return false;
  }

  private boolean validateResponseJsonStatus(Response response) {
    try {
      String responseJson = EntityUtils.toString(response.getEntity());
      JsonNode responseJsonNode = objectMapper.readTree(responseJson);
      JsonNode statusNode = responseJsonNode.get("status");
      String status = Optional.ofNullable(statusNode)
          .map(JsonNode::textValue)
          .orElse(null);
      if (HEALTHY_STATUSES.contains(status)) {
        return true;
      }
      log.debugf("Unexpected Cluster Health status: %s, expected one of: %s", status,
          HEALTHY_STATUSES);
    } catch (IOException ex) {
      log.warnf(ex, "Error retrieving Cluster Health");
    }
    return false;
  }

}

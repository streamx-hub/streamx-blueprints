package com.streamx.blueprints.opensearch.sink;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.opensearch.sink.OpensearchSinkIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class OpensearchSinkIT extends BaseQuarkusIntegrationTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @AfterAll
  static void stopOpensearch() {
    OpensearchTestContainer.stop();
  }

  @Test
  void shouldIndexAndFindResources() throws Exception {
    // when
    publishIndexableResourceFragment(
        "fragment-1",
        "{\"content\": \"Fragment A\"}"
    );
    publishIndexableResourceFragment(
        "fragment-2",
        "{\"content\": \"Fragment B\"}"
    );
    publishIndexableResource(
        "resource-1",
        "{\"content\": \"Some data\"}",
        List.of("fragment-1", "fragment-2")
    );

    // and
    String expectedUrl = new URIBuilder()
        .setScheme("http")
        .setHost(OpensearchTestContainer.getHost())
        .setPort(OpensearchTestContainer.getPort())
        .setPath("/default/_search")
        .setCustomQuery("q=_id:\"resource-1\"")
        .build()
        .toString();

    // then
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      String urlContent = getUrlContent(expectedUrl);
      JsonNode tree = objectMapper.readTree(urlContent);
      JsonNode hits = tree.get("hits").get("hits");
      assertSameJsons(hits, """
          [ {
            "_index" : "default",
            "_id" : "resource-1",
            "_score" : 1.0,
            "_source" : {
              "fragments" : [ {
                "key" : "fragment-1",
                "eventTime" : null,
                "payload" : {
                  "content" : "Fragment A"
                }
              }, {
                "key" : "fragment-2",
                "eventTime" : null,
                "payload" : {
                  "content" : "Fragment B"
                }
              } ],
              "namespace" : null,
              "type" : "resources/simple",
              "payload" : {
                "content" : "Some data"
              }
            }
          } ]""");
    });
  }

  private void publishIndexableResourceFragment(String key, String content) {
    CloudEvent event = CloudEventUtils.eventWithData(
        key,
        IndexableResourceFragment.TYPE_PUBLISHED,
        new IndexableResourceFragment(content, "fragments/simple"),
        null
    );
    sendEvent(event, Channels.INDEXABLE_RESOURCE_FRAGMENTS);
  }

  private void publishIndexableResource(String key, String content, List<String> fragmentKeys) {
    CloudEvent event = CloudEventUtils.eventWithData(
        key,
        IndexableResource.TYPE_PUBLISHED,
        new IndexableResource(content, "resources/simple", fragmentKeys)
    );
    sendEvent(event, Channels.INDEXABLE_RESOURCES);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    static {
      OpensearchTestContainer.waitUntilPreviousInstanceExited();
      OpensearchTestContainer.start();
    }

    @Override
    public Map<String, String> getConfigOverrides() {
      Map<String, String> overrides = super.getConfigOverrides();
      overrides.put("quarkus.elasticsearch.hosts", OpensearchTestContainer.internalHostAndPort());
      return overrides;
    }
  }
}
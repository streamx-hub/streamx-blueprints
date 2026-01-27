package com.streamx.blueprints.opensearch.sink;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.regex.Pattern;
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
  void shouldIndexResourceAndUpdateItsFragments() throws Exception {
    // when
    publishIndexableResource(
        "resource-1",
        "{\"content\": \"Some data\"}",
        List.of("fragment-1", "fragment-2")
    );
    String expectedUrl = OpensearchTestContainer.getSearchUrl("resource-1");
    waitUntilSearchResponseContains(expectedUrl, "Some data");

    publishIndexableResourceFragment(
        "fragment-1",
        "{\"content\": \"Fragment A\"}"
    );

    publishIndexableResourceFragment(
        "fragment-2",
        "{\"content\": \"Fragment B\"}"
    );

    // then
    assertIndexedResource(expectedUrl, """
        [ {
          "_index" : "default",
          "_id" : "resource-1",
          "_score" : 1.0,
          "_source" : {
            "payload" : {
              "content" : "Some data"
            },
            "namespace" : null,
            "fragments" : [ {
              "payload" : {
                "content" : "Fragment A"
              },
              "eventTime" : 1234567890,
              "key" : "fragment-1"
            }, {
              "payload" : {
                "content" : "Fragment B"
              },
              "eventTime" : 1234567890,
              "key" : "fragment-2"
            } ],
            "type" : "resources/simple"
          }
        } ]"""
    );
  }

  @Test
  void shouldUpdateFragmentsPublishedBeforePublishingResource() throws Exception {
    // when
    publishIndexableResourceFragment(
        "fragment-11",
        "{\"content\": \"Fragment A\"}"
    );

    publishIndexableResourceFragment(
        "fragment-12",
        "{\"content\": \"Fragment B\"}"
    );

    publishIndexableResource(
        "resource-11",
        "{\"content\": \"Some data\"}",
        List.of("fragment-11", "fragment-12")
    );

    String expectedUrl = OpensearchTestContainer.getSearchUrl("resource-11");

    // then
    assertIndexedResource(expectedUrl, """
        [ {
          "_index" : "default",
          "_id" : "resource-11",
          "_score" : 1.0,
          "_source" : {
            "fragments" : [ {
              "key" : "fragment-11",
              "eventTime" : 1234567890,
              "payload" : {
                "content" : "Fragment A"
              }
            }, {
              "key" : "fragment-12",
              "eventTime" : 1234567890,
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
        } ]"""
    );
  }

  private void publishIndexableResourceFragment(String key, String content) {
    CloudEvent event = CloudEventUtils.eventWithData(
        key,
        IndexableResourceFragment.TYPE_PUBLISHED,
        new IndexableResourceFragment(content, "fragments/simple")
    );
    sendStatefulEvent(event,
        Channels.INDEXABLE_RESOURCE_FRAGMENTS_STATE, Channels.INDEXABLE_RESOURCE_FRAGMENTS);
  }

  private void publishIndexableResource(String key, String content, List<String> fragmentKeys) {
    CloudEvent event = CloudEventUtils.eventWithData(
        key,
        IndexableResource.TYPE_PUBLISHED,
        new IndexableResource(content, "resources/simple", fragmentKeys)
    );
    sendEvent(event, Channels.INDEXABLE_RESOURCES);
  }

  private void waitUntilSearchResponseContains(String searchUrl, String responsePath) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      String indexedJson = getUrlContent(searchUrl);
      assertThat(indexedJson).contains(responsePath);
    });
  }

  private void assertIndexedResource(String expectedUrl, String expectedResourceJson) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      String urlContent = getUrlContent(expectedUrl);
      JsonNode tree = objectMapper.readTree(urlContent);
      JsonNode hits = tree.get("hits").get("hits");
      assertSameJsons(
          hits,
          expectedResourceJson,
          Pattern.compile("(?m).*\"eventTime\".*\n") // remove event times from comparison
      );
    });
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
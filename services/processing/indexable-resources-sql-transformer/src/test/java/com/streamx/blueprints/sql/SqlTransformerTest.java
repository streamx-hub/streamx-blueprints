package com.streamx.blueprints.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.state.sql.SqlRepositoryFactory;
import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SqlTransformerTest {

  private static final String DEFAULT_KEY = "/test.html";
  private static final String RESOURCE_TYPE = "any";

  private StatefulInMemorySource indexableResourceSource;
  private InMemorySink<CloudEvent> dataSink;

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  SqlTransformer sqlTransformer;

  @Inject
  StateRepository repository;

  @Inject
  SqlRepositoryFactory repositoryFactory;

  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    indexableResourceSource =
        new StatefulInMemorySource(
            connector,
            Channels.INDEXABLE_RESOURCES,
            Channels.INDEXABLE_RESORUCES_STATE);
    dataSink = connector.sink(Channels.OUTGOING_TRANSFORMATIONS);
  }

  @AfterEach
  void clearStore() {
    dataSink.clear();
    cleanDatabase();
  }

  @Test
  void shouldProduceJsonDataFromIndexableResource() throws JsonProcessingException {
    // given
    String payload = """
        {"title":"test",
         "content":"content",
         "facets":{},
         "fields":{
            "url":"https://example.com",
            "description":"Description",
            "publication_date":"2025-07-15",
            "author":"David Beckham"
         }}
        """;
    CloudEvent event =
        CloudEventUtils.eventWithData(
            DEFAULT_KEY,
            IndexableResource.TYPE_PUBLISHED,
            new IndexableResource(payload, RESOURCE_TYPE, Collections.emptyList()),
            CloudEventUtils.toOffsetDateTime(1));

    // when
    sendEvent(event);
    requestFeedsGeneration();

    // then
    assertThat(dataSink.received()).hasSize(1);
    CloudEvent resultEvent = dataSink.received().getFirst().getPayload();
    assertThat(resultEvent).isNotNull();
    assertThat(resultEvent.getData()).isNotNull();
    assertThat(resultEvent.getSubject()).isEqualTo("latestArticlesRss");
    assertThat(resultEvent.getType()).isEqualTo(Data.TYPE_PUBLISHED);

    ResourceEntity resource = getResourcesList(resultEvent).getFirst();
    assertThat(resource.title()).isEqualTo("test");
    assertThat(resource.fields().get("description")).isEqualTo("Description");
    assertThat(resource.fields().get("author")).isEqualTo("David Beckham");
    assertThat(resource.fields().get("tags")).isNull();
  }

  @Test
  void shouldUpdateExistingIndexableResource() throws JsonProcessingException {
    // given
    String initialPayload = """
        {
          "title": "initial title",
          "content": "initial content",
          "facets": {
            "category": "sports",
            "language": "en"
          },
          "fields": {
            "description": "Initial description",
            "legacyField" : "legacy"
          }
        }
        """;
    CloudEvent initialEvent =
        CloudEventUtils.eventWithData(
            DEFAULT_KEY,
            IndexableResource.TYPE_PUBLISHED,
            new IndexableResource(
                initialPayload,
                RESOURCE_TYPE,
                Collections.emptyList()),
            CloudEventUtils.toOffsetDateTime(1));
    sendEvent(initialEvent);
    requestFeedsGeneration();
    dataSink.clear();

    // when
    String updatedPayload = """
        {
          "title": "updated title",
          "content": "updated content",
          "facets": {
            "category": "football",
            "country": "uk"
          },
          "fields": {
            "description": "Updated description",
            "author": "John Smith"
          }
        }
        """;
    CloudEvent updatedEvent =
        CloudEventUtils.eventWithData(
            DEFAULT_KEY,
            IndexableResource.TYPE_PUBLISHED,
            new IndexableResource(
                updatedPayload,
                RESOURCE_TYPE,
                Collections.emptyList()),
            CloudEventUtils.toOffsetDateTime(2));
    sendEvent(updatedEvent);
    requestFeedsGeneration();

    // then
    assertThat(dataSink.received()).hasSize(1);
    CloudEvent resultEvent = dataSink.received().getFirst().getPayload();
    ResourceEntity resource = getResourcesList(resultEvent).getFirst();

    assertThat(resource.title()).isEqualTo("updated title");
    assertThat(resource.content()).isEqualTo("updated content");

    assertThat(resource.fields())
        .containsEntry("description", "Updated description")
        .containsEntry("author", "John Smith")
        .doesNotContainKey("legacyField");

    assertThat(resource.facets())
        .containsEntry("category", "football")
        .containsEntry("country", "uk")
        .doesNotContainKey("language");
  }


  @Test
  void shouldPublishEventWithoutUnpublishedResource() throws JsonProcessingException {
    // given
    String payload = """
        {
          "title": "test",
          "content": "content",
          "facets": {},
          "fields": {
            "url": "https://example.com",
            "description": "Description",
            "author": "David Beckham"
          }
        }
        """;
    CloudEvent publishedEvent =
        CloudEventUtils.eventWithData(
            DEFAULT_KEY,
            IndexableResource.TYPE_PUBLISHED,
            new IndexableResource(
                payload,
                RESOURCE_TYPE,
                Collections.emptyList()),
            CloudEventUtils.toOffsetDateTime(1));
    sendEvent(publishedEvent);
    requestFeedsGeneration();
    dataSink.clear();

    // when
    CloudEvent unpublishedEvent =
        CloudEventUtils.eventWithData(
            DEFAULT_KEY,
            IndexableResource.TYPE_UNPUBLISHED,
            new IndexableResource(
                payload,
                RESOURCE_TYPE,
                Collections.emptyList()),
            CloudEventUtils.toOffsetDateTime(2));
    sendEvent(unpublishedEvent);
    requestFeedsGeneration();

    // then
    assertThat(dataSink.received()).hasSize(1);
    CloudEvent resultEvent = dataSink.received().getFirst().getPayload();
    List<ResourceEntity> resources = getResourcesList(resultEvent);
    assertThat(resources)
        .noneMatch(resource -> DEFAULT_KEY.equals(resource.subject()));
  }

  /**
   * The method will publish feed if there are not included indexable-resources regardless of
   * defined <i>max-dirty-sequence-count</i> limit in properties
   */
  private void requestFeedsGeneration() throws JsonProcessingException {
    sqlTransformer.publishFeedsIfNeeded();
    sqlTransformer.publishFeedsIfNeeded();
  }

  private void sendEvent(CloudEvent event) {
    indexableResourceSource.send(event);
    waitForEventProcessed(event);
  }

  private void waitForEventProcessed(CloudEvent event) {
    String sqlQuery = "SELECT * FROM indexable_resource";
    int expectedSize =
        IndexableResource.TYPE_UNPUBLISHED.equals(event.getType()) ? 0 : 1;

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () ->
                assertThat(repository.read(sqlQuery))
                    .hasSize(expectedSize));
  }

  private List<ResourceEntity> getResourcesList(CloudEvent event) {
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNotNull();
    String json = data.getContentAsString();
    try {
      return objectMapper.readValue(json,
          new TypeReference<Map<String, List<ResourceEntity>>>() {
        }).get("resources");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void cleanDatabase() {
    repositoryFactory
        .getOrCreate("indexable-resources")
        .executeQuery("DELETE FROM indexable_resource");
  }
}

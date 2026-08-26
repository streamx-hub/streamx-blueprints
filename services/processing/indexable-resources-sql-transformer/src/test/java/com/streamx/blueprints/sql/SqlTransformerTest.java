package com.streamx.blueprints.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.sql.repository.IndexableResourcesRepository;
import com.streamx.blueprints.sql.repository.IndexableSqlResources;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SqlTransformerTest {

  private static final String DEFAULT_KEY = "/test.html";
  private static final String RESOURCE_TYPE = "any";

  private InMemorySource<CloudEvent> indexableResourceSource;
  private InMemorySink<CloudEvent> dataSink;

  @Inject
  @Any
  InMemoryConnector connector;
  @Inject
  SqlTransformer sqlTransformer;
  @Inject
  IndexableResourcesRepository repository;
  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    indexableResourceSource = connector.source(Channels.INDEXABLE_RESOURCES);
    dataSink = connector.sink(Channels.DATA);
    dataSink.clear();
    cleanDatabase();
  }

  // TODO: write test for unpublish

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
    CloudEvent event = CloudEventUtils.eventWithData(
        DEFAULT_KEY,
        IndexableResource.TYPE_PUBLISHED,
        new IndexableResource(payload, RESOURCE_TYPE, Collections.emptyList()),
        CloudEventUtils.toOffsetDateTime(1)
    );

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

    IndexableSqlResources resource = getIndexableSqlResources(resultEvent).getFirst();
    assertThat(resource.title()).isEqualTo("test");
    assertThat(resource.description()).isEqualTo("Description");
    assertThat(resource.author()).isEqualTo("David Beckham");
    assertThat(resource.tags()).isNull();
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
    waitForEventProcessed();
  }

  private void waitForEventProcessed() {
    String sqlQuery = "SELECT * FROM indexable_resources";
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
          assertThat(repository.read(sqlQuery)).hasSize(1);
        }
    );
  }

  private List<IndexableSqlResources> getIndexableSqlResources(CloudEvent event) {
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNotNull();
    String json = data.getContentAsString();
    try {
      return objectMapper.readValue(json,
          new TypeReference<Map<String, List<IndexableSqlResources>>>() {
          }).get("feeds");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void cleanDatabase() {
    repository.executeQuery("DELETE FROM indexable_resources");
  }
}

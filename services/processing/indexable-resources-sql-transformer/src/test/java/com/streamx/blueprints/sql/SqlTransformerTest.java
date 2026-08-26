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
import javax.sql.DataSource;
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
  DataSource dataSource;

  @Inject
  @Any
  InMemoryConnector connector;
  @Inject
  SqlTransformer sqlTransformer;
  @Inject
  IndexableResourcesRepository repository;
  @Inject
  SqlRepositoryFactory repositoryFactory;
  @Inject
  ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    indexableResourceSource = new StatefulInMemorySource(connector,
        Channels.INDEXABLE_RESOURCES, Channels.INDEXABLE_RESORUCES_STATE);
    dataSink = connector.sink(Channels.DATA);
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

    NormalizedResource resource = getNormalizedResources(resultEvent).getFirst();
    assertThat(resource.title()).isEqualTo("test");
    assertThat(resource.fields().get("description")).isEqualTo("Description");
    assertThat(resource.fields().get("author")).isEqualTo("David Beckham");
    assertThat(resource.fields().get("tags")).isNull();
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
    String sqlQuery = "SELECT * FROM indexable_resource";
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
          assertThat(repository.read(sqlQuery)).hasSize(1);
        }
    );
  }

  private List<NormalizedResource> getNormalizedResources(CloudEvent event) {
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNotNull();
    String json = data.getContentAsString();
    try {
      return objectMapper.readValue(json,
          new TypeReference<Map<String, List<NormalizedResource>>>() {
          }).get("resources");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void cleanDatabase() {
    repositoryFactory.get("sqlite").executeQuery("DELETE FROM indexable_resource");
  }
}

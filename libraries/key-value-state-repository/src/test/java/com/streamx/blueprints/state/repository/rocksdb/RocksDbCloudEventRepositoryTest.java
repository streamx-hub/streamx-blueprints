package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.state.PropertyNames;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocksDbCloudEventRepositoryTest extends BaseRocksDbRepositoryTest {

  private static final CloudEventSerializer eventSerializer = new CloudEventJsonSerializer();

  @BeforeAll
  static void setupCloudEventUtils() {
    System.setProperty(PropertyNames.SERVICE_INSTANCE_ID,
        RocksDbCloudEventRepositoryTest.class.getName());
  }

  @Test
  void shouldReturnInsertedCloudEvent() {
    // given
    String key = "event-1";
    CloudEvent inputEvent = createRandomEvent();

    // when
    RocksDbRepository<CloudEvent> repository = createRepository();
    repository.put(key, inputEvent);

    // then
    CloudEventTestUtils.assertSameEvents(repository.get(key), inputEvent);

    // and: should reopen existing repository
    RocksDbRepository<CloudEvent> otherRepositoryInstance = createRepository();
    CloudEventTestUtils.assertSameEvents(otherRepositoryInstance.get(key), inputEvent);
  }

  @Test
  void shouldReturnStreamOfInsertedEvents() {
    // given
    int eventsCount = 9;
    List<String> keys = IntStream.rangeClosed(1, eventsCount)
        .mapToObj(i -> "event#" + i)
        .toList();
    List<CloudEvent> inputEvents = IntStream.rangeClosed(1, eventsCount)
        .mapToObj(i -> createRandomEvent())
        .toList();

    // when
    RocksDbRepository<CloudEvent> repository = createRepository();
    addData(repository, keys, inputEvents);

    // then
    Map<String, CloudEvent> retrievedEvents = getRepositoryEntries(repository);

    assertThat(retrievedEvents.keySet())
        .containsExactlyElementsOf(keys);

    assertThat(retrievedEvents.values())
        .usingElementComparator(
            Comparator.comparing(e -> new String(eventSerializer.serialize(e))))
        .containsExactlyElementsOf(inputEvents);
  }

  private RocksDbRepository<CloudEvent> createRepository() {
    return createRepository("events", CloudEvent.class);
  }

  private static CloudEvent createRandomEvent() {
    return CloudEventUtils.eventWithData(randomString(), randomString(), randomString());
  }

  private static String randomString() {
    byte[] bytes = new byte[10];
    new SecureRandom().nextBytes(bytes);
    return new String(bytes);
  }
}

package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RocksDbCloudEventRepositoryTest extends BaseStateRepositoryTest {

  private static final CloudEventSerializer eventsSerializer = new CloudEventJsonSerializer();
  private static final File dbPath = new File("target/rocksdb-test");

  @BeforeEach
  void init() {
    setConfigProperty(PropertyNames.STATE_BACKEND, "rocksdb");
    setConfigProperty(PropertyNames.STATE_ROCKSDB_PATH, dbPath.getAbsolutePath());
  }

  @BeforeAll
  static void setupCloudEventUtils() {
    System.setProperty("streamx.service.instance-id",
        RocksDbCloudEventRepositoryTest.class.getName());
  }

  @AfterEach
  void stopRocksDb() throws IOException {
    if (dbPath.exists()) {
      FileUtils.forceDelete(dbPath);
    }
  }

  @Test
  void shouldReturnInsertedCloudEvent() {
    // given
    String key = "event-1";
    CloudEvent inputEvent = createRandomEvent();

    // when
    StateRepository<CloudEvent> repository = createRepository();
    repository.put(key, inputEvent);

    // then
    CloudEventTestUtils.assertSameEvents(inputEvent, repository.get(key));

    // and: should reopen existing repository
    StateRepository<CloudEvent> otherRepositoryInstance = createRepository();
    CloudEventTestUtils.assertSameEvents(inputEvent, otherRepositoryInstance.get(key));

    // cleanup
    repository.clear();
  }

  @Test
  void shouldReturnStreamOfInsertedEvents() {
    // given
    int eventsCount = 10;
    List<String> keys = IntStream.rangeClosed(1, eventsCount)
        .mapToObj(i -> "event#" + i)
        .toList();
    List<CloudEvent> inputEvents = IntStream.rangeClosed(1, eventsCount)
        .mapToObj(i -> createRandomEvent())
        .toList();

    // when
    StateRepository<CloudEvent> repository = createRepository();
    addData(repository, keys, inputEvents);

    // then
    Map<String, CloudEvent> retrievedEvents = getRepositoryEntries(repository);

    assertThat(retrievedEvents.keySet())
        .containsExactlyInAnyOrderElementsOf(keys);

    assertThat(retrievedEvents.values())
        .usingElementComparator(
            Comparator.comparing(e -> new String(eventsSerializer.serialize(e))))
        .containsExactlyInAnyOrderElementsOf(inputEvents);

    // cleanup
    repository.clear();
  }

  private StateRepository<CloudEvent> createRepository() {
    var repository = RepositoryFactory.createRepository(config, CloudEvent.class, "events");
    assertThat(repository).isInstanceOf(RocksDbRepository.class);
    return repository;
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

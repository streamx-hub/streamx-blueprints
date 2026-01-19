package com.streamx.blueprints.state.repository.rocksdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.state.StateRepository;
import com.streamx.ce.serialization.CloudEventDeserializer;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonDeserializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.config.Config;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

public class RocksDbRepository<T> implements StateRepository<T> {

  public static final String BACKEND = "rocksdb";

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final CloudEventSerializer eventSerializer = new CloudEventJsonSerializer();
  private static final CloudEventDeserializer eventDeserializer = new CloudEventJsonDeserializer();
  private static final Set<String> cloudEventKeys = ConcurrentHashMap.newKeySet();

  private final RocksDB rocksDb;
  private final Class<T> valueClass;

  public RocksDbRepository(Config config, Class<T> valueClass, String instanceId, String identifier) {
    this.rocksDb = RocksDbManager.getOrCreateDb(config, instanceId, identifier);
    this.valueClass = valueClass;
  }

  @Override
  public void put(@Nonnull String key, @Nonnull T value) {
    try {
      if (value instanceof CloudEvent event) {
        synchronized (this) {
          cloudEventKeys.add(key);
          rocksDb.put(key.getBytes(), eventSerializer.serialize(event));
        }
      } else {
        rocksDb.put(key.getBytes(), mapper.writeValueAsBytes(value));
      }
    } catch (Exception e) {
      throw new RuntimeException("Error putting entry with key " + key + " to RocksDB", e);
    }
  }

  @Nullable
  @Override
  public T get(@Nonnull String key) {
    try {
      byte[] value = rocksDb.get(key.getBytes());
      return deserializeValue(key, value);
    } catch (Exception e) {
      throw new RuntimeException("Error getting value of key " + key + " from RocksDB", e);
    }
  }

  protected T deserializeValue(String key, byte[] value) {
    try {
      if (value == null) {
        return null;
      }
      if (cloudEventKeys.contains(key)) {
        return (T) eventDeserializer.deserialize(value);
      }
      return mapper.readValue(value, valueClass);
    } catch (Exception e) {
      throw new RuntimeException("Error deserializing value of key " + key + " from RocksDB", e);
    }
  }

  @Override
  public Stream<Entry<String, T>> entries() {
    RocksIterator iterator = rocksDb.newIterator();
    iterator.seekToFirst();

    Spliterator<Map.Entry<String, T>> spliterator = Spliterators.spliteratorUnknownSize(
        new RocksDbIteratorWrapper(iterator),
        Spliterator.ORDERED
    );
    return StreamSupport
        .stream(spliterator, false)
        .onClose(iterator::close);
  }

  @Override
  public void remove(@Nonnull String key) {
    try {
      synchronized (this) {
        rocksDb.delete(key.getBytes());
        cloudEventKeys.remove(key);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error removing entry with key " + key + " from RocksDB", e);
    }
  }

  @Override
  public void clear() {
    try (RocksIterator it = rocksDb.newIterator()) {
      for (it.seekToFirst(); it.isValid(); it.next()) {
        String key = new String(it.key());
        remove(key);
      }
    }
  }

  class RocksDbIteratorWrapper implements Iterator<Entry<String, T>> {

    private final RocksIterator rocksIterator;

    RocksDbIteratorWrapper(RocksIterator rocksIterator) {
      this.rocksIterator = rocksIterator;
    }

    @Override
    public boolean hasNext() {
      return rocksIterator.isValid();
    }

    @Override
    public Map.Entry<String, T> next() {
      String key = new String(rocksIterator.key());
      byte[] value = rocksIterator.value();
      rocksIterator.next();
      return Map.entry(key, deserializeValue(key, value));
    }
  }
}


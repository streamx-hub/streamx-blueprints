package com.streamx.blueprints.state.repository.rocksdb;

import com.streamx.blueprints.state.StateRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

public class RocksDbRepository<T> implements StateRepository<T> {

  public static final String BACKEND = "rocksdb";

  private final RocksDB rocksDb;
  private final Class<T> valueClass;

  public RocksDbRepository(RocksDB rocksDb, Class<T> valueClass) {
    this.rocksDb = rocksDb;
    this.valueClass = valueClass;
  }

  @Override
  public void put(@Nonnull String key, @Nonnull T value) {
    try {
      byte[] serialized = SerializationUtils.toByteArray(value);
      rocksDb.put(key.getBytes(), serialized);
    } catch (Exception e) {
      throw new RuntimeException("Error putting entry with key " + key + " to RocksDB", e);
    }
  }

  @Nullable
  @Override
  public T get(@Nonnull String key) {
    try {
      byte[] keyBytes = key.getBytes();
      if (!rocksDb.keyExists(keyBytes)) {
        return null;
      }
      byte[] value = rocksDb.get(keyBytes);
      return deserializeValue(key, value);
    } catch (Exception e) {
      throw new RuntimeException("Error getting value of key " + key + " from RocksDB", e);
    }
  }

  private T deserializeValue(String key, byte[] value) {
    try {
      return SerializationUtils.fromByteArray(value, valueClass);
    } catch (Exception e) {
      throw new RuntimeException("Error deserializing value of key " + key + " from RocksDB", e);
    }
  }

  @Override
  public Stream<Entry<String, T>> entries() {
    return rocksDbStream((key, value) -> Map.entry(key, deserializeValue(key, value)));
  }

  @Override
  public Stream<String> keys() {
    return rocksDbStream((key, value) -> key);
  }

  @Override
  public Stream<T> values() {
    return rocksDbStream(this::deserializeValue);
  }

  private <U> Stream<U> rocksDbStream(BiFunction<String, byte[], U> valueMapper) {
    RocksIterator iterator = rocksDb.newIterator();
    iterator.seekToFirst();

    Spliterator<U> spliterator = Spliterators.spliteratorUnknownSize(
        new Iterator<>() {
          @Override
          public boolean hasNext() {
            return iterator.isValid();
          }

          @Override
          public U next() {
            String key = new String(iterator.key());
            byte[] value = iterator.value();
            iterator.next();
            return valueMapper.apply(key, value);
          }
        },
        Spliterator.ORDERED
    );
    return StreamSupport
        .stream(spliterator, false)
        .onClose(iterator::close);
  }

  @Override
  public void remove(@Nonnull String key) {
    try {
      rocksDb.delete(key.getBytes());
    } catch (Exception e) {
      throw new RuntimeException("Error removing entry with key " + key + " from RocksDB", e);
    }
  }

  @Override
  public void removeByValue(@Nonnull T value) {
    try (RocksIterator it = rocksDb.newIterator()) {
      for (it.seekToFirst(); it.isValid(); it.next()) {
        String key = new String(it.key());
        T currentValue = deserializeValue(key, it.value());
        if (value.equals(currentValue)) {
          remove(key);
        }
      }
    }
  }

}


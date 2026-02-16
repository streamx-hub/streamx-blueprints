package com.streamx.blueprints.state;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * The {@code StateRepository} interface defines the contract for a repository that stores and
 * retrieves key-value data.
 *
 * @param <T> Value type
 */
public interface StateRepository<T> {

  /**
   * Puts value into the repository using the given key.
   *
   * @param key   key used while storing the message
   * @param value the value to be stored in the repository
   */
  void put(@Nonnull String key, @Nonnull T value);

  /**
   * Retrieves value from the repository based on the specified key.
   *
   * @param key the key
   * @return data associated with the key, or {@code null} if the key is not found.
   */
  @Nullable
  T get(@Nonnull String key);

  /**
   * Returns a sequential Stream of all entries
   *
   * @return The stream of entries
   */
  Stream<Entry<String, T>> entries();

  /**
   * Returns a sequential Stream of all keys
   *
   * @return The stream of keys
   */
  Stream<String> keys();

  /**
   * Returns a sequential Stream of all values
   *
   * @return The stream of values
   */
  Stream<T> values();

  /**
   * Removes entry associated with the given key
   *
   * @param key the key
   */
  void remove(@Nonnull String key);

  /**
   * Removes all entries associated with the given value
   *
   * @param value the value. It must implement the equals/hashCode contract
   *
   * @apiNote <b>Performance Warning:</b> This implementation performs a full scan of the underlying
   * storage. It is discouraged for use with large datasets.
   */
  void removeByValue(@Nonnull T value);
}

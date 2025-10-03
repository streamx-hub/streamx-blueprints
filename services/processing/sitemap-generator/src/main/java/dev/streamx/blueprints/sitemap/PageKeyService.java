package dev.streamx.blueprints.sitemap;

import com.google.re2j.Pattern;
import java.util.List;
import java.util.stream.Stream;

class PageKeyService {

  private final List<Pattern> patterns;

  public PageKeyService(String... patterns) {
    this.patterns = Stream.of(patterns)
        .map(Pattern::compile)
        .toList();
  }

  /**
   * This method checks if a given key is supported for sitemap generation.
   * It does so by matching the key against a list of patterns.
   * If no patterns are defined, all keys are considered supported.
   *
   * @param key the key to check for support
   * @return true if the key is supported, false otherwise
   */
  boolean isSupportedKey(String key) {
    if (patterns.isEmpty()) {
      return true;
    }
    return patterns.stream()
        .anyMatch(
            pattern ->
                pattern.matcher(key)
                    .find()
        );
  }
}

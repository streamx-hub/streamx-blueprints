package com.streamx.blueprints.index.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-producer.search-feed-extractor")
public interface SearchFeedExtractorConfig {

  Extractor xpath();

  interface Extractor {

    Map<String, Field> fields();
  }

  interface Field {

    boolean facet();

    Optional<String> elementSelector();

    Optional<String> keySelector();

    Optional<String> valueSelector();

    Optional<String> key();

    Optional<String> value();

    List<Processor> keyProcessors();

    List<Processor> valueProcessors();

    @WithDefault("false")
    boolean noIndex();
  }

  interface Processor {

    String name();

    Optional<String> config();
  }
}

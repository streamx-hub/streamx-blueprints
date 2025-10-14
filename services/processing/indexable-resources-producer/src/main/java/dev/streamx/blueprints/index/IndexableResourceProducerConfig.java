package dev.streamx.blueprints.index;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.content.parser.urlinclude.UrlIncludeCollector;
import dev.streamx.content.parser.urlinclude.UrlIncludeRemover;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;

@Dependent
public class IndexableResourceProducerConfig {

  @ApplicationScoped
  ObjectMapper objectMapper() {
    return new ObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
    );
  }

  @ApplicationScoped
  UrlIncludeCollector urlIncludeCollector() {
    return new UrlIncludeCollector();
  }

  @ApplicationScoped
  UrlIncludeRemover urlIncludeRemover() {
    return new UrlIncludeRemover();
  }
}

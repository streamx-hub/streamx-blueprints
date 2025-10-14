package dev.streamx.blueprints.index;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import java.util.Optional;

abstract class AbstractIndexableResourceProducer {

  static final String EXTENSION_NAME_INDEXABLE = "indexable";

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  protected boolean isIndexable(CloudEvent event) {
    return Optional.ofNullable(event.getExtension(EXTENSION_NAME_INDEXABLE))
        .map(Object::toString)
        .map(Boolean::parseBoolean)
        .orElse(isIndexableDefault());
  }

  protected boolean isIndexableDefault() {
    return true;
  }

}

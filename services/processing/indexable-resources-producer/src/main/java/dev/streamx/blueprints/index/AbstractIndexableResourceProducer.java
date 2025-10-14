package dev.streamx.blueprints.index;

import dev.streamx.metadata.Properties;
import java.util.Optional;

abstract class AbstractIndexableResourceProducer {

  public static final String MESSAGE_PN_INDEXABLE = "indexable";

  protected boolean isIndexable(Properties properties) {
    return Optional.ofNullable(properties)
        .filter(props -> props.getValues().containsKey(MESSAGE_PN_INDEXABLE))
        .map(props -> props.get(MESSAGE_PN_INDEXABLE))
        .map(Boolean::parseBoolean)
        .orElse(isIndexableDefault());
  }

  protected boolean isIndexableDefault() {
    return true;
  }

}

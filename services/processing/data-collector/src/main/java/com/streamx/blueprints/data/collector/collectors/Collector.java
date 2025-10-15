package com.streamx.blueprints.data.collector.collectors;

import java.util.List;

public interface Collector {

  default void acceptDataKey(String key) {

  }

  List<CollectedOutput> collect();

  record CollectedOutput(String key, String dataContent, String dataType) {

    public CollectedOutput(String key, String dataContent) {
      this(key, dataContent, null);
    }
  }

}

package com.streamx.blueprints.data.collector.collectors;

import com.streamx.blueprints.data.Data;
import java.util.List;

public interface Collector {

  /**
   * Process the data and event type. Returns flag if the collector output might be affected by the
   * processed data.
   *
   * @return true if the collector output might be affected by the processed data, false otherwise
   */
  boolean process(String key, Data data, String eventType);

  List<CollectedOutput> collect();

  record CollectedOutput(String key, String dataContent, String dataType) {

    public CollectedOutput(String key, String dataContent) {
      this(key, dataContent, null);
    }
  }

}

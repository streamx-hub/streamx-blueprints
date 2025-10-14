package com.streamx.blueprints.data.collector.collectors;

import dev.streamx.blueprints.data.Data;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import java.util.List;

public interface Collector {

  /**
   * Process the data and action. Returns flag if the collector output might be affected by the
   * processed data.
   *
   * @return true if the collector output might be affected by the processed data, false otherwise
   */
  boolean process(Key key, Data data, Action action);

  List<CollectedOutput> collect();

  record CollectedOutput(Key key, String type, Data data) {

    public CollectedOutput(Key key, Data data) {
      this(key, null, data);
    }
  }

}

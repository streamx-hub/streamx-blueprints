package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.Collector;
import dev.streamx.blueprints.data.Data;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import java.util.ArrayList;
import java.util.List;

public class TestCollector implements Collector {

  public static final String TEST_OUTPUT_KEY = "test-collector-output-key";
  public static final String TEST_OUTPUT_TYPE = "test-collector-output-type";

  private final List<String> processedKeys = new ArrayList<>();

  @Override
  public boolean process(Key key, Data data, Action action) {
    processedKeys.add(key.getValue());
    return true;
  }

  @Override
  public List<CollectedOutput> collect() {
    return List.of(new CollectedOutput(Key.of(TEST_OUTPUT_KEY), TEST_OUTPUT_TYPE,
        new Data(String.join(",", processedKeys))));
  }
}

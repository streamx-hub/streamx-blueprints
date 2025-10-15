package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.Collector;
import java.util.ArrayList;
import java.util.List;

public class TestCollector implements Collector {

  public static final String TEST_OUTPUT_KEY = "test-collector-output-key";
  public static final String TEST_OUTPUT_TYPE = "test-collector-output-type";

  private final List<String> processedKeys = new ArrayList<>();

  @Override
  public void acceptDataKey(String key) {
    processedKeys.add(key);
  }

  @Override
  public List<CollectedOutput> collect() {
    String content = String.join(",", processedKeys);
    return List.of(new CollectedOutput(TEST_OUTPUT_KEY, content, TEST_OUTPUT_TYPE));
  }
}

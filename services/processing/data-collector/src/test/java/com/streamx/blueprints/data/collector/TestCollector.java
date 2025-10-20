package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.collectors.Collector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TestCollector implements Collector {

  public static final String TEST_OUTPUT_KEY = "test-collector-output-key";
  public static final String TEST_OUTPUT_TYPE = "test-collector-output-type";

  private static final Predicate<String> PROCESSABLE_KEY_PREDICATE = key -> !key.contains("skip");

  private final List<String> processedKeys = new ArrayList<>();

  @Override
  public boolean process(String key, Data data, String eventType) {
    if (PROCESSABLE_KEY_PREDICATE.test(key)) {
      processedKeys.add(key);
      return true;
    }
    return false;
  }

  @Override
  public List<CollectedOutput> collect() {
    String content = String.join(",", processedKeys);
    return List.of(new CollectedOutput(TEST_OUTPUT_KEY, content, TEST_OUTPUT_TYPE));
  }
}

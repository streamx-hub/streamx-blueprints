package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class TestCollectorFactory implements CollectorFactory {

  private Map<String, String> properties;

  @Override
  public String id() {
    return "test-collector";
  }

  @Override
  public Collector create(DataFilter dataFilter, Map<String, String> properties) {
    this.properties = properties;
    return new TestCollector();
  }

  public Map<String, String> getProperties() {
    return properties;
  }
}

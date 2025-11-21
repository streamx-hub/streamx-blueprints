package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.configuration.CollectorProperties;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestCollectorFactory implements CollectorFactory {

  private CollectorProperties properties;

  @Override
  public String id() {
    return "test-collector";
  }

  @Override
  public Collector create(DataFilter dataFilter, CollectorProperties properties) {
    this.properties = properties;
    return new TestCollector();
  }

  public CollectorProperties getProperties() {
    return properties;
  }
}

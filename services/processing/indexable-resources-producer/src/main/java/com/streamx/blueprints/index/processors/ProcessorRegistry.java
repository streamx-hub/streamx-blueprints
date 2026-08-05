package com.streamx.blueprints.index.processors;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import com.streamx.blueprints.index.processors.string.impl.LowercaseProcessor;
import com.streamx.blueprints.index.processors.string.impl.RemoveStartProcessor;
import com.streamx.blueprints.index.processors.string.impl.SplitProcessor;
import com.streamx.blueprints.index.processors.string.impl.TrimProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class ProcessorRegistry {

  private final Map<String, StringProcessor> processors = Map.of(
      "trim", new TrimProcessor(),
      "lowercase", new LowercaseProcessor(),
      "removeStart", new RemoveStartProcessor(),
      "split", new SplitProcessor()
  );

  public StringProcessor get(String name) {
    StringProcessor processor = processors.get(name);

    if (processor == null) {
      throw new IllegalArgumentException(
          "Unknown processor: " + name
      );
    }

    return processor;
  }
}

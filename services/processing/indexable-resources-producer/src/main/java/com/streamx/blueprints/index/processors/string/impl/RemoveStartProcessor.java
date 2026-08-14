package com.streamx.blueprints.index.processors.string.impl;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.Strings;

@ApplicationScoped
public class RemoveStartProcessor implements StringProcessor {

  @Override
  public List<String> process(List<String> input, String config) {
    return Optional.ofNullable(input)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(string -> Strings.CS.removeStart(string, config))
        .toList();
  }

  @Override
  public String getName() {
    return "removeStart";
  }
}

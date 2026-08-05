package com.streamx.blueprints.index.processors.string.impl;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import java.util.List;
import org.apache.commons.lang3.Strings;

public class RemoveStartProcessor implements StringProcessor {

  @Override
  public List<String> process(List<String> input, String config) {
    return input.stream()
        .map(string -> Strings.CS.removeStart(string, config))
        .toList();
  }
}

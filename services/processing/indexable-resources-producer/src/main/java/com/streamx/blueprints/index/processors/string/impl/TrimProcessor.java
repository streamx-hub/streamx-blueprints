package com.streamx.blueprints.index.processors.string.impl;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import java.util.List;

public class TrimProcessor implements StringProcessor {

  @Override
  public List<String> process(List<String> input, String config) {
    return input.stream()
        .map(String::trim)
        .toList();
  }
}

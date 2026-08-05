package com.streamx.blueprints.index.processors.string.impl;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SplitProcessor implements StringProcessor {

  @Override
  public List<String> process(List<String> input, String config) {
    return input.stream()
        .flatMap(string -> Arrays.stream(string.split(Pattern.quote(config))))
        .toList();
  }
}

package com.streamx.blueprints.index.processors.string.impl;

import com.streamx.blueprints.index.processors.string.StringProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class SplitProcessor implements StringProcessor {

  @Override
  public List<String> process(List<String> input, String config) {
    return Optional.ofNullable(input)
        .orElseGet(Collections::emptyList)
        .stream()
        .flatMap(string -> Arrays.stream(string.split(Pattern.quote(config))))
        .toList();
  }

  @Override
  public String getName() {
    return "split";
  }
}

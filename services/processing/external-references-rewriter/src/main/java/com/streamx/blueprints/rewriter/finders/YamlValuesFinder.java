package com.streamx.blueprints.rewriter.finders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Set;

public class YamlValuesFinder extends JsonValuesFinder {

  private static final ObjectMapper YAML_READER = new ObjectMapper(new YAMLFactory());

  private static final ObjectMapper JSON_WRITER = new ObjectMapper();

  @Override
  public Set<String> doFindMatchingValues(String inputYaml, List<String> jsonPaths)
      throws Exception {
    Object obj = YAML_READER.readValue(inputYaml, Object.class);
    String inputJson = JSON_WRITER.writeValueAsString(obj);
    return super.doFindMatchingValues(inputJson, jsonPaths);
  }
}

package com.streamx.blueprints.rendering.engine.generator;

import java.util.Map;

public interface OutputGenerator {

  String generate(String template, Map<String, Object> data) throws GeneratorException;

  void invalidateCache();

}

package dev.streamx.blueprints.rendering.engine.generator;

import java.util.Map;

public interface OutputGenerator {

  byte[] generate(String template, Map<String, Object> data) throws GeneratorException;

  void invalidateCache();

}

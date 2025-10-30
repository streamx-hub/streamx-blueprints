package com.streamx.blueprints.rendering.engine.generator.impl;

import com.streamx.blueprints.rendering.engine.generator.GeneratorException;
import com.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.loader.StringLoader;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

@ApplicationScoped
public class PebbleOutputGenerator implements OutputGenerator {

  private final PebbleEngine engine = createPebbleEngine();

  @Override
  public String generate(String template, Map<String, Object> data) throws GeneratorException {
    StringWriter writer = new StringWriter();
    try {
      engine.getTemplate(template).evaluate(writer, data);
    } catch (PebbleException | IOException e) {
      throw new GeneratorException("Could not evaluate template", e);
    }
    return writer.toString();
  }

  @Override
  public void invalidateCache() {
    engine.getTemplateCache().invalidateAll();
    engine.getTagCache().invalidateAll();
  }

  private PebbleEngine createPebbleEngine() {
    return new PebbleEngine.Builder().loader(new StringLoader())
        .newLineTrimming(false)
        .cacheActive(true)
        .build();
  }

}

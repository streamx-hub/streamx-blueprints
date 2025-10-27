package com.streamx.blueprints.rendering.engine.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamx.blueprints.rendering.engine.generator.GeneratorException;
import io.pebbletemplates.pebble.error.ParserException;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PebbleOutputGeneratorTest {

  private final PebbleOutputGenerator generator = new PebbleOutputGenerator();

  @AfterEach
  void invalidatePebbleCache() {
    generator.invalidateCache();
  }

  @Test
  void shouldGenerateOutput() throws GeneratorException {
    // given
    String template = "Hello {{name}}, your number is {{number}}";
    Map<String, Object> data = Map.of(
        "name", "John",
        "number", 123
    );

    // when
    String output = generator.generate(template, data);

    // then
    assertThat(output).isEqualTo("Hello John, your number is 123");
  }

  @Test
  void shouldThrowExceptionOnInvalidInput() {
    // given
    String template = "Hello {{name";

    // when & then
    assertThatThrownBy(() -> generator.generate(template, Collections.emptyMap()))
        .isInstanceOf(GeneratorException.class)
        .hasMessage("Could not evaluate template")
        .hasCauseInstanceOf(ParserException.class);
  }

}
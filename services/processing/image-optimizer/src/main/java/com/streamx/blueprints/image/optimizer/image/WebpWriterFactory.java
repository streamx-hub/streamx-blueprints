package com.streamx.blueprints.image.optimizer.image;

import com.sksamuel.scrimage.webp.WebpWriter;
import com.streamx.blueprints.image.optimizer.configuration.Configuration.WebpConversion;
import java.util.Optional;

final class WebpWriterFactory {

  private WebpWriterFactory() {
    // no instances
  }

  private static final int INTEGER_PARAM_DEFAULT_VALUE = -1;
  private static final boolean BOOLEAN_PARAM_DEFAULT_VALUE = false;

  static WebpWriter createWriterInstance(WebpConversion configuration) {
    return new WebpWriter(
        getOrDefault(configuration.speed()),
        getOrDefault(configuration.quality()),
        getOrDefault(configuration.method()),
        getOrDefault(configuration.lossless()),
        getOrDefault(configuration.noAlpha()),
        getOrDefault(configuration.multiThread())
    );
  }

  private static int getOrDefault(Integer i) {
    return Optional.ofNullable(i).orElse(INTEGER_PARAM_DEFAULT_VALUE);
  }

  private static boolean getOrDefault(Boolean b) {
    return Optional.ofNullable(b).orElse(BOOLEAN_PARAM_DEFAULT_VALUE);
  }
}

package com.streamx.blueprints.image.generator.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "streamx.blueprints.optimized-images-generator")
public interface Configuration {

  String processedImagePathPattern();

  @WithDefault("-optimized")
  String optimizedImageFileNameSuffix();

  WebpConversion webpConversion();

  interface WebpConversion {

    @WithDefault("6")
    int speed();

    @WithDefault("75")
    int quality();

    @WithDefault("4")
    int method();

    @WithDefault("false")
    boolean lossless();

    @WithDefault("false")
    boolean noAlpha();
  }
}

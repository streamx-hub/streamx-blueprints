package com.streamx.blueprints.image.optimizer.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "streamx.blueprints.image-optimizer")
public interface Configuration {

  String optimizedFilePathsPattern();

  @WithDefault("-optimized")
  String optimizedImageFileNameSuffix();

  String adjustedPagePathsPattern();

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

    @WithDefault("false")
    boolean multiThread();
  }
}

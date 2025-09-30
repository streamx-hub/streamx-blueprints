package com.streamx.blueprints.image.optimization.configuration;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "streamx.blueprints.image-optimization-processing-service")
public interface Configuration {

  String optimizedFilePathsPattern();

  String optimizedImageFileNameSuffix();

  String adjustedPagePathsPattern();

  Integer webpConversionSpeed();

  Integer webpConversionQuality();

  Integer webpConversionMethod();

  Boolean webpConversionLossless();

  Boolean webpConversionNoAlpha();

  Boolean webpConversionMultiThread();
}

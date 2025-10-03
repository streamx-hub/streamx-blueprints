package com.streamx.blueprints.image.optimizer.configuration;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "streamx.blueprints.image-optimizer")
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

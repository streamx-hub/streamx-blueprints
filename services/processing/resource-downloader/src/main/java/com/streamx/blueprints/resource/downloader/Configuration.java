package com.streamx.blueprints.resource.downloader;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;
import java.util.regex.Pattern;

@ConfigMapping(prefix = "streamx.blueprints.resource-downloader")
public interface Configuration {

  @WithDefault("1500")
  int headTimeoutMillis();

  @WithDefault("5000")
  int downloadTimeoutMillis();

  @WithDefault("30000")
  long repeatIntervalMillis();

  Optional<Pattern> repeatableUrlPattern();

}

package com.streamx.blueprints.resource.downloader;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;
import java.util.regex.Pattern;

@ConfigMapping(prefix = "streamx.blueprints.resource-downloader")
public interface Configuration {

  @WithDefault("1500")
  int headTimeoutMilliseconds();

  @WithDefault("5000")
  int downloadTimeoutMilliseconds();

  Optional<Pattern> urlRepeatingPattern();

  @WithDefault("30000")
  long repeatIntervalMillis();
}

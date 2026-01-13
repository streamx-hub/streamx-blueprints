package com.streamx.blueprints.resource.downloader;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "streamx.blueprints.resource-downloader")
public interface Configuration {

  @WithDefault("1500")
  int headTimeoutMilliseconds();

  @WithDefault("5000")
  int downloadTimeoutMilliseconds();

  @WithDefault("30000")
  long repeatIntervalMillis();
}

package com.streamx.blueprints.web.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.web-server-sink")
public interface Configuration {

  Optional<String> defaultNamespace();

  Optional<List<String>> htmlResourceTypes();

  @WithDefault("/tmp/streamx")
  String storageRootDirectory();
}

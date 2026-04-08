package com.streamx.blueprints.data;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
public record Config(
    Map<String, String> configMap) {

  public static final String CONFIG_APPLY_TYPE =
      "com.streamx.blueprints.config.applied.v1";

}

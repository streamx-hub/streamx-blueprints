package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;

@RegisterForReflection
public abstract class Typed {

  @Nullable
  private final String type;

  @JsonCreator
  public Typed(@JsonProperty("type") String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}

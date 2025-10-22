package com.streamx.blueprints.data;

import jakarta.annotation.Nullable;

public abstract class Typed {

  @Nullable
  private final String type;

  protected Typed(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}

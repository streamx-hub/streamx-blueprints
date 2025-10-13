package com.streamx.blueprints.data;

public abstract class Typed {

  private final String type;

  protected Typed(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}

package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

@RegisterForReflection
public class OptimizedAsset extends Asset {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.optimized-asset.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.optimized-asset.unpublished.v1";

  private final String originalPath;

  @JsonCreator
  public OptimizedAsset(
      @JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type,
      @JsonProperty("originalPath") String originalPath) {
    super(content, type);
    this.originalPath = originalPath;
  }

  public OptimizedAsset(byte[] content, String type, String originalPath) {
    super(content, type);
    this.originalPath = originalPath;
  }

  public String getOriginalPath() {
    return originalPath;
  }
}

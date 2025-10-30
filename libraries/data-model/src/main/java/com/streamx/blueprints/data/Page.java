package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

@RegisterForReflection
public class Page extends WebResource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.page.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.page.unpublished.v1";

  @JsonCreator
  public Page(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(content, type);
  }

  public Page(String content, String type) {
    super(content, type);
  }
}

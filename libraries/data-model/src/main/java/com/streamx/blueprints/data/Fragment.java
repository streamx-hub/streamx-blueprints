package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents fragment of web content. Can be included on {@link Page}
 */
@RegisterForReflection
public class Fragment extends WebResource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.fragment.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.fragment.unpublished.v1";

  @JsonCreator
  public Fragment(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(content, type);
  }

  public Fragment(String content, String type) {
    super(content, type);
  }
}

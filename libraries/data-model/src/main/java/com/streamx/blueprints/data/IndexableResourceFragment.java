package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents JSON fragment to be indexed in search.
 */
@RegisterForReflection
public class IndexableResourceFragment extends JsonResource {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.indexable-resource-fragment.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.indexable-resource-fragment.unpublished.v1";

  @JsonCreator
  public IndexableResourceFragment(@JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type) {
    super(content, type);
  }

  public IndexableResourceFragment(String content, String type) {
    super(content, type);
  }
}

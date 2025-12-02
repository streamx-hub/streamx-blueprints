package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Represents JSON data to be indexed in search.
 */
@RegisterForReflection
public class IndexableResource extends JsonResource {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.indexable-resource.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.indexable-resource.unpublished.v1";

  private final List<String> fragmentKeys;

  @JsonCreator
  public IndexableResource(@JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type, @JsonProperty("fragmentKeys") List<String> fragmentKeys) {
    super(content, type);
    this.fragmentKeys = fragmentKeys;
  }

  public IndexableResource(String content, String type, List<String> fragmentKeys) {
    super(content, type);
    this.fragmentKeys = fragmentKeys;
  }

  public List<String> getFragmentKeys() {
    return fragmentKeys;
  }
}

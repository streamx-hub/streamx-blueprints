package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Represents JSON data to be indexed in search.
 */
public class IndexableResource extends JsonResource {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.indexable-resource.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.indexable-resource.unpublished.v1";

  private final Set<String> fragmentKeys;

  @JsonCreator
  public IndexableResource(@JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type, @JsonProperty("fragmentKeys") Set<String> fragmentKeys) {
    super(content, type);
    this.fragmentKeys = fragmentKeys;
  }

  public IndexableResource(String content, String type, Set<String> fragmentKeys) {
    super(content, type);
    this.fragmentKeys = fragmentKeys;
  }

  public Set<String> getFragmentKeys() {
    return fragmentKeys;
  }
}

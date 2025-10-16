package com.streamx.blueprints.data;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Represents data to be indexed in search.
 */
public class IndexableResource extends Resource {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.indexable-resource.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.indexable-resource.unpublished.v1";

  private final Set<String> fragmentKeys;

  public IndexableResource(ByteBuffer content, Set<String> fragmentKeys) {
    super(content);
    this.fragmentKeys = fragmentKeys;
  }

  public IndexableResource(byte[] content, Set<String> fragmentKeys) {
    super(content);
    this.fragmentKeys = fragmentKeys;
  }

  public Set<String> getFragmentKeys() {
    return fragmentKeys;
  }
}

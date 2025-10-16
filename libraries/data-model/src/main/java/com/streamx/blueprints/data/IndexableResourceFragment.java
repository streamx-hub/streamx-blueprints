package com.streamx.blueprints.data;

import java.nio.ByteBuffer;

/**
 * Represents fragment to be indexed in search.
 */
public class IndexableResourceFragment extends Resource {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.indexable-resource-fragment.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.indexable-resource-fragment.unpublished.v1";

  public IndexableResourceFragment(ByteBuffer content) {
    super(content);
  }

  public IndexableResourceFragment(byte[] content) {
    super(content);
  }

  public IndexableResourceFragment(String content) {
    super(content);
  }
}

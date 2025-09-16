package dev.streamx.blueprints.data;

import java.nio.ByteBuffer;

/**
 * Represents object containing data to be injected into layout.
 */
public class Composition extends Resource {

  public static final String TYPE_COMPOSITION_PUBLISHED = "dev.streamx.blueprints.composition.published.v1";
  public static final String TYPE_COMPOSITION_UNPUBLISHED = "dev.streamx.blueprints.composition.unpublished.v1";

  private String layoutKey;

  public Composition(ByteBuffer content, String type, String layoutKey) {
    super(content, type);
    this.layoutKey = layoutKey;
  }

  public Composition(String content, String type, String layoutKey) {
    super(content, type);
    this.layoutKey = layoutKey;
  }

  public String getLayoutKey() {
    return layoutKey;
  }
}

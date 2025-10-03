package com.streamx.blueprints.rewriter.data;

import java.util.LinkedHashSet;
import java.util.Set;

public class ExternalResource {

  /**
   * Paths from parent resource content: may come in different forms (absolute / relative)
   */
  private final Set<String> paths = new LinkedHashSet<>();
  private final String absoluteUrl;
  private final String streamxKey;

  public ExternalResource(String path, String absoluteUrl, String streamxKey) {
    this.absoluteUrl = absoluteUrl;
    this.streamxKey = streamxKey;
    addPath(path);
  }

  public void addPath(String path) {
    paths.add(path);
  }

  public Set<String> getPaths() {
    return Set.copyOf(paths);
  }

  public String getAbsoluteUrl() {
    return absoluteUrl;
  }

  public String getStreamxKey() {
    return streamxKey;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ExternalResource res && getAbsoluteUrl().equals(res.getAbsoluteUrl());
  }

  @Override
  public int hashCode() {
    return getAbsoluteUrl().hashCode();
  }
}

package com.streamx.blueprints.opensearch;

import java.util.Objects;

public class ExampleIndexableResourceContent {

  private String title;
  private String content;

  public ExampleIndexableResourceContent() {
  }

  public ExampleIndexableResourceContent(String title, String content) {
    this.title = title;
    this.content = content;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (ExampleIndexableResourceContent) obj;
    return Objects.equals(this.title, that.title)
        && Objects.equals(this.content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, content);
  }

  @Override
  public String toString() {
    return "IndexableResourceContent["
        + "title=" + title + ", "
        + "content=" + content + ']';
  }
}

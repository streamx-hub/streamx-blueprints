package dev.streamx.blueprints.opensearch;

import java.util.Objects;

public class ExampleDataContent {

  private String id;
  private String category;

  public ExampleDataContent() {
  }

  public ExampleDataContent(String id, String category) {
    this.id = id;
    this.category = category;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExampleDataContent that = (ExampleDataContent) o;
    return Objects.equals(id, that.id) && Objects.equals(category, that.category);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, category);
  }

  @Override
  public String toString() {
    return "ExampleDataContent{"
        + "id='" + id + '\''
        + ", category='" + category + '\''
        + '}';
  }
}

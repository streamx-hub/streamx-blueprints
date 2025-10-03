package dev.streamx.blueprints.sitemap;

import java.util.Objects;
import org.apache.avro.specific.AvroGenerated;

@AvroGenerated
public class PageEntry {
  private String pageName;
  private boolean published;

  public PageEntry(String pageName, boolean published) {
    this.pageName = pageName;
    this.published = published;
  }

  private PageEntry() {
    // needed for Avro serialization
  }

  public String getPageName() {
    return pageName;
  }

  public boolean isPublished() {
    return published;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PageEntry pageEntry = (PageEntry) o;
    return published == pageEntry.published && Objects.equals(pageName, pageEntry.pageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pageName, published);
  }

  @Override
  public String toString() {
    return "PageEntry{"
        + "pageName='" + pageName + '\''
        + ", published=" + published
        + '}';
  }
}

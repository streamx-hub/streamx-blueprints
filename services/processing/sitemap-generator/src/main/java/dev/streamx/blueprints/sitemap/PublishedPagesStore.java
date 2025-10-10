package dev.streamx.blueprints.sitemap;

import com.streamx.blueprints.data.Page;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PublishedPagesStore {

  private static final Map<String, PublishedPage> publishedPages = new ConcurrentHashMap<>();

  public void register(String pageKey, @Nullable OffsetDateTime time, String eventType) {
    if (Page.TYPE_PUBLISHED.equals(eventType)) {
      PublishedPage page = new PublishedPage(pageKey, time);
      publishedPages.put(pageKey, page);
    } else {
      publishedPages.remove(pageKey);
    }
  }

  public Collection<PublishedPage> getEntries() {
    return publishedPages.values();
  }

  void clear() {
    publishedPages.clear();
  }
}

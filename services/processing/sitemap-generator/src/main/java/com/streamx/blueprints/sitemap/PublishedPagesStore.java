package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class PublishedPagesStore {

  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<PublishedPage> publishedPages;

  @PostConstruct
  void initRepository() {
    publishedPages = repositoryFactory.getOrCreate("published-pages", PublishedPage.class);
  }

  @Incoming(Channels.INCOMING_PAGES_STATE)
  void register(CloudEvent pageEvent) {
    String pageKey = CloudEventUtils.getSubject(pageEvent);
    String eventType = pageEvent.getType();
    if (Page.TYPE_PUBLISHED.equals(eventType)) {
      PublishedPage page = new PublishedPage(pageKey, toTimestamp(pageEvent.getTime()));
      publishedPages.put(pageKey, page);
    } else {
      publishedPages.remove(pageKey);
    }
  }

  private static Long toTimestamp(OffsetDateTime eventTime) {
    return Optional.ofNullable(eventTime)
        .map(time -> time.toInstant().toEpochMilli())
        .orElse(null);
  }

  public Stream<PublishedPage> getPages() {
    return publishedPages.values();
  }
}

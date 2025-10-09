package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.configuration.properties.SitemapGeneratorProperties;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
class ProcessPageFunction {

  private static final Set<String> handledEventTypes = Set.of(
      Page.TYPE_PUBLISHED,
      Page.TYPE_UNPUBLISHED
  );

  @Inject
  PublishedPagesStore publishedPagesStore;

  @Inject
  @Channel(Channels.OUTGOING_SITEMAPS_CHANNEL)
  Emitter<CloudEvent> emitter;

  @Inject
  DirtySequenceStateManager dirtySequenceStateManager;

  @Inject
  SitemapGeneratorProperties configuration;

  @Inject
  SitemapService sitemapService;

  @Inject
  PageKeyService pageKeyService;

  @Incoming(Channels.INCOMING_PAGES_CHANNEL)
  void processPage(CloudEvent event) {
    String eventType = event.getType();
    if (!handledEventTypes.contains(eventType)) {
      return;
    }

    String pageKey = CloudEventUtils.getSubject(event);
    publishedPagesStore.register(pageKey, event.getTime(), event.getType());
    if (pageKeyService.isSupportedKey(pageKey)) {
      dirtySequenceStateManager.newDirtyResource();
    }
  }

  @Scheduled(
      every = "${streamx.blueprints.sitemap-generator.dirty-check.interval}",
      delayed = "${streamx.blueprints.sitemap-generator.dirty-check.delay}",
      concurrentExecution = ConcurrentExecution.SKIP
  )
  public void publishSitemapIfNeeded() {
    if (dirtySequenceStateManager.checkIfActionIsNeededForNewSequence()) {
      WebResource sitemapWebResource = sitemapService.createSitemapResource();
      CloudEvent sitemapEvent = CloudEventUtils.eventWithData(
          sitemapWebResource, WebResource.TYPE_PUBLISHED, configuration.outputKey()
      );
      emitter.send(sitemapEvent);
    }
  }
}

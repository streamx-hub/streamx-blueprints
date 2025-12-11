package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.configuration.Configuration;
import io.cloudevents.CloudEvent;
import io.netty.util.internal.StringUtil;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
class ProcessPageFunction {

  @Inject
  PublishedPagesStore publishedPagesStore;

  @Inject
  @Channel(Channels.OUTGOING_SITEMAPS)
  Emitter<CloudEvent> emitter;

  @Inject
  DirtySequenceStateManager dirtySequenceStateManager;

  @Inject
  Configuration configuration;

  @Inject
  SitemapService sitemapService;

  @Inject
  PageKeyService pageKeyService;

  @Incoming(Channels.INCOMING_PAGES)
  void processPage(CloudEvent event) {
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

      String baseUrl = configuration.baseUrl(); // TODO change to collection of base urls
      String hostAndPort = StringUtils.substringAfter(baseUrl, "//")
          .replace(':', '/');

      String key = "/sitemaps/" + hostAndPort + "/sitemap.xml";

      CloudEvent sitemapEvent = CloudEventUtils.eventWithData(
          key, WebResource.TYPE_PUBLISHED, sitemapWebResource
      );
      emitter.send(sitemapEvent);
    }
  }
}

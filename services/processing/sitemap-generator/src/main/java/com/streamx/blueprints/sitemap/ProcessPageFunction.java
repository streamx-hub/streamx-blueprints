package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.configuration.Configuration;
import io.cloudevents.CloudEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
class ProcessPageFunction {

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
          configuration.outputKey(), WebResource.TYPE_PUBLISHED, sitemapWebResource
      );
      emitter.send(sitemapEvent);
    }
  }
}

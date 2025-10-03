package dev.streamx.blueprints.sitemap;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.sitemap.configuration.properties.SitemapGeneratorProperties;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

@ApplicationScoped
class ProcessPageFunction {
  @Inject
  @Channel(Channels.OUTGOING_SITEMAPS_CHANNEL)
  Emitter<WebResource> emitter;

  @Inject
  DirtySequenceStateManager dirtySequenceStateManager;

  @Inject
  SitemapGeneratorProperties configuration;

  @Inject
  SitemapService sitemapService;

  @Inject
  PageKeyService pageKeyService;

  @Incoming(Channels.INCOMING_PAGES_CHANNEL)
  void processPage(Page message, Key key) {
    if (pageKeyService.isSupportedKey(key.getValue())) {
      dirtySequenceStateManager.newDirtyResource();
    }
  }

  @Scheduled(
      every = "${streamx.blueprints.sitemap-generator-processing-service.dirty-check.interval}",
      delayed = "${streamx.blueprints.sitemap-generator-processing-service.dirty-check.delay}",
      concurrentExecution = ConcurrentExecution.SKIP
  )
  public void publishSitemapIfNeeded() {
    if (dirtySequenceStateManager.checkIfActionIsNeededForNewSequence()) {
      WebResource sitemapWebResource = sitemapService.createSitemapResource();
      Message<WebResource> message = Message.of(sitemapWebResource, Metadata.of(
          Key.of(configuration.outputKey()),
          EventTime.of(System.currentTimeMillis()),
          Action.PUBLISH,
          Properties.empty().withType(configuration.outputType().orElse(null))
      ));
      emitter.send(message);
    }
  }
}

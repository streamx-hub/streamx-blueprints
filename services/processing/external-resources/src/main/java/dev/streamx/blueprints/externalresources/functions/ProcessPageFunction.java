package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.HtmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ProcessPageFunction extends BaseProcessResourceFunction<Page> {

  private static final HtmlValuesFinder htmlValuesFinder = new HtmlValuesFinder();
  private static final HtmlContentAdjuster htmlContentAdjuster = new HtmlContentAdjuster();

  @Override
  protected ExternalResourcesCollector externalResourcesCollector() {
    return new ExternalResourcesCollector(
        log, urlComputationService, htmlValuesFinder,
        configuration.htmlExternalResourceXpathSelectors(),
        configuration.htmlExternalResourceUrlExclusionsPattern()
    );
  }

  @Override
  protected BaseResourceContentAdjuster contentAdjuster() {
    return htmlContentAdjuster;
  }

  @Override
  protected Page newResource(String content) {
    return new Page(content);
  }

  @Incoming(Channels.INCOMING_PAGES)
  @Outgoing(Channels.OUTGOING_PAGES)
  public Uni<Message<Page>> processIncomingPage(Message<Page> pageMessage) {
    return processIncomingResource(pageMessage);
  }

}
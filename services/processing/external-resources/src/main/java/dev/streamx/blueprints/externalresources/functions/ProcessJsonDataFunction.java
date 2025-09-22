package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.JsonValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ProcessJsonDataFunction extends BaseProcessResourceFunction<Data> {

  private static final JsonValuesFinder jsonValuesFinder = new JsonValuesFinder();
  private static final JsonContentAdjuster jsonContentAdjuster = new JsonContentAdjuster();

  @Override
  protected ExternalResourcesCollector externalResourcesCollector() {
    return new ExternalResourcesCollector(
        log, urlComputationService, jsonValuesFinder,
        configuration.jsonExternalResourceJsonpathSelectors(),
        configuration.jsonExternalResourceUrlExclusionsPattern()
    );
  }

  @Override
  protected BaseResourceContentAdjuster contentAdjuster() {
    return jsonContentAdjuster;
  }

  @Override
  protected Data newResource(String content) {
    return new Data(content);
  }

  @Incoming(Channels.INCOMING_DATA)
  @Outgoing(Channels.OUTGOING_DATA)
  public Uni<Message<Data>> processIncomingData(Message<Data> dataMessage) {
    return processIncomingResource(dataMessage);
  }
}
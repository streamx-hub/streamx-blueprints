package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.JsonValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessJsonWebResourceFunction extends BaseProcessWebResourceFunction {

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
  protected String handledResourcePathSuffix() {
    return ".json";
  }
}
package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.contentadjusters.XmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.XmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessXmlWebResourceFunction extends BaseProcessWebResourceFunction {

  private static final XmlValuesFinder xmlValuesFinder = new XmlValuesFinder();
  private static final XmlContentAdjuster xmlContentAdjuster = new XmlContentAdjuster();

  @Override
  protected ExternalResourcesCollector externalResourcesCollector() {
    return new ExternalResourcesCollector(
        log, urlComputationService, xmlValuesFinder,
        configuration.xmlExternalResourceXpathSelectors(),
        configuration.xmlExternalResourceUrlExclusionsPattern()
    );
  }

  @Override
  protected BaseResourceContentAdjuster contentAdjuster() {
    return xmlContentAdjuster;
  }

  @Override
  protected String handledResourcePathSuffix() {
    return ".xml";
  }
}
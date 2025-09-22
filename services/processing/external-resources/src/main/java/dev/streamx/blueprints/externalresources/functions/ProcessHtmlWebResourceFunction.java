package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.HtmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessHtmlWebResourceFunction extends BaseProcessWebResourceFunction {

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
  protected String handledResourcePathSuffix() {
    return ".html";
  }
}
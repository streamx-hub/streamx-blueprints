package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.HtmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import org.jboss.logging.Logger;

public class ProcessHtmlWebResourceFunctionSettings extends ProcessResourceFunctionSettings<WebResource> {

  public ProcessHtmlWebResourceFunctionSettings(Logger log,
      UrlComputationService urlComputationService, Configuration configuration) {
    super(
        new HtmlValuesFinder(),
        new HtmlContentAdjuster(),
        WebResource.class,
        WebResource::new,
        Set.of(WebResource.TYPE_PUBLISHED, WebResource.TYPE_UNPUBLISHED),
        ".html",
        log,
        urlComputationService,
        configuration.htmlExternalResourceXpathSelectors(),
        configuration.htmlExternalResourceUrlExclusionsPattern()
    );
  }
}
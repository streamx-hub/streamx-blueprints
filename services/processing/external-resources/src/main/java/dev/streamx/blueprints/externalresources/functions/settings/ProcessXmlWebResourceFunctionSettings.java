package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.XmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.XmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import org.jboss.logging.Logger;

public class ProcessXmlWebResourceFunctionSettings extends ProcessResourceFunctionSettings<WebResource> {

  public ProcessXmlWebResourceFunctionSettings(Logger log,
      UrlComputationService urlComputationService, Configuration configuration) {
    super(
        new XmlValuesFinder(),
        new XmlContentAdjuster(),
        WebResource.class,
        WebResource::new,
        Set.of(WebResource.TYPE_PUBLISHED, WebResource.TYPE_UNPUBLISHED),
        ".xml",
        log,
        urlComputationService,
        configuration.xmlExternalResourceXpathSelectors(),
        configuration.xmlExternalResourceUrlExclusionsPattern()
    );
  }
}
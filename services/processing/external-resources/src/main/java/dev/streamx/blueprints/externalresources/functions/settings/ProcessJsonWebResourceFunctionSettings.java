package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.JsonValuesFinder;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import org.jboss.logging.Logger;

public class ProcessJsonWebResourceFunctionSettings extends ProcessResourceFunctionSettings<WebResource> {

  public ProcessJsonWebResourceFunctionSettings(Logger log,
      UrlComputationService urlComputationService, Configuration configuration) {
    super(
        new JsonValuesFinder(),
        new JsonContentAdjuster(),
        WebResource.class,
        WebResource::new,
        Set.of(WebResource.TYPE_PUBLISHED, WebResource.TYPE_UNPUBLISHED),
        ".json",
        log,
        urlComputationService,
        configuration.jsonExternalResourceJsonpathSelectors(),
        configuration.jsonExternalResourceUrlExclusionsPattern()
    );
  }
}
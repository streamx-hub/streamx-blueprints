package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.JsonValuesFinder;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import org.jboss.logging.Logger;

public class ProcessJsonDataFunctionSettings extends ProcessResourceFunctionSettings<Data> {

  public ProcessJsonDataFunctionSettings(Logger log, UrlComputationService urlComputationService,
      Configuration configuration) {
    super(
        new JsonValuesFinder(),
        new JsonContentAdjuster(),
        Data.class,
        Data::new,
        Set.of(Data.TYPE_PUBLISHED, Data.TYPE_UNPUBLISHED),
        "",
        log,
        urlComputationService,
        configuration.jsonExternalResourceJsonpathSelectors(),
        configuration.jsonExternalResourceUrlExclusionsPattern()
    );
  }
}
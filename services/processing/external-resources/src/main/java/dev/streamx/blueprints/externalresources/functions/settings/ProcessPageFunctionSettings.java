package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.HtmlValuesFinder;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import org.jboss.logging.Logger;

public class ProcessPageFunctionSettings extends ProcessResourceFunctionSettings<Page> {

  public ProcessPageFunctionSettings(Logger log,
      UrlComputationService urlComputationService, Configuration configuration) {
    super(
        new HtmlValuesFinder(),
        new HtmlContentAdjuster(),
        Page.class,
        Page::new,
        Set.of(Page.TYPE_PUBLISHED, Page.TYPE_UNPUBLISHED),
        "",
        log,
        urlComputationService,
        configuration.htmlExternalResourceXpathSelectors(),
        configuration.htmlExternalResourceUrlExclusionsPattern()
    );
  }
}
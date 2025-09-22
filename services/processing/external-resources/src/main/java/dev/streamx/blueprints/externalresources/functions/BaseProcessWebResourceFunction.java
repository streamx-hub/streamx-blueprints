package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.WebResource;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
abstract class BaseProcessWebResourceFunction extends BaseProcessResourceFunction<WebResource> {

  protected abstract String handledResourcePathSuffix();

  @Override
  protected WebResource newResource(String content) {
    return new WebResource(content);
  }
}
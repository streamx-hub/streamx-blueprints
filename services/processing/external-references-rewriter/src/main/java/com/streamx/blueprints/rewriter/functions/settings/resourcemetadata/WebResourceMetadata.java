package com.streamx.blueprints.rewriter.functions.settings.resourcemetadata;

import com.streamx.blueprints.data.WebResource;

public class WebResourceMetadata extends BaseResourceMetadata<WebResource> {

  public WebResourceMetadata() {
    super(
        WebResource.class, WebResource::new,
        WebResource.TYPE_PUBLISHED, WebResource.TYPE_UNPUBLISHED
    );
  }
}

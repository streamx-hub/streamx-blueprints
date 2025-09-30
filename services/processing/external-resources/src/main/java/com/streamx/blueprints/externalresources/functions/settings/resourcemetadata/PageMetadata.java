package com.streamx.blueprints.externalresources.functions.settings.resourcemetadata;

import com.streamx.blueprints.data.Page;

public class PageMetadata extends BaseResourceMetadata<Page> {

  public PageMetadata() {
    super(
        Page.class, Page::new,
        Page.TYPE_PUBLISHED, Page.TYPE_UNPUBLISHED
    );
  }
}

package dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata;

import dev.streamx.blueprints.data.Data;

public class DataMetadata extends BaseResourceMetadata<Data> {

  public DataMetadata() {
    super(
        Data.class, Data::new,
        Data.TYPE_PUBLISHED, Data.TYPE_UNPUBLISHED
    );
  }
}

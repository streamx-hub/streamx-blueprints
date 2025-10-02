package com.streamx.blueprints.dependenciesrewriter.functions.settings.resourcemetadata;

import com.streamx.blueprints.data.Data;

public class DataMetadata extends BaseResourceMetadata<Data> {

  public DataMetadata() {
    super(
        Data.class, Data::new,
        Data.TYPE_PUBLISHED, Data.TYPE_UNPUBLISHED
    );
  }
}

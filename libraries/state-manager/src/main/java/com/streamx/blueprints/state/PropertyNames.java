package com.streamx.blueprints.state;

public final class PropertyNames {

  private PropertyNames() {
    // no instance
  }

  public static final String STATE_BACKEND = "state.backend";
  public static final String STATE_ROCKSDB_PATH = "state.rocksdb.path";

  // property from streamx-service-mesh
  public static final String SERVICE_INSTANCE_ID = "streamx.service.instance-id";
}

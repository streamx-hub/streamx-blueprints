package com.streamx.blueprints.state;

public final class PropertyNames {

  private PropertyNames() {
    // no instance
  }

  private static final String PREFIX = "streamx.blueprints.key-value-state-repository";
  public static final String BACKEND = PREFIX + ".backend";
  public static final String ROCKSDB_PATH = PREFIX + ".rocksdb.path";

  // property from streamx-service-mesh
  public static final String SERVICE_INSTANCE_ID = "streamx.service.instance-id";
}

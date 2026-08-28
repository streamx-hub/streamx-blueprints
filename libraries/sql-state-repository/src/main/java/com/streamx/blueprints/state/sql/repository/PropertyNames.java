package com.streamx.blueprints.state.sql.repository;

public final class PropertyNames {

  private PropertyNames() {
    // no instance
  }

  private static final String PREFIX = "streamx.blueprints.sql-state-repository";
  public static final String BACKEND = PREFIX + ".backend";
  public static final String SQLITE_PATH = PREFIX + ".sqlite.path";
  public static final String SQLITE_MAX_POOL_SIZE = PREFIX + ".sqlite.max-pool-size";
  public static final String SQLITE_MAX_BUSY_TIMEOUT = PREFIX + ".sqlite.max-busy-timeout";

  // property from streamx-service-mesh
  public static final String SERVICE_INSTANCE_ID = "streamx.service.instance-id";
}

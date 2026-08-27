package com.streamx.blueprints.state.sql.repository;

public final class PropertyNames {

  private PropertyNames() {
    // no instance
  }

  private static final String PREFIX = "streamx.blueprints.sql-state-repository";
  public static final String BACKEND = PREFIX + ".backend";
  public static final String SQLITE_PATH = PREFIX + ".sqlite.path";

  // property from streamx-service-mesh
  public static final String SERVICE_INSTANCE_ID = "streamx.service.instance-id";
}

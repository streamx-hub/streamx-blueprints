package com.streamx.blueprints.data.collector;

public final class Channels {

  public static class Incoming {

    public static final String DATA = "data";

    private Incoming() {
      // no instances
    }
  }

  public static class Outgoing {

    public static final String COLLECTED_DATA = "collected-data";
    public static final String WEB_RESOURCES = "web-resources";

    private Outgoing() {
      // no instances
    }
  }

  private Channels() {
    // no instances
  }
}

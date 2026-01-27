package com.streamx.blueprints.rendering.engine;

public class Channels {

  private Channels() {
    // no instances
  }

  public static class Incoming {

    private Incoming() {
      // no instances
    }

    public static final String DATA = "data";
    public static final String DATA_STATE = "data-state";

    public static final String RENDERERS = "renderers";
    public static final String RENDERERS_STATE = "renderers-state";

    public static final String RENDERING_CONTEXTS = "rendering-contexts";
    public static final String RENDERING_CONTEXTS_STATE = "rendering-contexts-state";

    public static final String RENDERING_REQUESTS = "incoming-rendering-requests";
  }

  public static class Outgoing {

    private Outgoing() {
      // no instances
    }

    public static final String RENDERING_REQUESTS = "outgoing-rendering-requests";
    public static final String PAGES = "pages";
    public static final String FRAGMENTS = "fragments";
  }
}

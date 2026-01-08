package com.streamx.blueprints.composition;

public final class Channels {

  public static final String INCOMING_LAYOUTS = "layouts";
  public static final String INCOMING_COMPOSITIONS = "compositions";
  public static final String OUTGOING_PAGES = "pages";

  public static final String INCOMING_LAYOUTS_STATE = "layouts-state";
  public static final String INCOMING_COMPOSITIONS_STATE = "compositions-state";

  public static final String INCOMING_PAGE_COMPOSE_REQUESTS = "incoming-page-compose-requests";
  public static final String OUTGOING_PAGE_COMPOSE_REQUESTS = "outgoing-page-compose-requests";

  private Channels() {
    // no instances
  }
}

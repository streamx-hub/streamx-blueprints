package com.streamx.blueprints.composition;

record PageComposeRequest(String compositionKey, String layoutKey) {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.page-compose-request.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.page-compose-request.unpublished.v1";
}

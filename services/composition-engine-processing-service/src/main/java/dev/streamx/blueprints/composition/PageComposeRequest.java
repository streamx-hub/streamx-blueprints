package dev.streamx.blueprints.composition;

record PageComposeRequest(String compositionKey, String layoutKey) {

  public static final String TYPE_PUBLISHED = "dev.streamx.blueprints.page-compose-request.published.v1";
  public static final String TYPE_UNPUBLISHED = "dev.streamx.blueprints.page-compose-request.unpublished.v1";
}

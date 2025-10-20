package com.streamx.blueprints.opensearch;

record ExampleIndexableResourceContent(
    String title,
    String content
) implements TestResource {

}

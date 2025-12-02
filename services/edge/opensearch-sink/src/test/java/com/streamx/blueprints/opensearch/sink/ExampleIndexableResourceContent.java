package com.streamx.blueprints.opensearch.sink;

record ExampleIndexableResourceContent(
    String title,
    String content
) implements TestResource {

}

package com.streamx.blueprints.dependenciesrewriter.data;

public record ParentResource(
    String absoluteUrl,
    String streamxKey,
    String content,
    String payloadType) {

}

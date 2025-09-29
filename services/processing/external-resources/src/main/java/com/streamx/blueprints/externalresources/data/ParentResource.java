package com.streamx.blueprints.externalresources.data;

public record ParentResource(
    String absoluteUrl,
    String streamxKey,
    String content,
    String payloadType) {

}

package com.streamx.blueprints.rewriter.data;

public record ParentResource(
    String absoluteUrl,
    String streamxKey,
    String content,
    String payloadType) {

}

package com.streamx.blueprints.rewriter.data;

public record ResourceData(
    String absoluteUrl,
    String streamxKey,
    String content,
    String payloadType) {

}

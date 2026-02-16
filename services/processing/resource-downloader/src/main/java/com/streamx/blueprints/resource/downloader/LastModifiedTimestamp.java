package com.streamx.blueprints.resource.downloader;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record LastModifiedTimestamp(String lastModifiedGmt, int httpHeadStatus) {

}
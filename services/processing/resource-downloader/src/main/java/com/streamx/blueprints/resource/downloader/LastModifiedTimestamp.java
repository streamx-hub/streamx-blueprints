package com.streamx.blueprints.resource.downloader;

public record LastModifiedTimestamp(String lastModifiedGmt, int httpHeadStatus) {

}
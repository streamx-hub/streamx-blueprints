package com.streamx.blueprints.rewriter.configuration;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "streamx.blueprints.local-references-rewriter")
public interface Configuration {

  String processedPagePathPattern();

}

package com.streamx.blueprints.externalresources.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigMapping(prefix = "streamx.blueprints.external-resources-processing-service")
public interface Configuration {

  String baseUrlForRelativePaths();

  Set<String> processablePayloadTypes();

  Optional<List<String>> htmlExternalResourceXpathSelectors();

  Optional<Pattern> htmlExternalResourceUrlExclusionsPattern();

  Optional<List<String>> xmlExternalResourceXpathSelectors();

  Optional<Pattern> xmlExternalResourceUrlExclusionsPattern();

  Optional<List<String>> jsonExternalResourceJsonpathSelectors();

  Optional<Pattern> jsonExternalResourceUrlExclusionsPattern();

  String emittedPageType();

  String emittedWebResourceType();

  String emittedAssetType();

  @WithDefault("5000")
  int externalResourceDownloadTimeoutMilliseconds();
}

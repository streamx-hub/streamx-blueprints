package com.streamx.blueprints.rewriter.configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class Configuration {

  private static final Config CONFIG = ConfigProvider.getConfig();
  private static final String PREFIX = "streamx.blueprints.external-references-rewriter.";
  public static final String BASE_URL = PREFIX + "base-url-for-relative-paths";
  public static final String PROCESSABLE_TYPES = PREFIX + "processable-payload-types";

  public static final String HTML_XPATH = PREFIX + "html-external-resource-xpath-selectors";
  public static final String HTML_EXCLUDE =
      PREFIX + "html-external-resource-url-exclusions-pattern";

  public static final String XML_XPATH = PREFIX + "xml-external-resource-xpath-selectors";
  public static final String XML_EXCLUDE = PREFIX + "xml-external-resource-url-exclusions-pattern";

  public static final String JSON_PATH = PREFIX + "json-external-resource-jsonpath-selectors";
  public static final String JSON_EXCLUDE =
      PREFIX + "json-external-resource-url-exclusions-pattern";

  public static final String YAML_PATH = PREFIX + "yaml-external-resource-jsonpath-selectors";
  public static final String YAML_EXCLUDE =
      PREFIX + "yaml-external-resource-url-exclusions-pattern";

  public static final String EMITTED_PAGE_TYPE = PREFIX + "emitted-page-type";
  public static final String EMITTED_WEB_RESOURCE_TYPE = PREFIX + "emitted-web-resource-type";
  public static final String EMITTED_ASSET_TYPE = PREFIX + "emitted-asset-type";

  public static final String TIMEOUT = PREFIX + "external-resource-download-timeout-milliseconds";

  public static String baseUrlForRelativePaths() {
    return CONFIG.getValue(BASE_URL, String.class);
  }

  public static Set<String> processablePayloadTypes() {
    return getList(PROCESSABLE_TYPES).map(HashSet::new).orElse(new HashSet<>());
  }

  public static Optional<List<String>> htmlExternalResourceXpathSelectors() {
    return getList(HTML_XPATH);
  }

  public static Optional<Pattern> htmlExternalResourceUrlExclusionsPattern() {
    return getPattern(HTML_EXCLUDE);
  }

  public static Optional<List<String>> xmlExternalResourceXpathSelectors() {
    return getList(XML_XPATH);
  }

  public static Optional<Pattern> xmlExternalResourceUrlExclusionsPattern() {
    return getPattern(XML_EXCLUDE);
  }

  public static Optional<List<String>> jsonExternalResourceJsonpathSelectors() {
    return getList(JSON_PATH);
  }

  public static Optional<Pattern> jsonExternalResourceUrlExclusionsPattern() {
    return getPattern(JSON_EXCLUDE);
  }

  public static Optional<List<String>> yamlExternalResourceJsonpathSelectors() {
    return getList(YAML_PATH);
  }

  public static Optional<Pattern> yamlExternalResourceUrlExclusionsPattern() {
    return getPattern(YAML_EXCLUDE);
  }

  public static String emittedPageType() {
    return CONFIG.getValue(EMITTED_PAGE_TYPE, String.class);
  }

  public static String emittedWebResourceType() {
    return CONFIG.getValue(EMITTED_WEB_RESOURCE_TYPE, String.class);
  }

  public static String emittedAssetType() {
    return CONFIG.getValue(EMITTED_ASSET_TYPE, String.class);
  }

  public static int externalResourceDownloadTimeoutMilliseconds() {
    return CONFIG.getOptionalValue(TIMEOUT, Integer.class).orElse(5000);
  }

  private static Optional<List<String>> getList(String key) {
    return CONFIG.getOptionalValue(key, String.class)
        .map(val -> Arrays.stream(val.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
  }

  private static Optional<Pattern> getPattern(String key) {
    return CONFIG.getOptionalValue(key, String.class)
        .map(Pattern::compile);
  }
}
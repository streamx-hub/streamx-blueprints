package com.streamx.blueprints.index.collectors.html;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class AbstractHtmlCollector {

  protected  <C, V> Map<String, V> collect(
      boolean enabled,
      Collection<C> configs,
      BiFunction<Document, C, Map<String, V>> collector,
      String pageContent
  ) {
    if (!enabled) {
      return Collections.emptyMap();
    }
    return configs.stream()
        .flatMap(config -> collector.apply(Jsoup.parse(pageContent), config).entrySet().stream())
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue
        ));
  }

  protected static String normalizeKey(String key) {
    return key.trim()
        .toLowerCase()
        .replace("-", "_");
  }

  protected static String getKey(String key, String delimiter) {
    if (StringUtils.isBlank(delimiter)) {
      return key;
    }
    String[] parts = key.split(Pattern.quote(delimiter), 2);
    return parts.length > 1 ? parts[1] : key;
  }

  protected static String findFirst(Element element, List<String> attributesNames) {
    return attributesNames.stream()
        .filter(element::hasAttr)
        .map(element::attr)
        .findFirst()
        .orElse(null);
  }
}

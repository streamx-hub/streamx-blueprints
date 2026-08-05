package com.streamx.blueprints.index.collectors.html.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class AbstractHtmlCollector {

  protected <FieldT, V> Map<String, V> collect(
      Collection<FieldT> configs,
      BiFunction<Document, FieldT, Map<String, V>> collector,
      String pageContent
  ) {
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

  protected static String findFirst(Element element, List<String> attributesNames) {
    return attributesNames.stream()
        .filter(element::hasAttr)
        .map(element::attr)
        .findFirst()
        .orElse(null);
  }
}

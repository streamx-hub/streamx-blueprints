package com.streamx.blueprints.index.collectors.html.fields.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.collectors.html.AbstractHtmlCollector;
import com.streamx.blueprints.index.collectors.html.fields.FieldsCollector;
import com.streamx.blueprints.index.configuration.Configuration;
import com.streamx.blueprints.index.configuration.FieldsHtmlElementCollectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@ApplicationScoped
public class HtmlFieldsCollector extends AbstractHtmlCollector implements FieldsCollector {

  @Inject
  Configuration configuration;

  @Override
  public Map<String, String> getFields(Page page) {
    return collect(configuration.includeFields(),
        configuration.fieldsConfiguration().values(),
        this::collectFields,
        page.getContentAsString());
  }

  private Map<String, String> collectFields(Document document,
      FieldsHtmlElementCollectorConfig config) {
    return config.selector().map(selector ->
        document.select(selector)
            .stream()
            .flatMap(element -> {
              if (config.singleAttr()) {
                return config.keys()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(
                        Collectors.toMap(key -> normalizeKey(getKey(key, null)),
                            element::attr))
                    .entrySet()
                    .stream();
              } else {
                return getFieldFromAttributes(config, element).entrySet().stream();
              }
            }).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ))
    ).orElse(Collections.emptyMap());
  }

  private Map<String, String> getFieldFromAttributes(FieldsHtmlElementCollectorConfig config,
      Element element) {
    String key = findFirst(element, config.keys().orElse(Collections.emptyList()));
    String value = findFirst(element, config.values().orElse(Collections.emptyList()));
    if (StringUtils.isBlank(key) || StringUtils.isBlank(value) || !isKeyValueAllowed(config, key)) {
      return Collections.emptyMap();
    }
    return Map.of(getKey(key, null), value);
  }

  private boolean isKeyValueAllowed(FieldsHtmlElementCollectorConfig config, String key) {
    return config.allowedKeyValues().map(list -> list.contains(key)).orElse(false);
  }
}

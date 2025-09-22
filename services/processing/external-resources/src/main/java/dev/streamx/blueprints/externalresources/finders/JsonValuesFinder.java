package dev.streamx.blueprints.externalresources.finders;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class JsonValuesFinder extends BaseValuesFinder {

  private static final Configuration config = Configuration.defaultConfiguration()
      .addOptions(Option.ALWAYS_RETURN_LIST)
      .addOptions(Option.SUPPRESS_EXCEPTIONS);

  @Override
  public Set<String> doFindMatchingValues(String inputContent, List<String> jsonPaths) {
    DocumentContext document = JsonPath.using(config).parse(inputContent);
    Set<String> foundValues = new LinkedHashSet<>();

    for (String jsonPath : jsonPaths) {
      @SuppressWarnings("rawtypes")
      List found = document.read(jsonPath, List.class);
      for (Object item : found) {
        Optional.ofNullable(item)
            .map(Object::toString)
            .filter(StringUtils::isNotBlank)
            .ifPresent(foundValues::add);
      }
    }

    return foundValues;
  }
}

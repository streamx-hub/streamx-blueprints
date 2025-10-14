package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.DataFilter;
import java.util.regex.Pattern;

class PatternsDataFilter implements DataFilter {

  private final Pattern dataKeyMatchPattern;
  private final Pattern dataTypeMatchPattern;

  PatternsDataFilter(Pattern dataKeyMatchPattern, Pattern dataTypeMatchPattern) {
    this.dataKeyMatchPattern = dataKeyMatchPattern;
    this.dataTypeMatchPattern = dataTypeMatchPattern;
  }

  @Override
  public boolean test(String dataKey, String dataType) {
    return isMatching(dataKeyMatchPattern, dataKey) && isMatching(dataTypeMatchPattern, dataType);
  }

  private static boolean isMatching(Pattern pattern, String value) {
    // accept all values if not patter set in the context
    return pattern == null
        // otherwise, value must be present and matching the pattern
        || (value != null && pattern.matcher(value).matches());
  }
}
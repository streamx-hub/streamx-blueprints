package com.streamx.blueprints.data.collector.collectors;

/**
 * Filter applied to data according to the common configuration for the collection.
 */
public interface DataFilter {

  boolean test(String dataKey, String dataType);
}

package com.streamx.blueprints.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
public record IndexableResourceContent(String title, String content, Map<String, Object> facets,
                                Map<String, Object> fields) {

}

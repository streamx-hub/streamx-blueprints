package com.streamx.blueprints.index;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
record IndexableResourceContent(String title, String content, Map<String, Object> facets,
                                Map<String, Object> fields) {

}

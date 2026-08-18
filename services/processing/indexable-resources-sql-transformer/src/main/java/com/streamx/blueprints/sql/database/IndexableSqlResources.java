package com.streamx.blueprints.sql.database;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
public record IndexableSqlResources(String subject,
                             String title,
                             String content,
                             Map<String, Object> facets,
                             Map<String, Object> fields) {

}

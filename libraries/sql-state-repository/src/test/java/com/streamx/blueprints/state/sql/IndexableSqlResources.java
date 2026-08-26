package com.streamx.blueprints.state.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record IndexableSqlResources(
    String subject,
    String title,
    String url,
    String description,
    String publicationDate,
    String modificationDate,
    String tags,
    String author,
    String image,
    String language,
    String contentType,
    String metadata
) {}

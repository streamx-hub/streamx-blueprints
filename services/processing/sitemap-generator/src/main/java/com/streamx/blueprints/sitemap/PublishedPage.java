package com.streamx.blueprints.sitemap;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;

@RegisterForReflection
public record PublishedPage(String pageName, @Nullable Long timestamp) {

}

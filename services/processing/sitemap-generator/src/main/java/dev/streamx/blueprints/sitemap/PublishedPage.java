package dev.streamx.blueprints.sitemap;

import jakarta.annotation.Nullable;
import java.time.OffsetDateTime;

public record PublishedPage(String pageName, @Nullable OffsetDateTime time) {

}

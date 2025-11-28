package com.senacor.elasticsearch.evolution.core.model.migration;

import static com.senacor.elasticsearch.evolution.core.internal.utils.AssertionUtils.requireNotEmpty;
import static java.util.Objects.requireNonNull;

import com.senacor.elasticsearch.evolution.core.model.FileNameInfo;
import com.senacor.elasticsearch.evolution.core.model.MigrationVersion;

public record FileNameInfoImpl(MigrationVersion version, String description,
                               String scriptName) implements FileNameInfo {

  public FileNameInfoImpl(MigrationVersion version, String description, String scriptName) {
    this.version = requireNonNull(version, "version must not be null");
    this.description = requireNotEmpty(description, "description must not be empty");
    this.scriptName = requireNotEmpty(scriptName, "scriptName must not be empty");
  }

}

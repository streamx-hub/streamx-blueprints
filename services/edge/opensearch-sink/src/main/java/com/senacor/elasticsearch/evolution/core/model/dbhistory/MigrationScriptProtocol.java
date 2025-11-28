package com.senacor.elasticsearch.evolution.core.model.dbhistory;

import com.senacor.elasticsearch.evolution.core.model.FileNameInfo;
import com.senacor.elasticsearch.evolution.core.model.MigrationVersion;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Represents a Script execution in the database.
 */
public class MigrationScriptProtocol implements FileNameInfo, Comparable<MigrationScriptProtocol> {

  private MigrationVersion version;
  private String indexName;
  private String description;
  private String scriptName;
  private int checksum;
  private OffsetDateTime executionTimestamp;
  private int executionRuntimeInMillis;
  private boolean success;
  private boolean locked = true;

  @Override
  public MigrationVersion version() {
    return version;
  }

  public MigrationScriptProtocol setVersion(String version) {
    this.version = MigrationVersion.fromVersion(version);
    return this;
  }

  public MigrationScriptProtocol setVersion(MigrationVersion version) {
    this.version = version;
    return this;
  }

  public String getIndexName() {
    return indexName;
  }

  public MigrationScriptProtocol setIndexName(String indexName) {
    this.indexName = indexName;
    return this;
  }

  @Override
  public String description() {
    return description;
  }

  public MigrationScriptProtocol setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public String scriptName() {
    return scriptName;
  }

  public MigrationScriptProtocol setScriptName(String scriptName) {
    this.scriptName = scriptName;
    return this;
  }

  public int getChecksum() {
    return checksum;
  }

  public MigrationScriptProtocol setChecksum(int checksum) {
    this.checksum = checksum;
    return this;
  }

  public OffsetDateTime getExecutionTimestamp() {
    return executionTimestamp;
  }

  public MigrationScriptProtocol setExecutionTimestamp(OffsetDateTime executionTimestamp) {
    this.executionTimestamp = executionTimestamp;
    return this;
  }

  public int getExecutionRuntimeInMillis() {
    return executionRuntimeInMillis;
  }

  public MigrationScriptProtocol setExecutionRuntimeInMillis(int executionRuntimeInMillis) {
    this.executionRuntimeInMillis = executionRuntimeInMillis;
    return this;
  }

  public boolean isSuccess() {
    return success;
  }

  public MigrationScriptProtocol setSuccess(boolean success) {
    this.success = success;
    return this;
  }

  public boolean isLocked() {
    return locked;
  }

  public MigrationScriptProtocol setLocked(boolean locked) {
    this.locked = locked;
    return this;
  }

  @Override
  public int compareTo(MigrationScriptProtocol o) {
    if (o == null) {
      return 1;
    }
    return version.compareTo(o.version);
  }

}

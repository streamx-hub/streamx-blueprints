package com.senacor.elasticsearch.evolution.core.model;

public interface FileNameInfo {

  MigrationVersion version();

  String description();

  String scriptName();
}

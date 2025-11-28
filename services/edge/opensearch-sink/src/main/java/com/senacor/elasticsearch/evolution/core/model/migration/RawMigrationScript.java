package com.senacor.elasticsearch.evolution.core.model.migration;

/**
 * @param fileName script file name without any packages/directories
 */
public record RawMigrationScript(String fileName, String content) {

}

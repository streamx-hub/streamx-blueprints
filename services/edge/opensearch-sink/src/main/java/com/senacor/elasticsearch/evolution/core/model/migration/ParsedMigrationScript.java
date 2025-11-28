package com.senacor.elasticsearch.evolution.core.model.migration;

import com.senacor.elasticsearch.evolution.core.model.FileNameInfo;

public record ParsedMigrationScript(FileNameInfo fileNameInfo, int checksum,
                                    MigrationScriptRequest migrationScriptRequest) {

}

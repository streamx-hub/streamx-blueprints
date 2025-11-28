package com.senacor.elasticsearch.evolution.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senacor.elasticsearch.evolution.core.migration.execution.HistoryRepository;
import com.senacor.elasticsearch.evolution.core.migration.execution.MigrationService;
import com.senacor.elasticsearch.evolution.core.migration.input.MigrationScriptParser;
import com.senacor.elasticsearch.evolution.core.migration.input.MigrationScriptReader;
import com.senacor.elasticsearch.evolution.core.model.dbhistory.MigrationScriptProtocol;
import com.senacor.elasticsearch.evolution.core.model.migration.ParsedMigrationScript;
import com.senacor.elasticsearch.evolution.core.model.migration.RawMigrationScript;
import java.util.Collection;
import java.util.List;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

public class ElasticsearchEvolution {

  private static final Logger logger = Logger.getLogger(ElasticsearchEvolution.class);
  private static final ObjectMapper objectMapper = new ObjectMapper()
      // not all search response properties are mapped, so they must be ignored
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final MigrationScriptReader migrationScriptReader;
  private final MigrationScriptParser migrationScriptParser;
  private final MigrationService migrationService;

  /**
   * @param migrationScriptLocations Locations of migrations scripts. Supported is
   *                                 classpath:some/path and file:/some/path The location is scanned
   *                                 recursive. NOTE: all scripts in all locations / subdirectories
   *                                 will be flattened and only the version number will be used to
   *                                 order them
   */
  public ElasticsearchEvolution(List<String> migrationScriptLocations, RestClient restClient) {
    migrationScriptReader = new MigrationScriptReader(migrationScriptLocations);
    migrationScriptParser = new MigrationScriptParser();

    HistoryRepository historyRepository = new HistoryRepository(restClient, objectMapper);
    migrationService = new MigrationService(historyRepository, restClient);

    logger.infof("Created ElasticsearchEvolution with locations='%s' and client='%s'",
        migrationScriptLocations, restClient.getNodes());
  }

  /**
   * <p>Starts the migration. All pending migrations will be applied in order.
   * Calling migrate on an up-to-date database has no effect.</p>
   *
   * @return The number of successfully applied migrations.
   * @throws MigrationException when the migration failed.
   */
  public int migrate() throws MigrationException {
    logger.info("start elasticsearch migration...");
    logger.info("reading migration scripts...");
    Collection<RawMigrationScript> rawMigrationScripts = migrationScriptReader.read();
    if (rawMigrationScripts.size() > 1_000) {
      throw new MigrationException(
          ("configured historyMaxQuerySize of 1000 is to low for the number of "
           + "migration scripts of '%s'").formatted(rawMigrationScripts.size()));
    }

    logger.info("parsing migration scripts...");
    Collection<ParsedMigrationScript> parsedMigrationScripts = migrationScriptParser.parse(
        rawMigrationScripts);
    logger.info("execute migration scripts...");
    List<MigrationScriptProtocol> executedScripts = migrationService.executePendingScripts(
        parsedMigrationScripts);
    return (int) executedScripts.stream()
        .filter(MigrationScriptProtocol::isSuccess)
        .count();
  }

}

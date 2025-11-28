package com.senacor.elasticsearch.evolution.core.migration.execution;

import static java.util.Objects.requireNonNull;

import com.senacor.elasticsearch.evolution.core.MigrationException;
import com.senacor.elasticsearch.evolution.core.model.MigrationVersion;
import com.senacor.elasticsearch.evolution.core.model.dbhistory.MigrationScriptProtocol;
import com.senacor.elasticsearch.evolution.core.model.migration.ParsedMigrationScript;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

public class MigrationService {

  private static final Logger logger = Logger.getLogger(MigrationService.class);

  private final HistoryRepository historyRepository;
  private final RestClient restClient;
  private final ContentType defaultContentType = ContentType.parse(
      "application/json; charset=UTF-8");
  private final String baselineVersion = "1.0";

  public MigrationService(HistoryRepository historyRepository, RestClient restClient) {
    this.historyRepository = requireNonNull(historyRepository,
        "historyRepository must not be null");
    this.restClient = requireNonNull(restClient, "restClient must not be null");
  }

  /**
   * Executes all Migration Scripts after the last succeeded reported History version. The already
   * executed scrips will not be executed again. Therefor after the execution the scripts will be
   * logged in an elasticsearch index where Elasticsearch-Evolution keeps its state.
   *
   * @param migrationScripts all parsed migration scripts which should be executed.
   * @return executed Scripts
   * @throws MigrationException if execution failed
   */
  public List<MigrationScriptProtocol> executePendingScripts(
      Collection<ParsedMigrationScript> migrationScripts)
      throws MigrationException {
    if (!getPendingScriptsToBeExecuted(migrationScripts).isEmpty()) {
      return executePendingScriptsWithLock(migrationScripts);
    } else {
      return new ArrayList<>();
    }
  }

  private List<MigrationScriptProtocol> executePendingScriptsWithLock(
      Collection<ParsedMigrationScript> migrationScripts)
      throws MigrationException {
    List<MigrationScriptProtocol> executedScripts = new ArrayList<>();
    try {
      historyRepository.createIndexIfAbsent();
      waitUntilUnlocked();
      // set a logical index lock
      if (!historyRepository.lock()) {
        throw new MigrationException("could not lock the elasticsearch-evolution history index");
      }

      // get scripts which needs to be executed
      List<ParsedMigrationScript> scriptsToExecute = getPendingScriptsToBeExecuted(
          migrationScripts);

      // now execute scripts and write protocols to history index
      for (ParsedMigrationScript script : scriptsToExecute) {
        // execute scripts
        ExecutionResult res = executeScript(script);
        MigrationScriptProtocol executedScriptProtocol = res.protocol();
        logger.infof("executed migration script %s", executedScriptProtocol.scriptName());
        executedScripts.add(executedScriptProtocol);
        // write protocols to history index
        historyRepository.saveOrUpdate(executedScriptProtocol);
        if (res.error().isPresent()) {
          throw res.error().get();
        }
      }
    } finally {
      // release logical index lock
      if (!historyRepository.unlock()) {
        throw new MigrationException(
            "could not release the elasticsearch-evolution history index lock! "
            + "Maybe you have to release it manually.");
      }
    }
    return executedScripts;
  }

  /**
   * executes the given script and returns a protocol ready to save in the history index
   *
   * @param scriptToExecute the script
   * @return unsaved protocol
   */
  ExecutionResult executeScript(ParsedMigrationScript scriptToExecute) {
    logger.infof("executing script %s", scriptToExecute.fileNameInfo().scriptName());
    boolean success = false;
    long startTimeInMillis = System.currentTimeMillis();
    Optional<RuntimeException> error = Optional.empty();
    try {
      Request request = new Request(
          scriptToExecute.migrationScriptRequest().getHttpMethod().name(),
          scriptToExecute.migrationScriptRequest().getPath());
      if (null != scriptToExecute.migrationScriptRequest().getBody()
          && !scriptToExecute.migrationScriptRequest().getBody().trim().isEmpty()) {
        ContentType contentType = scriptToExecute.migrationScriptRequest().getContentType()
            .orElse(defaultContentType);
        if (null == contentType.getCharset()) {
          logger.debugf("no charset is defined for %s, setting to configured encoding %s",
              scriptToExecute.fileNameInfo(), StandardCharsets.UTF_8);
          contentType = contentType.withCharset(StandardCharsets.UTF_8);
        }
        request.setEntity(
            new NStringEntity(scriptToExecute.migrationScriptRequest().getBody(), contentType));
      }

      RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
      scriptToExecute.migrationScriptRequest().getHttpHeader()
          .forEach(builder::addHeader);
      request.setOptions(builder);

      Response response = restClient.performRequest(request);

      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 200 && statusCode < 300) {
        success = true;
      } else {
        error = Optional.of(new MigrationException(
            "execution of script '%s' failed with HTTP status %s: %s".formatted(
                scriptToExecute.fileNameInfo(),
                statusCode,
                response.toString())));
      }
    } catch (RuntimeException | IOException e) {
      error = Optional.of(new MigrationException(
          "execution of script '%s' failed".formatted(scriptToExecute.fileNameInfo()), e));
    }

    return new ExecutionResult(
        new MigrationScriptProtocol()
            .setExecutionRuntimeInMillis((int) (System.currentTimeMillis() - startTimeInMillis))
            .setSuccess(success)
            .setVersion(scriptToExecute.fileNameInfo().version())
            .setScriptName(scriptToExecute.fileNameInfo().scriptName())
            .setDescription(scriptToExecute.fileNameInfo().description())
            .setChecksum(scriptToExecute.checksum())
            .setExecutionTimestamp(OffsetDateTime.now())
            .setLocked(true),
        error);
  }

  /**
   * This method returns only those scripts, which must be executed. Already executed scripts will
   * be filtered out. the returned scripts must be executed in the returned order.
   *
   * @param migrationScripts all migration scripts that were potentially executed earlier.
   * @return list of ordered scripts which must be executed
   */
  List<ParsedMigrationScript> getPendingScriptsToBeExecuted(
      Collection<ParsedMigrationScript> migrationScripts) {
    if (migrationScripts.isEmpty()) {
      return new ArrayList<>();
    }

    // order migrationScripts by version
    final TreeMap<MigrationVersion, ParsedMigrationScript> scriptsInFilesystemMap = migrationScripts
        .stream()
        .filter(script -> script.fileNameInfo().version().isAtLeast(baselineVersion))
        .collect(Collectors.toMap(
            script -> script.fileNameInfo().version(),
            script -> script,
            (oldValue, newValue) -> newValue,
            TreeMap::new));

    List<MigrationScriptProtocol> history = new ArrayList<>(historyRepository.findAll());
    List<ParsedMigrationScript> res = new ArrayList<>(scriptsInFilesystemMap.values());
    List<ParsedMigrationScript> orderedScripts = new ArrayList<>(scriptsInFilesystemMap.values());
    for (int i = 0; i < history.size(); i++) {
      // do some checks
      MigrationScriptProtocol protocol = history.get(i);
      if (orderedScripts.size() <= i) {
        logger.warnf("""
            there are less migration scripts than already executed history entries! \
            You should never delete migration scripts you have already executed. \
            Or maybe you have to cleanup the Elasticsearch-Evolution history index manually! \
            history version at position %s is %s\
            """, i, protocol.version());
        break;
      }
      ParsedMigrationScript parsedMigrationScript = orderedScripts.get(i);
      if (!protocol.version().getVersion().equals(
          parsedMigrationScript.fileNameInfo().version().getVersion())) {
        throw new MigrationException((
            """
              The logged execution in the Elasticsearch-Evolution history index at position %s is \
              version %s and in the same position in the given migration scripts is version %s! \
              Out of order execution is not supported. Or maybe you have added new migration \
              scripts in between or have to cleanup the Elasticsearch-Evolution history index \
              manually\
              """).formatted(
          i, protocol.version(), parsedMigrationScript.fileNameInfo().version()));
      }
      validateOnMigrateIfEnabled(protocol, parsedMigrationScript);

      if (protocol.isSuccess()) {
        res.remove(parsedMigrationScript);
      }
    }

    return res;
  }

  private void validateOnMigrateIfEnabled(MigrationScriptProtocol protocol,
      ParsedMigrationScript parsedMigrationScript) {
    // failed scripts can be edited and retried, but successfully executed scripts may not
    // be modified afterward
    if (protocol.isSuccess() && protocol.getChecksum() != parsedMigrationScript.checksum()) {
      throw new MigrationException((
          """
              The logged execution for the migration script version %s (%s) \
              has a different checksum from the given migration script! \
              Modifying already-executed scripts is not supported.\
              """).formatted(
          protocol.version(), protocol.scriptName()));
    }
  }

  /**
   * wait until the elasticsearch-evolution history index is unlocked
   */
  void waitUntilUnlocked() {
    while (historyRepository.isLocked()) {
      try {
        logger.info("Elasticsearch-Evolution history index is locked, waiting 1 s until retry...");
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        logger.warn("waitUntilUnlocked was interrupted!", e);
      }
    }
  }

  record ExecutionResult(MigrationScriptProtocol protocol, Optional<RuntimeException> error) {

  }
}

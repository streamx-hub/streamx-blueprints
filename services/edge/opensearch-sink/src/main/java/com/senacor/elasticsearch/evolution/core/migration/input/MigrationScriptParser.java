package com.senacor.elasticsearch.evolution.core.migration.input;

import static com.senacor.elasticsearch.evolution.core.internal.utils.AssertionUtils.requireCondition;
import static com.senacor.elasticsearch.evolution.core.internal.utils.AssertionUtils.requireNotBlank;
import static java.util.Objects.requireNonNull;

import com.senacor.elasticsearch.evolution.core.MigrationException;
import com.senacor.elasticsearch.evolution.core.model.FileNameInfo;
import com.senacor.elasticsearch.evolution.core.model.MigrationVersion;
import com.senacor.elasticsearch.evolution.core.model.migration.FileNameInfoImpl;
import com.senacor.elasticsearch.evolution.core.model.migration.MigrationScriptRequest;
import com.senacor.elasticsearch.evolution.core.model.migration.MigrationScriptRequest.HttpMethod;
import com.senacor.elasticsearch.evolution.core.model.migration.ParsedMigrationScript;
import com.senacor.elasticsearch.evolution.core.model.migration.RawMigrationScript;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses the filename and the content of the migration file
 */
public class MigrationScriptParser {

  private static final String VERSION_DESCRIPTION_SEPARATOR = "__";

  public Collection<ParsedMigrationScript> parse(
      Collection<RawMigrationScript> rawMigrationScripts) {
    requireNonNull(rawMigrationScripts, "rawMigrationScripts must not be null");
    return rawMigrationScripts.stream()
        .map(this::parse)
        .toList();
  }

  ParsedMigrationScript parse(RawMigrationScript rawMigrationScript) {
    return new ParsedMigrationScript(
        parseFileName(rawMigrationScript.fileName()),
        rawMigrationScript.content().hashCode(),
        parseContent(rawMigrationScript)
    );
  }

  private MigrationScriptRequest parseContent(RawMigrationScript script) {
    MigrationScriptRequest res = new MigrationScriptRequest();

    final AtomicReference<ParseState> state = new AtomicReference<>(ParseState.METHOD_PATH);
    for (String line : script.content().split("\n", -1)) {
      if (!line.trim().startsWith("#") && !line.trim().startsWith("//")) {
        switch (state.get()) {
          case METHOD_PATH:
            parseMethodWithPath(res, line);
            state.set(ParseState.HEADER);
            break;
          case HEADER:
            if (line.trim().isEmpty()) {
              state.set(ParseState.CONTENT);
            } else {
              parseHeader(res, line);
            }
            break;
          case CONTENT:
            if (!res.isBodyEmpty()) {
              res.addToBody("\n");
            }
            res.addToBody(line);
            break;
          default:
            throw new UnsupportedOperationException("state '" + state + "' not supported");
        }
      }
    }

    return res;
  }

  private void parseHeader(MigrationScriptRequest res, String line) {
    String[] header = line.trim().split("[:=]", 2);
    if (header.length != 2) {
      throw new MigrationException(
          ("can't parse header: '%s'. Header must be separated by ':' and should look like this: "
           + "'Content-Type: application/json'").formatted(line));
    }
    res.addHttpHeader(header[0].trim(), header[1].trim());
  }

  private void parseMethodWithPath(MigrationScriptRequest res, String line) {
    String[] methodAndPath = line.trim().split(" +", 2);
    if (methodAndPath.length != 2) {
      throw new MigrationException(
          ("can't parse method and path: '%s'. Method and path must be separated by space and "
           + "should look like this: 'PUT /my_index'").formatted(line));
    }
    res.setHttpMethod(HttpMethod.create(methodAndPath[0]))
        .setPath(methodAndPath[1].trim());
  }

  /**
   * Extracts the schema version and the description from a migration name formatted as
   * 1_2__Description.
   *
   * @param migrationName The migration name to parse. Should not contain any folders or packages.
   * @return The extracted schema version.
   */
  FileNameInfo parseFileName(String migrationName) {
    String cleanMigrationName = cleanMigrationName(migrationName);
    int separatorPos = cleanMigrationName.indexOf(VERSION_DESCRIPTION_SEPARATOR);

    String version;
    String description;
    if (separatorPos < 0) {
      throw new MigrationException(
          ("Description in migration filename is required: '%s'. It should look like this: "
           + "'%s1.2Vsome_desctiption here.http'").formatted(migrationName,
              VERSION_DESCRIPTION_SEPARATOR));
    }

    description = cleanMigrationName.substring(
        separatorPos + VERSION_DESCRIPTION_SEPARATOR.length()).replace("_", " ");
    version = requireNotBlank(cleanMigrationName.substring(0, separatorPos),
        "Wrong versioned migration name format: '" + migrationName
        + "' (It must contain a version and should look like this: "
        + "V1.2" + VERSION_DESCRIPTION_SEPARATOR + description + ".http" + ")");

    MigrationVersion migrationVersion = MigrationVersion.fromVersion(version);
    requireCondition(migrationVersion,
        vers -> vers.isMajorNewerThan("0"),
        "used version '%s' in migration file '%s' is not allowed. "
        + "Major version must be greater than 0",
        migrationVersion, migrationName);
    return new FileNameInfoImpl(migrationVersion, description, migrationName);
  }

  /**
   * remove prefix and suffix
   */
  private String cleanMigrationName(String migrationName) {
    if (migrationName.toLowerCase().endsWith(".http")) {
      return StringUtils.substringBefore(migrationName.substring(1), ".http");
    }
    throw new MigrationException(
        ("Wrong versioned migration name format: '%s'. It must end with a configured "
         + "suffix: '.http'").formatted(migrationName));
  }

  private enum ParseState {
    METHOD_PATH,
    HEADER,
    CONTENT
  }
}

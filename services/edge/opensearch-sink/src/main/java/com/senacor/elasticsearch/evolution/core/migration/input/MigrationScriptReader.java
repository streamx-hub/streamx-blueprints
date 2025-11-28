package com.senacor.elasticsearch.evolution.core.migration.input;

import com.senacor.elasticsearch.evolution.core.MigrationException;
import com.senacor.elasticsearch.evolution.core.model.migration.RawMigrationScript;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

public class MigrationScriptReader {

  private static final Logger logger = Logger.getLogger(MigrationScriptReader.class);

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final String FILE_PREFIX = "file:";

  private final List<String> locations;

  /**
   * @param locations Locations of migrations scripts, e.g classpath:es/migration or
   *                  file:/home/migration
   */
  public MigrationScriptReader(List<String> locations) {
    this.locations = locations;
  }

  public List<RawMigrationScript> read() {
    return this.locations.stream()
        .flatMap(location -> {
          try {
            return readFromLocation(location);
          } catch (URISyntaxException | IOException e) {
            throw new MigrationException(
                "couldn't read scripts from %s".formatted(location), e);
          }
        })
        .distinct()
        .toList();
  }

  /**
   * Reads migration scripts from a specific location
   *
   * @param location path where to look for migration scripts
   * @return a list of {@link RawMigrationScript}
   * @throws URISyntaxException if the location is not formatted strictly according to RFC2396 and
   *                            cannot be converted to a URI.
   * @throws IOException        if an I/O error is thrown when accessing the files at the
   *                            location(s).
   */
  protected Stream<RawMigrationScript> readFromLocation(String location)
      throws URISyntaxException, IOException {
    if (location.startsWith(CLASSPATH_PREFIX)) {
      return readScriptsFromClassPath(location);

    } else if (location.startsWith(FILE_PREFIX)) {
      return readScriptsFromFilesystem(location);
    } else {
      throw new MigrationException(("""
          could not read location path %s, \
          should look like this: %ses/migration or this: %s/home/scripts/migration\
          """).formatted(
          location, CLASSPATH_PREFIX, FILE_PREFIX));
    }
  }

  private Stream<RawMigrationScript> readScriptsFromFilesystem(String location) throws IOException {
    String locationWithoutPrefix = StringUtils.substringAfter(location, FILE_PREFIX);
    URI uri = Paths.get(locationWithoutPrefix).toUri();
    logger.debugf("URI of location '%s' = '%s'", location, uri);
    Path path = Paths.get(uri);
    return Files.find(path, 10, (pathToCheck, basicFileAttributes) ->
            !basicFileAttributes.isDirectory()
            && basicFileAttributes.size() > 0
            && isValidFilename(pathToCheck.getFileName().toString()))
        .flatMap(file -> {
          logger.debugf("reading migration script '%s' from filesystem...", file);
          String filename = file.getFileName().toString();
          try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return readBuffer(reader, filename);
          } catch (IOException e) {
            throw new MigrationException("can't read script from filesystem: " + file.getFileName(),
                e);
          }
        });
  }

  private Stream<RawMigrationScript> readScriptsFromClassPath(String location) {
    String locationWithoutPrefix = StringUtils.substringAfter(location, CLASSPATH_PREFIX);
    String locationWithSlash = StringUtils.prependIfMissing(locationWithoutPrefix, "/");
    List<RawMigrationScript> scripts = new ArrayList<>();
    collectMigrationScripts(locationWithSlash, scripts);
    return scripts.stream();
  }

  private void collectMigrationScripts(String path, List<RawMigrationScript> scripts) {
    try {
      InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(path);
      if (resourceAsStream == null) {
        logger.infof("Not existing path %s", path);
        return;
      }

      // use static line separator to get predictable and system independent checksum later
      String content = new String(resourceAsStream.readAllBytes()).replaceAll("\\R", "\n");
      logger.infof("Resource %s has content %s", path, content);

      String fileName = StringUtils.substringAfterLast(path, "/");

      logger.infof("Reading %s", fileName);
      if (isValidFilename(fileName)) {
        logger.infof("Adding script %s", fileName);
        scripts.add(new RawMigrationScript(fileName, content));
      } else {
        for (String line : content.lines().toList()) {
          collectMigrationScripts(path + "/" + line, scripts);
        }
      }
    } catch (IOException e) {
      throw new MigrationException("can't read script from classpath: " + path, e);
    }
  }

  Stream<RawMigrationScript> readBuffer(BufferedReader reader, String filename) throws IOException {
    StringBuilder sb = new StringBuilder();
    int ch;
    while ((ch = reader.read()) != -1) {
      sb.append((char) ch);
    }
    // use static line separator to get predictable and system independent checksum later
    String content = sb.toString().replaceAll("\\R", "\n");

    if (content.isEmpty()) {
      return Stream.empty();
    }

    return Stream.of(new RawMigrationScript(filename, content));
  }

  private boolean hasValidSuffix(String path) {
    return path.toLowerCase().endsWith(".http");
  }

  private boolean isValidFilename(String fileName) {
    return hasValidSuffix(fileName) && fileName.startsWith("V");
  }
}

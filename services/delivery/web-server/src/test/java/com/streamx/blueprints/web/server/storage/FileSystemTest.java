package com.streamx.blueprints.web.server.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.common.Assert.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.file.FileSystemException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FileSystemTest {

  @ConfigProperty(name = "streamx.blueprints.web.resources.directory")
  String tempDirectory;

  @Inject
  FileSystem tested;

  @Test
  void expectEmptyFileCreatedWhenDataIsEmpty() throws IOException {
    Path path = Path.of(tempDirectory, "empty.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, new byte[0])
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertTrue(Files.exists(path));
    assertEquals(0L, Files.size(path));
  }

  @Test
  void expectFileWithDataCreated() throws IOException {
    Path path = Path.of(tempDirectory, "sample.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, "test-value".getBytes(StandardCharsets.UTF_8))
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertTrue(Files.exists(path));
    assertLinesMatch(List.of("test-value"), Files.readAllLines(path));
  }

  @Test
  void expectFileAndParendDirectoriesCreated() {
    Path path = Path.of(tempDirectory, "some/directory/test.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, new byte[0])
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertTrue(Files.exists(path));
  }

  @Test
  void expectFileOverwrittenWhenAlreadyExists() throws IOException {
    Path path = Path.of(tempDirectory, "file.txt");

    Files.writeString(path, "existing-value");
    assertTrue(Files.exists(path));

    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, "new-value".getBytes(StandardCharsets.UTF_8))
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertTrue(Files.exists(path));
    assertLinesMatch(List.of("new-value"), Files.readAllLines(path));
  }

  @Test
  void expectFileDeletedWhenFileExists() throws IOException {
    Path path = Path.of(tempDirectory, "file.txt");

    Files.writeString(path, "expectFileDeletedWhenFileExists");
    assertTrue(Files.exists(path));

    UniAssertSubscriber<Void> subscriber =
        tested.deleteFile(path)
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();

    assertFalse(Files.exists(path));
  }

  @Test
  void expectFailureWhenFileDoesNotExist() {
    Path path = Path.of(tempDirectory, "file.txt");

    assertFalse(Files.exists(path));

    UniAssertSubscriber<Void> subscriber =
        tested.deleteFile(path)
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    Throwable t = subscriber.awaitFailure().getFailure();

    subscriber.awaitFailure().assertFailedWith(FileSystemException.class);
  }

}
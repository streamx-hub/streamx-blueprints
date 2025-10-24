package com.streamx.blueprints.web.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.web.server.Configuration;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.file.FileSystemException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FileSystemTest {

  @Inject
  Configuration configuration;

  @Inject
  FileSystem tested;

  @Test
  void expectEmptyFileCreatedWhenDataIsEmpty() {
    Path path = getPathToStorageFile("empty.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, new byte[0])
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertThat(path)
        .exists()
        .hasSize(0);
  }

  @Test
  void expectFileWithDataCreated() {
    Path path = getPathToStorageFile("sample.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, "test-value".getBytes(StandardCharsets.UTF_8))
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertThat(path)
        .exists()
        .hasContent("test-value");
  }

  @Test
  void expectFileAndParendDirectoriesCreated() {
    Path path = getPathToStorageFile("some/directory/test.txt");
    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, new byte[0])
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertThat(path).exists();
  }

  @Test
  void expectFileOverwrittenWhenAlreadyExists() throws IOException {
    Path path = getPathToStorageFile("file.txt");

    Files.writeString(path, "existing-value");
    assertThat(path).exists();

    UniAssertSubscriber<Void> subscriber =
        tested.writeFile(path, "new-value".getBytes(StandardCharsets.UTF_8))
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();
    assertThat(path)
        .exists()
        .hasContent("new-value");
  }

  @Test
  void expectFileDeletedWhenFileExists() throws IOException {
    Path path = getPathToStorageFile("file.txt");

    Files.writeString(path, "expectFileDeletedWhenFileExists");
    assertThat(path).exists();

    UniAssertSubscriber<Void> subscriber =
        tested.deleteFile(path)
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem();

    assertThat(path).doesNotExist();
  }

  @Test
  void expectFailureWhenFileDoesNotExist() {
    Path path = getPathToStorageFile("file.txt");
    FileUtils.deleteQuietly(path.toFile());

    UniAssertSubscriber<Void> subscriber =
        tested.deleteFile(path)
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitFailure().assertFailedWith(FileSystemException.class);
  }

  private Path getPathToStorageFile(String relativePath) {
    return Path.of(configuration.storageRootDirectory(), relativePath);
  }

}
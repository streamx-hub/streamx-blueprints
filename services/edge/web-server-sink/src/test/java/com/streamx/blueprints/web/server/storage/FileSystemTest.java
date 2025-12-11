package com.streamx.blueprints.web.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.web.server.Configuration;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.file.FileSystemException;
import jakarta.inject.Inject;
import java.io.IOException;
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
    writeFile(path, "");

    assertThat(path)
        .exists()
        .hasSize(0);
  }

  @Test
  void expectFileWithDataCreated() {
    Path path = getPathToStorageFile("sample.txt");
    writeFile(path, "test-value");

    assertThat(path)
        .exists()
        .hasContent("test-value");
  }

  @Test
  void expectFileAndParentDirectoriesCreated() {
    Path path = getPathToStorageFile("some/directory/test.txt");
    writeFile(path, "");

    assertThat(path).exists();
  }

  @Test
  void expectFileOverwrittenWhenAlreadyExists() throws IOException {
    Path path = getPathToStorageFile("file.txt");

    Files.writeString(path, "existing-value");
    assertThat(path).exists();

    writeFile(path, "new-value");

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

  private void writeFile(Path path, String content) {
    tested.writeFile(path, content.getBytes())
        .subscribe()
        .withSubscriber(UniAssertSubscriber.create())
        .awaitItem();
  }
}
package com.streamx.blueprints.web.server.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.file.FileSystemException;
import jakarta.inject.Inject;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FileSystemResourceStorageTest {

  @InjectMock
  FileSystem fileSystem;

  @Inject
  FileSystemResourceStorage tested;

  @Test
  void shouldHandleNoSuchFile() {
    when(fileSystem.deleteFile(any())).thenReturn(
        Uni.createFrom().failure(() -> new FileSystemException("simulate NoSuchFileException",
            new NoSuchFileException("no file"))));

    UniAssertSubscriber<Void> subscriber =
        tested.delete("any")
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem().assertCompleted();
  }

  @Test
  void shouldHandleDirectoryNotEmpty() {
    when(fileSystem.deleteFile(any())).thenReturn(
        Uni.createFrom().failure(
            () -> new FileSystemException("simulate DirectoryNotEmptyException",
                new DirectoryNotEmptyException("no file"))));

    UniAssertSubscriber<Void> subscriber =
        tested.delete("any")
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitItem().assertCompleted();
  }

  @Test
  void shouldFailOnFileSystemException() {
    when(fileSystem.deleteFile(any())).thenReturn(
        Uni.createFrom().failure(() -> new FileSystemException("some file system issue")));

    UniAssertSubscriber<Void> subscriber =
        tested.delete("any")
            .subscribe().withSubscriber(UniAssertSubscriber.create());

    subscriber.awaitFailure();
  }

}
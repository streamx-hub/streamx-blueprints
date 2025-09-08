package dev.streamx.blueprints.web.delivery.storage;

import static java.util.Objects.requireNonNull;

import io.smallrye.mutiny.Uni;
import io.vertx.core.file.FileSystemException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FileSystemResourceStorage {

  @Inject
  Logger log;

  @ConfigProperty(name = "streamx.blueprints.web-delivery-service.resources.directory",
      defaultValue = "/tmp/streamx")
  String storageRootDirectory;

  @Inject
  FileSystem fs;

  @PostConstruct
  void init() {
    log.infof(
        "Stating with options: streamx.blueprints.web-delivery-service.resources.directory=%s",
        storageRootDirectory);
  }

  public Uni<Void> add(String path, byte[] data) {
    log.tracef("Adding resource: %s", path);
    requireNonNull(data);
    return fs.writeFile(storagePath(path), data)
        .onFailure().invoke(failure -> log.errorf(failure, "Failed to write %s", path));
  }

  public Uni<Void> delete(String path) {
    log.tracef("Deleting resource: %s", path);
    return fs.deleteFile(storagePath(path))
        .onFailure()
        .recoverWithUni(failure -> handleDeleteFailure(path, failure));
  }

  private Uni<Void> handleDeleteFailure(String path, Throwable failure) {
    if (isNoSuchFileException(failure)) {
      log.warnf(failure, "File do not exist. Path: %s", path);
      return Uni.createFrom().nullItem();
    } else if (isDirectoryNotEmptyException(failure)) {
      log.warnf(failure, "Directory is not empty. Path: %s", path);
      return Uni.createFrom().nullItem();
    }

    log.errorf(failure, "Delete failed. Path: %s");
    return Uni.createFrom().failure(failure);
  }

  public String getStorageRootDirectory() {
    return storageRootDirectory;
  }

  private boolean isNoSuchFileException(Throwable throwable) {
    return throwable instanceof FileSystemException fileSystemException
        && fileSystemException.getCause() instanceof NoSuchFileException;
  }

  private boolean isDirectoryNotEmptyException(Throwable throwable) {
    return throwable instanceof FileSystemException fileSystemException
        && fileSystemException.getCause() instanceof DirectoryNotEmptyException;
  }

  private Path storagePath(String path) {
    validatePath(path);
    return Path.of(storageRootDirectory, path);
  }

  private void validatePath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Invalid resource path: " + path);
    }
  }

}

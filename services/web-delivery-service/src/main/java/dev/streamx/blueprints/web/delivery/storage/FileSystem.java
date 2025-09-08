package dev.streamx.blueprints.web.delivery.storage;

import static java.util.Objects.requireNonNull;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.jboss.logging.Logger;

@ApplicationScoped
class FileSystem {

  @Inject
  Logger log;

  @Inject
  Vertx vertx;

  Uni<Void> writeFile(Path path, byte[] data) {
    requireNonNull(path);
    requireNonNull(data);
    log.tracef("Saving file: %s", path);
    return vertx.fileSystem()
        .writeFile(path.toString(), Buffer.buffer(data))
        // Create directories only when it's needed. In most of the cases there are more
        // files than directories, so this method requires less IO operations
        .onFailure().recoverWithUni(e ->
            vertx.fileSystem().mkdirsAndForget(path.getParent().toString())
                .writeFile(path.toString(), Buffer.buffer(data))
        )
        .invoke(() -> log.tracef("File updated: %s", path));
  }

  Uni<Void> deleteFile(Path path) {
    requireNonNull(path);
    log.trace("Deleting file: " + path);
    return vertx.fileSystem()
        .delete(path.toString())
        .invoke(() -> log.tracef("File deleted: %s", path));
  }
}

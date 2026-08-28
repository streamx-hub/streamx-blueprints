# sql-state-repository

A module responsible for persistent storage of `IndexableResource` state in an SQL database.

Data is received from CloudEvents (`published` / `unpublished`) and stored as a resource together with its facets and fields.

Currently supported backend: **SQLite**, with connections managed by a HikariCP connection pool. The database runs in WAL mode, which allows concurrent reads without blocking each other or being blocked by writes.

## Configuration

All properties use the `streamx.blueprints.sql-state-repository` prefix.

| Property | Description | Default value |
|---|---|---|
| `sql-state-repository.backend` | Database backend to use. Currently, only `sqlite` is supported. | `sqlite` |
| `sql-state-repository.sqlite.path` | Root directory where SQLite database files are created (one `.db` file per `identifier`, in a separate subdirectory for each `instance-id`). | `/tmp/sqlite` |
| `sql-state-repository.sqlite.max-pool-size` | Maximum HikariCP connection pool size per database (per `.db` file). | `4` |
| `sql-state-repository.sqlite.max-busy-timeout` | Time (in ms) for which the SQLite driver waits and retries acquiring the write lock before returning a `database is locked` error. | `5000` |

The `streamx.service.instance-id` property is also used. It is provided by `streamx-service-mesh` and identifies the service instance, determining the subdirectory in which the database files for that instance are created.

### Validation

Both `instance-id` and `identifier` (the database name provided when creating the repository) must match the `^[a-zA-Z0-9-.]+$` pattern. Only letters, digits, hyphens, and dots are allowed.

An invalid value results in an `IllegalArgumentException` being thrown when the repository is created.

## Concurrency Considerations

- Each operation (`query`, `executeQuery`, `transaction`) obtains its own independent connection from the pool and returns it when the operation completes. Transaction state is not shared between threads.
- SQLite physically allows only a single writer at a time. Concurrent writes are safely queued using `max-busy-timeout` rather than being executed in parallel.
- Increasing `max-pool-size` does not speed up writes due to SQLite's engine-level limitation. It can, however, improve performance when handling a large number of concurrent reads.
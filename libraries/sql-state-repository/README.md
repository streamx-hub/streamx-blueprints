# SQL State Repository

A CDI-based service for creating and accessing SQL repositories identified by a service instance and repository identifier.

## Overview

The main entry point is `SqlRepositoryFactory`. It provides the `getOrCreate` method, which returns a `SqlRepository` for a given identifier.

Currently, the service supports the **SQLite** backend.

## Usage in a Quarkus Service

The `identifier` is used to create a database file.

Example:

```java
@Inject
SqlRepositoryFactory repositoryFactory;

public void init() {
  SqlRepository repository = repositoryFactory.getOrCreate("indexable-resources");
}
```

## Configuration

The service uses MicroProfile Config to determine its runtime configuration.

| Property | Required | Default | Description |
|---|---|---|---|
| `streamx.blueprints.sql-state-repository.backend` | No | `sqlite` | Repository backend to use |
| `streamx.service.instance-id` | No | `unnamed` | Identifies the service instance and is used when resolving databases |

For example:

```properties
streamx.blueprints.sql-state-repository.backend=sqlite
streamx.service.instance-id=my-service
```

## Identifiers

Both `streamx.service.instance-id` and the repository `identifier` are validated before a repository is created.

Allowed characters are:

- letters (`a-z`, `A-Z`)
- digits (`0-9`)
- dashes (`-`)
- dots (`.`)

The identifier must match:

```text
^[a-zA-Z0-9-.]+$
```

Examples of valid identifiers:

```text
orders
orders-v2
orders.eu
service-1.eu
```

Examples of invalid identifiers:

```text
orders_test
orders/test
orders test
orders@production
```
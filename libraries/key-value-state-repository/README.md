# Key-Value State Repository

A high-performance, pluggable state management library for Quarkus-based `delivery` and `edge` services.
It provides a unified API for persistent or volatile state storage,
allowing you to switch between **In-Memory** and **RocksDB** backends via configuration.

## Features
 - **Typed Storage**: Native support for Java objects with automatic Jackson serialization.
 - **Dual Backends**: Use In-Memory for speed/ephemeral state or RocksDB for persistence.
 - **Quarkus Native Support**: Fully compatible with `GraalVM` native image builds.
 - **Scoped Repositories**: Segregate state using unique identifiers for each service instance.

## Usage in a Quarkus Service

To use the repository, inject the `RepositoryFactory` and initialize your specific `StateRepository`
during the component's startup phase.

### Example: Storing Page State

```java
  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<Page> pagesState;

  @PostConstruct
  void initRepository() {
    pagesState = repositoryFactory.getOrCreate("pages", Page.class);
  }
```

**Naming Constraints**: The repository identifier (e.g., "pages") must be a valid slug.
It can only contain alphanumeric characters, dashes (-), and dots (.)

### Data Integration

The repository is designed to be reactive and fits perfectly into a message-driven architecture.
You can populate or synchronize the state using SmallRye Reactive Messaging (`@Incoming`).

#### Example: Syncing State via CloudEvents

In a typical event-driven service, you can use the repository to maintain a local "projection"
of your state based on incoming events from a message broker (like Pulsar, Kafka or RabbitMQ).

```java
  @Incoming(Channels.INCOMING_PAGES_STATE)
  public void registerPage(CloudEvent pageEvent) {
    String subject = CloudEventUtils.getSubject(pageEvent);
    String eventType = pageEvent.getType();

    if (Page.TYPE_PUBLISHED.equals(eventType)) {
      Page data = requireNonNull(CloudEventUtils.getData(pageEvent, Page.class));
      pagesState.put(subject, data);
    }
    else if (Page.TYPE_UNPUBLISHED.equals(eventType)) {
      pagesState.remove(subject);
    }
  }
```

## Serialization Requirements

To ensure high performance with RocksDB backend, the `RocksDB` repository uses `Jackson` for data binding.

Objects stored in the repository must be capable of a round-trip serialization without data loss.

You can verify compatibility with this simple test:

```java
  ObjectMapper objectMapper = new ObjectMapper();

  T value = ...;
  Class<T> valueClass = value.getClass();

  byte[] serializedValue = objectMapper.writeValueAsBytes(value);
  T deserializedValue = objectMapper.readValue(serializedValue, valueClass);
```

Note: To ensure your objects are correctly serialized in Quarkus Native mode,
you must annotate the class with `@io.quarkus.runtime.annotations.RegisterForReflection`.
This allows Jackson to access the class fields via reflection within the native environment.

### Specialized Support: CloudEvents

The repository includes a built-in handler for `io.cloudevents.CloudEvent objects`.
This uses custom CloudEvent serialization methods to preserve all metadata and context attributes
(like subject, source, and type) when persisting to `RocksDB`.

### In-Memory Optimization
When using the In-Memory backend, objects are stored as-is (references) without serialization,
providing maximum performance for ephemeral data.

## Repository Backends

The library supports two backend implementations.
You can toggle between them using the configuration property `streamx.blueprints.key-value-state-repository.backend`.

### 1. In-Memory Backend (Default)
If the backend property is omitted or set to In-Memory, the state is stored in a `ConcurrentHashMap`.

Lifecycle: State is lost when the service restarts.

### 2. RocksDB Backend
If set to `rocksdb`, the library uses an embedded RocksDB instance for storage.
This allows for datasets larger than the available RAM and provides on-disk persistence.

 - **Root Directory**: Configure the base path for storage using the `streamx.blueprints.key-value-state-repository.rocksdb.path` property.
 - **Default Path**: If no path is provided, `/tmp/rocksdb` is used.

#### Persistence & Stateless Services
By externalizing the RocksDB directory via Docker Volumes,
you can ensure that the state persists across container restarts.
Furthermore, when combined with state channels (as shown in the Usage section),
the service can technically remain "stateless" at the architectural level:
it can reconstruct its local projection from the message broker at any time,
using RocksDB as a high-performance local buffer.

## Quarkus Native Support

Using RocksDB in a GraalVM native image can be challenging due to
its heavy reliance on JNI (Java Native Interface) and platform-specific native libraries.

This library provides a dedicated Quarkus extension to automate this process.
To enable RocksDB in **Quarkus Native mode**, add the following dependency to your service's `pom.xml`:
```xml
<dependency>
  <groupId>com.streamx.blueprints</groupId>
  <artifactId>quarkus-rocksdb-native-support</artifactId>
  <version>${project.version}</version>
</dependency>
```

### What this extension handles:
 - **Native Library Injection**: Automatically detects the target architecture (e.g., x86_64 vs. aarch64)
   and bundles the correct RocksDB native library into the executable.

 - **JNI Registration**: Registers the necessary internal RocksDB classes for JNI access to prevent runtime crashes.

 - **Initialization Management**: Ensures that the native engine is initialized at runtime
   in the production container rather than at build-time on the developer's machine.
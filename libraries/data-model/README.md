# This module contains data models related to handling CloudEvents exchanged by services

## CloudEvents Data Model
Java Objects representing cloudEvents payloads consumed and produced by blueprints. It only applies for cloudEvents exposed outside of the service. If service has data object used internally it should not be a part of the library. It should be defined inside the actual service.

CloudEvent type should:
1. start with reverse domain - com.streamx.blueprints
2. dots separates logical blocks
3. '-' can be used when multiword
4. vX denotes the version - must be changed when a not backward compatible change is introduced
5. No requirement to contain "published/unpublished". It is up to service what types are supported and how they are processed

Examples:
- com.streamx.blueprints.page.published.v1
- com.streamx.blueprints.temperature.changed.v1

All cloudEvents types should be linked to a given data model. Data model should provide const with supported types.

CloudEvents with payload from this library should use "application/json" as serialization method for cloud event data exchange

Data model objects may extend BaseModel that provides payload type

### JSON Serialization and Deserialization in Quarkus Native Mode

To ensure data model classes can be correctly serialized and deserialized with jackson library
when running in Quarkus native mode (for example as CloudEvent payloads or when explicitly calling `objectMapper.writeValueAsString` on them),
the following requirements must be met:

- **Non-record classes**:  
For any data model class that is not a Java `record`, the primary constructor must be annotated with `@JsonCreator`
to enable proper Jackson deserialization.


- **Shared data model library classes**:  
Every class from the `data-model` library must be listed in the `src/main/resources/META-INF/native-image/reflect-config.json` file,
so reflection metadata is preserved during native image compilation.


- **Service-local model classes**:  
For internal data model classes defined within a service module,
annotate each class with `@io.quarkus.runtime.annotations.RegisterForReflection` to make them available for reflection at runtime.
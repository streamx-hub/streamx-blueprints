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

The blueprints project uses Jackson library for serializing and deserializing data.

When running in **Quarkus native mode**, Jackson (which relies on reflection internally) requires
certain configuration to correctly serialize and deserialize objects - for example, when handling **CloudEvent payloads** or explicitly calling `objectMapper.writeValueAsString()` and similar methods.

To ensure proper behavior:

#### Register classes for reflection

Any class that needs to be serialized or deserialized at runtime must be annotated with `@io.quarkus.runtime.annotations.RegisterForReflection`.

#### Handle non-record classes
If the class is not a Java record, its primary constructor must be annotated with `@com.fasterxml.jackson.annotation.JsonCreator`.


This ensures Jackson can instantiate the object during deserialization.

Note: Java records automatically expose their canonical constructor for Jackson, so `@JsonCreator` is not required for them.
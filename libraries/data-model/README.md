# This module contains data models related to handling CloudEvents exchanged by services

## CloudEvents Data Model
Java Objects representing cloudEvents payloads consumed and produced by blueprints. It only applies for cloudEvents exposed outside of the service. If service has data object used internally it should not be a part of the library. It should be defined inside the actual service.

CloudEvent type should:
1. start with revers domain - dev.streamx.blueprints
2. dots separates logical blocks
3. '-' can be used when multiword
4. vX denotes the version - must be changed when no backward compatible change is introduced
5. No requirement to contains "published/unpublished". It is up to service what types are supported and how they are processed

Examples:
- dev.streamx.blueprints.page.published.v1
- dev.streamx.blueprints.temperature.changed.v1

All cloudEvents types should be linked to a given data model. Data model should provide const with supported types.

CloudEvents with payload from this library should use "application/json" as serialization method for cloud event data exchange

Data model objects may extend BaseModel that provides payload type


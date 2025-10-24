# Event Converter

The Event Converter service processes incoming CloudEvents whose payloads are instances of,
or extend, the `com.streamx.blueprints.data.Resource` type.

Upon receiving an event, the service creates a transformed copy where:
 - The payload is converted into an `IndexableResource` object.
 - The event type is updated to match the corresponding `IndexableResource` event type.

The transformed event is then emitted to the `indexable-resources` outgoing channel.

## Configuration

Currently, the service's behavior is not configurable

### Channels

Incoming channel: `resources`
Outgoing channel: `indexable-resource`

### Example environment variables config

```
MP_MESSAGING_INCOMING_RESOURCES_REF: "persistent://streamx/inbox.data"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCES_REF: "persistent://streamx/outbox.indexable-resources"
```

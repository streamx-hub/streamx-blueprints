# Indexable Resources Producer

Process pages and fragments to extract indexable data.
The incoming metadata are passed to outgoing indexable objects.

## Configuration
- `streamx.blueprints.indexable-resources-producer.index-fragments` - property to decide whether a _fragment_ should be indexed. 
`false` by default. Decision can be overridden by specifying the `indexable` property of the _fragment_ message.  
If fragment is not indexable, the unpublish event is sent for outgoing indexable fragment.

### Channels

Incoming channels:

- `pages`
- `fragments`

Outgoing channels:

- `indexable-resources`
- `indexable-resource-fragments`

### Example environment variables config

```
MP_MESSAGING_INCOMING_PAGES_TOPIC: "persistent://streamx/inboxes/pages"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCES_TOPIC: "persistent://streamx/outboxes/indexable-resources"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCE_FRAGMENTS_TOPIC: "persistent://streamx/outboxes/indexable-resource-fragments"
```

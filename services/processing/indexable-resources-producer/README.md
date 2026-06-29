# Indexable Resources Producer

Process pages and fragments to extract indexable data.
The incoming metadata are passed to outgoing indexable objects.

## Configuration

- `streamx.blueprints.indexable-resources-producer.index-fragments` - property to decide whether a
  _fragment_ should be indexed.
  `false` by default. Decision can be overridden by specifying the `indexable` extension of the
  _fragment_ event.  
  If fragment is not indexable, the unpublish event is sent for outgoing indexable fragment.
- `streamx.blueprints.indexable-resources-producer.include-facets` - property to decide whether a
  _facets_ should be included.
  `false` by default.
- `streamx.blueprints.indexable-resources-producer.configurations.<name>.selector` - CSS element
  selector, that finds elements matching a query.
- `streamx.blueprints.indexable-resources-producer.configurations.<name>.keys` - metadata field name
  that contains property names to be extracted as metadata keys,
  e.g. `property`, it can be arrayed: `property,name`.
- `streamx.blueprints.indexable-resources-producer.configurations.<name>.key-delimiter` - delimiter
  used to identify metadata properties and strip the prefix from extracted metadata keys,
  e.g. `facets:`.
- `streamx.blueprints.indexable-resources-producer.configurations.<name>.values` - metadata field
  name that contains values associated with extracted metadata keys,
  e.g. `content`, it can be arrayed: `content,data`.
- `streamx.blueprints.indexable-resources-producer.configurations.<name>.single-attr` - indicates
  that the same attribute contains both the key and the value. `false` by default. When enabled,
  values are extracted from the attributes listed in keys, and values does not need to be
  configured.

### Channels

Incoming channels:

- `pages`
- `fragments`

Outgoing channels:

- `indexable-resources`
- `indexable-resource-fragments`

### Example environment variables config

```
MP_MESSAGING_INCOMING_PAGES_REF: "persistent://streamx/inbox.pages"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCES_REF: "persistent://streamx/outbox.indexable-resources"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCE_FRAGMENTS_REF: "persistent://streamx/outbox.indexable-resource-fragments"
```

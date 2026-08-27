# Indexable Resources Producer

Process pages and fragments to extract indexable data.
The incoming metadata are passed to outgoing indexable objects.

## Configuration

- `streamx.blueprints.indexable-resources-producer.index-fragments` - property to decide whether a
  _fragment_ should be indexed.
  `false` by default. Decision can be overridden by specifying the `indexable` extension of the
  _fragment_ event.  
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
MP_MESSAGING_INCOMING_PAGES_REF: "persistent://streamx/inbox.pages"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCES_REF: "persistent://streamx/outbox.indexable-resources"
MP_MESSAGING_OUTGOING_INDEXABLE-RESOURCE_FRAGMENTS_REF: "persistent://streamx/outbox.indexable-resource-fragments"
```
# Search Feed Extractor - XPath Configuration

## Overview

The `streamx.blueprints.indexable-resources-producer.search-feed-extractor.xpath.fields` configuration defines how fields are extracted from an HTML document using XPath.

Each field configuration specifies:

* How to locate matching elements
* How to extract field keys and values
* Whether the field is indexed as a facet or not indexed at all
* Optional transformations applied to extracted data

## Configuration Example

```yaml
streamx:
  blueprints:
    indexable-resources-producer:
      search-feed-extractor:
        xpath:
          fields:
            field-name:
              no-index: true|false
              facet: true|false
              element-selector: "<xpath>"
              key-selector: "<xpath>"
              value-selector: "<xpath>"
              key-processors:
                - name: <processor>
                  config: <optional>
              value-processors:
                - name: <processor>
                  config: <optional>
```

## Field Properties

| Property           | Description                                                  |
|--------------------|--------------------------------------------------------------|
| `no-index`         | If `true`, the resource is not indexed                       |
| `facet`            | If `true`, the field is available for filtering/aggregation. |
| `element-selector` | XPath expression used to locate source elements.             |
| `key-selector`     | XPath expression used to extract the field name.             |
| `key`              | Static field name. Alternative to `key-selector`.            |
| `value-selector`   | XPath expression used to extract the field value.            |
| `value`            | Static field value. Alternative to `value-selector`.         |
| `key-processors`   | Processing pipeline applied to extracted keys.               |
| `value-processors` | Processing pipeline applied to extracted values.             |

## Field Definitions

### `eds`

Extracts facets from `<meta>` tags using the `property` attribute.

```yaml
eds:
  facet: true
  element-selector: "//*[local-name()='meta'][starts-with(@property, 'facets:')]"
  key-selector: "./@property"
  value-selector: "./@content"
```

Example:

```html
<meta property="facets:brand" content="Apple">
```

Result:

```json
{
  "brand": "Apple"
}
```

Key processing:

```yaml
key-processors:
  - trim
  - lowercase
  - removeStart: facets:
```

Transforms:

```
facets:Brand → brand
```

---

## Available Processors

Processors are executed in the order they are defined.

### `trim`

Removes leading and trailing whitespace.

Example:

```
"  value  " → "value"
```

---

### `lowercase`

Converts text to lowercase.

Example:

```
"BRAND" → "brand"
```

---

### `removeStart`

Removes a prefix.

Configuration:

```yaml
- name: removeStart
  config: "facets:"
```

Example:

```
facets:brand → brand
```

---

### `split`

Splits a value using the configured delimiter.

Configuration:

```yaml
- name: split
  config: ">"
```

Example:

```
A>B>C
```

Result:

```json
[
  "A",
  "B",
  "C"
]
```

---

## Processing Rules

* `key-selector` and `value-selector` extract values dynamically from the document.
* `key` and `value` define static values.
* `key-processors` run before storing the field name.
* `value-processors` run before storing the field value.
* Processors are applied sequentially.
* XPath expressions use `local-name()` to support XML namespaces.

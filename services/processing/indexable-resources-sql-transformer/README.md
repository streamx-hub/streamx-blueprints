# Indexable Resources SQL Transformer

The **Indexable Resources SQL Transformer** is a StreamX processing service that consumes indexable resources, stores their normalized state in a local SQLite database, and periodically publishes the required data to the `data` channel.

## Overview

The service has two main responsibilities:

1. Consume `Indexable` resources from the `indexable-resources` channel and persist them as normalized data in SQLite.
2. Periodically inspect the stored state and publish the required feed data to the `data` channel.

Incoming resources update the local state immediately, while feed generation is performed by a scheduled job. This allows multiple changes to be combined into a single generation cycle.

The service follows a materialized-tate approach:

```text
Published / unpublished resource
            │
            ▼
   Indexable Resource
            │
            ▼
      Normalization
            │
            ▼
       SQLite store
            │
            ▼
     Dirty / changed state
            │
            ▼
     Scheduled materialization
            │
            ▼
       Feed data
            │
            ▼
          data

```

## Configuration

> **Important:** Configuration names must use **kebab-case** (lowercase letters separated by hyphens). **camelCase is not supported.
> Please also keep in mind that the configuration must be provided in Properties format. YAML is used only to make the configuration options easier to visualize.**


The service is configured under:

```yaml
streamx:
  blueprints:
    indexable-resources-sql-transformer:
```

A complete example:

```yaml
streamx:
  blueprints:
    indexable-resources-sql-transformer:

      # Data persisted in the local SQLite state
      persisted-data:
        fields:
          - url
          - author
          - description
          - publication_date
          - modification_date
        facets:
          - size
          - category

      # Feed generation scheduling and dirty-state handling
      dirty-check:
        interval: 5s
        delay: 10s
        max-dirty-sequence-count: 12

      # Whether full resource content is persisted
      include-content: false

      # SQL-based output feeds
      transformations:
        latest-article-rss:
          sql-query: "SELECT * FROM indexable_resource"
```

### `persisted-data`

Defines which parts of an `IndexableResource` are persisted and made available to SQL transformations.

#### `fields`

The configured fields are:

- `url`
- `author`
- `description`
- `publication_date`
- `modification_date`

These fields are stored in the normalized SQLite state and can be used by SQL queries.

#### `facets`

The configured facets are:

- `size`
- `category`

Facets are persisted as part of the normalized resource state and can be queried by transformations where applicable.

### `include-content`

Controls whether the resource content is included in the persisted state.

```yaml
include-content: false
```

With the default configuration above, the service does **not** persist the full resource content. This keeps the local state focused on the metadata required by the configured transformations.

### `dirty-check`

The dirty-check configuration controls when changes trigger feed generation.

```yaml
dirty-check:
  interval: 5s
  delay: 10s
  max-dirty-sequence-count: 12
```

| Property | Description |
|---|---|
| `interval` | Frequency at which the scheduled job checks whether a new feed should be generated. |
| `delay` | Initial delay before the scheduled job starts. |
| `max-dirty-sequence-count` | Maximum number of dirty-resource sequences tracked by the dirty-state manager. |

When an `IndexableResource` is received, the service marks the state as dirty. It does not immediately generate a feed. The scheduled job later checks whether a new dirty sequence requires an output event.

This batching behavior prevents a feed from being generated for every individual resource change.

## SQL Transformations

Transformations define the feeds produced by the service.

```yaml
transformations:
  latestArticlesRss:
    sql-query: "SELECT * FROM indexable_resource"
```

Each transformation has:

- a **name**, which becomes the subject of the emitted CloudEvent;
- a **SQL query**, which is executed against the local SQLite state.

For example:

```yaml
latest-article-rss:
  sql-query: "SELECT * FROM indexable_resource"
```

produces an event with:

```text
subject = latest-articles-rss
```

The query result is serialized into the following payload structure:

```json
{
  "resources": [
    {
      "...": "..."
    }
  ]
}
```

The payload is emitted with the `data/json` content type.

### Adding a Transformation

Multiple transformations can be configured:

```yaml
transformations:
  latest-articles-rss:
    sql-query: "SELECT * FROM indexable_resource"

  popular-articles:
    sql-query: |
      SELECT *
      FROM indexable_resource
      WHERE category = 'popular'
      ORDER BY publication_date DESC
      LIMIT 20
```

Whenever a feed generation cycle is triggered, the service executes every configured transformation and publishes one CloudEvent per transformation.

## Input

The service consumes CloudEvents from the:

```text
indexable-resources
```

channel.

The event data is expected to contain an `IndexableResource`.

## Output

The generated events are published to the configured outgoing transformations channel.

For the example transformation:

```yaml
latest-articles-rss:
  sql-query: "SELECT * FROM indexable_resource"
```

the resulting CloudEvent has:

```text
subject: latest-articles-rss
data content type: data/json
```

with data equivalent to:

```json
{
  "resources": [
    {
      "url": "...",
      "author": "...",
      "description": "...",
      "publication_date": "...",
      "modification_date": "..."
    }
  ]
}
```

The exact resource representation depends on the fields and facets configured under `persisted-data`.
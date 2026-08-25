# Indexable Resources SQL Transformer

TODO: NOT FINISHED !!!

The **Indexable Resources SQL Transformer** is a StreamX processing service that consumes indexable resources, stores their normalized state in a local SQLite database, and periodically publishes the required feed data to the `data` channel.

## Overview

The service has two main responsibilities:

1. Consume `Indexable` resources from the `indexable-resources` channel and persist them as normalized data in SQLite.
2. Periodically inspect the stored state and publish the required feed data to the `data` channel.

Incoming resources update the local state immediately, while feed generation is performed by a scheduled job. This allows multiple changes to be combined into a single generation cycle.

The service follows a materialized-state approach:

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

## Normalized representation of page

TODO: Trzeba uzupełnić jakie typy przyjmuje
```
{
    subject
    url
    title
    description
    publication_date
    modification_date
    tags
    author
    image
    language
    content_type
    metadata
}
```



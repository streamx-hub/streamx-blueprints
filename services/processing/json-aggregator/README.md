# Json Data Aggregator

This module allows json data aggregation using 2 features: merging objects and creating arrays of multivalues data.

Merging:

Data objects from incoming `data` channel are merged according to configuration. Data with namespace
equal to configured `master-namespace` is required to create output. Optional objects with 
namespaces equal to any of configured `optional-namespaces` are added to output object. 
Objects are matched by key part after namespace.
Same key part after namespace is added to created output object after configured 
`output-namespace`. 
Published output resource use optional `output-type` or if not configured, type of master resource. 
Output is produced to outgoing `aggregated-data`.

Creating array of multivalues data:

Creates data with array of merged objects from incoming `multivalued-data` channel to outgoing
`aggregated-multivalued-data` data channel. Incoming objects are picked by configured
`master-namespace` and aggregated by following key part till next `:` separator. 
Outgoing object use `output-namespace` and optional `output-type`.

Please note that output of MultiValue function can be also an optional-namespace for Regular data aggregation function. 
In this case one should avoid publication of this output bypassing the MultiValue function because the outcome might be hard to predict and most probably not desired.

Example:
1. Publication of review:1:1
2. Publication of review:1:2
3. Outcome reviews:1
4. reviews:1 has the content of merged values from review:1:1 and review:1:2
5. Publication of reviews. This causes overwriting the reviews generated from previous step

## Configuration

`streamx.blueprints.json-aggregator.configurations` - named configurations with
fields:

- `master-namespace` - namespace that is mandatory to produce aggregated data. If no master namespace is present no output is generated
- `optional-namespaces` - namespaces of the data to be merged 
- `output-type` - type of generated aggregated data; optional
- `output-namespace` - namespaces of generated aggregated data

Example:

``` properties
streamx.blueprints.json-aggregator.configurations.pim.master-namespace=pim
streamx.blueprints.json-aggregator.configurations.pim.optional-namespaces=price,reviews
streamx.blueprints.json-aggregator.configurations.pim.output-type=product/variant
streamx.blueprints.json-aggregator.configurations.pim.output-namespace=product
streamx.blueprints.json-aggregator.configurations.reviews.master-namespace=review
streamx.blueprints.json-aggregator.configurations.reviews.output-namespace=reviews
```

### Channels

Incoming channels:

- `data`
- `multivalued-data`

Outgoing channels:

- `aggregated-data`
- `aggregated-multivalued-data`

### Example environment variables config

```
MP_MESSAGING_INCOMING_DATA_REF: "persistent://streamx/inbox.data"
MP_MESSAGING_INCOMING_MULTIVALUED-DATA_REF: "persistent://streamx/inbox.data"
MP_MESSAGING_OUTGOING_AGGREGATED-DATA_REF: "persistent://streamx/relay.aggregated-data"
MP_MESSAGING_OUTGOING_AGGREGATED-MULTIVALUED-DATA_REF: "persistent://streamx/inbox.data"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_PIM_MASTER_NAMESPACE: "pim"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_PIM_OPTIONAL_NAMESPACES: "price,reviews"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_PIM_OUTPUT_TYPE: "product/variant"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_PIM_OUTPUT_NAMESPACE: "product"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_REVIEWS_MASTER_NAMESPACE: "review"
STREAMX_BLUEPRINTS_JSON_AGGREGATOR_CONFIGURATIONS_REVIEWS_OUTPUT_NAMESPACE: "reviews"
```
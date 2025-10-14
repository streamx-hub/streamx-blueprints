# Data Collector

Generates new data using collectors. Optionally publish data object to web resources outgoing
channel (see configuration).

Scaling of this service is not supported.
See [ProcessDataFunction](src/main/java/dev/streamx/blueprints/data/collector/ProcessDataFunction.java).

The group by value added to the published key is stripped from non-alphanumeric characters and
spaces are replaced with underscores.

## Collectors

### aggregate-by-property-value

Aggregates by grouping the data by the value of configured property from the data object.

Produced data are JSON objects containing `key` field with same value as the message key and
`values` array with collected data.

Properties specific for this collector:

`output-key-prefix` - required; value of the group-by property will be added to this prefix in the
output data key

`filter-by` - optional; see [filter-by section](#filter-by) for details

`group-by` - optional; property name used to group the data, could be a field name, like `category`
where
the `category` is field in the data object or nested like `category/name` where `category` is nested
field name in the data object and `name` is the property in the `category` object; if not set all
the data are in 1 group and noting is added to the `output-key-prefix` for the produced output data
key - same result for data with blank value of the `group-by` property

`sort-by` - optional; property name used to sort the data; could be nested like `group-by`

`sort-mode` - optional; `asc` or `desc`; `asc` by default

`max` - optional; max number of results in the output data; `10` by default

## Configuration

`streamx.blueprints.data-collector.web-resources.filters` - optional list of
regex; if incoming data (from `data` incoming channel) key mach any of the patterns then the data
will be sent to outgoing `web-resources` channel; values could be comma separated
`streamx.blueprints.data-collector.web-resources.outgoing-prefix` - optional
prefix string added before output key; `.json` suffix is added always

`streamx.blueprints.data-collector.dirty-check.interval` - how often new
publications should be checked  
`streamx.blueprints.data-collector.dirty-check.delay` - when first new
publications should be checked
`streamx.blueprints.data-collector.dirty-check.max-dirty-sequence-count` - how
long wait for new publications to make batch sitemap update

`streamx.blueprints.data-collector.configurations` - collections configuration 
map with fields:

- `data-key-match-pattern` and `data-type-match-pattern`  - data key and type patters to select data
  to be used by collector instance; optional values but at least one of the patterns must be
  configured; if both configured, both must match
- `output-data-type` - optional; type set on produced data object; if configured, overrides the type
  returned from collector implementation
- `collector` - id of the collector used to process the data
- `properties` - properties map specific for the connector

Examples:

```
STREAMX_BLUEPRINTS_DATA_COLLECTOR_WEB-RESOURCES_FILTERS: "collected:.*"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_WEB-RESOURCES_OUTGOING-PREFIX: "_data/"
```

```
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_DATA_KEY_MATCH_PATTERN: "product:.*"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_DATA_TYPE_MATCH_PATTERN: "product/simple"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_OUTPUT_DATA_TYPE: "collection/products"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_COLLECTOR: "aggregate-by-property-value"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_PROPERTIES_OUTPUT-KEY-PREFIX: "collected:products:cheapest-by-category:"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_PROPERTIES_GROUP-BY: "category"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_PROPERTIES_SORT-BY: "price/value"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_PROPERTIES_SORT-MODE: "asc"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_CHEAPEST-PRODUCTS-BY-CATEGORY_PROPERTIES_MAX: "8"
```

```
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_POPULAR-PRODUCTS_DATA-KEY-MATCH-PATTERN: "product:.*"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_POPULAR-PRODUCTS_COLLECTOR: "aggregate-by-property-value"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_POPULAR-PRODUCTS_PROPERTIES_OUTPUT-KEY-PREFIX: "collected:products:most-popular"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_POPULAR-PRODUCTS_PROPERTIES_SORT-BY: "popularityRanking"
STREAMX_BLUEPRINTS_DATA_COLLECTOR_CONFIGURATIONS_POPULAR-PRODUCTS_PROPERTIES_SORT-MODE: "desc"
```

### Channels

Incoming channels:

- `data` - data to process

Outgoing channels:

- `collected-data` - produced collected data
- `web-resources` - data to be exposed as web resources

### Data processing

#### filter-by

Data processing uses [json-path](https://github.com/json-path/JsonPath) library for validation
processing. Property can have a single
query or multiple coma seperated queries, like example:

* \$[?(@.id == 'B072ZLCB3M')]
* \$[*].attributes[?(@.name == 'height' && @.value == '18.9')]
* \$[*].attributes[?(@.name == 'height' && @.value == '18.9')],\$[*]
  .categories[?(@.name == 'End Tables')]

Multi-queries have logical AND between checks. In order to approve item as valid both has to pass.

For more details about usage please
follow [Getting started](https://github.com/json-path/JsonPath?tab=readme-ov-file#getting-started)
For development purposes use this [online console](https://jsonpath.com) for your filter-by JsonPath
queries 

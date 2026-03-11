# Opensearch Sink

Consumes indexable resources and fragments from incoming `indexable-resources` and `indexable-resource-fragments` channels and persists them in Opensearch.
Expose the `/search/{searchTemplateId}` endpoint that executes the search template identified by the `searchTemplateId` path. 
This endpoint rewrites all query parameters to search template parameters.

The content of indexable resources may contain `{{#include src="fragmentKey"}}` directives, which are placeholders to be replaced by the fragment identified by the `fragmentKey` key.
If a search result contains phrases contained in fragment, then all indexable resources containing this fragment will be returned.

## Predefined searches

As this project is a blueprint, it contains predefined search templates. 
These searches are exposed via the `/search/{searchName}` endpoint. Each named search accepts specific query parameters.

Example named searches:
- `path` - simple search finding by value provided by `key`. Query parameters:
  - `path` (required) - `key` of ingested message (usually equal to `path` of indexed file) 
- `query` - more advanced search that supports full-text search of `indexable-resource`. It also supports indexed fragments (nested fragments are not supported). Query parameters:
  - `query` (required) - phrase that is searched in title, content (including fragments).
  - `size` (optional) - limits the search result
  - `type` (optional) - filters results to specified type prefix

The search templates defined above are included if the 
`streamx.blueprints.opensearch-sink.migration-script-locations` property 
contains `file:/deployments/opensearch/service-init/default/search-templates` or `file:/deployments/opensearch/service-init/default/`.


## Configuration

`streamx.blueprints.opensearch-sink.allowed-json-paths` - 
JSON paths used to filter Opensearch search results.
Filtered JSON is returned by the `/search/{searchTemplateId}` endpoint. 

Default: `took,timed_out,hits.max_score,hits.total..*,hits.hits.*['_id'],hits.hits.*['_score'],hits.hits.*['_source']..*, hits.hits.*.highlight..*`

`streamx.blueprints.opensearch-sink.migration-script-locations` -
Allows you to specify custom scripts that will be executed when Opensearch is launched.
Scripts should match [Elasticsearch-evolution migration script format](https://github.com/senacor/elasticsearch-evolution?tab=readme-ov-file#41-migration-script-file-content)
Locations must start with `file:`. Multiple locations should be separated with `,`.

At least `file:/deployments/opensearch/service-init/default/index-definition` is required to index incoming items.

Default: `file:/deployments/opensearch/service-init`

`streamx.blueprints.opensearch-sink.cluster-health-wait-timeout-seconds` -
Specifies the maximum duration (in seconds) the service will wait for the OpenSearch cluster
to reach a healthy status before proceeding with migrations.
This prevents the application from starting against an unhealthy or initializing cluster.

Default: `60`

### Opensearch configuration

This section applies to the default Opensearch docker image (`docker.io/opensearchproject/opensearch:2.16.0`).

For security reasons Opensearch does not provide default password for `admin` user - 
it must be set before Opensearch starts e.g. by defining `OPENSEARCH_INITIAL_ADMIN_PASSWORD` environmental variable.
If the container does not start up, it may be  because provided password is too weak.

Opensearch Sink Service also needs to know the localization of Opensearch. 
Specify it by setting the `QUARKUS_ELASTICSEARCH_HOSTS` environmental variable.

#### Security

For local environment development purposes 
[disabling security plugin](https://opensearch.org/docs/latest/security/configuration/disable-enable-security/) may be acceptable. 
This will disable authentication and authorization, allowing the `http` protocol to be accepted. 

WARNING: It's strongly discouraged to disable Security plugin for production environment.
For other production best practises, see [here](https://opensearch.org/docs/latest/security/configuration/best-practices/).

##### SSL

By default, http requests are rejected.

This is a consequence of setting the `plugins.security.ssl.http.enabled` property to `true` for this image.
To allow http requests, set the container's environmental variable `plugins.security.ssl.http.enabled` to `false`.

##### Opensearch client credentials.

By default, the enabled security plugin also verifies credentials sent with http requests as basic auth.

In this case, you need to provide credentials to the Opensearch Sink Service.
You can do this by defining the environmental variables `QUARKUS_ELASTICSEARCH_USERNAME` and `QUARKUS_ELASTICSEARCH_PASSWORD`.

For local development purposes, you can use the predefined user `admin` 
and password defined in the Opensearch environmental variable `OPENSEARCH_INITIAL_ADMIN_PASSWORD`.

### Channels

Incoming channels:

- `indexable-resources`
- `indexable-resource-fragments`

### Example environment variables config

1.Security plugin disabled:
* Opensearch Sink Service environmental variables:
```
MP_MESSAGING_INCOMING_INDEXABLE-RESOURCES_REF: "persistent://streamx/outbox.indexable-resources"
MP_MESSAGING_INCOMING_INDEXABLE-RESOURCE-FRAGMENTS_REF: "persistent://streamx/outbox.indexable-resource-fragments"
QUARKUS_ELASTICSEARCH_HOSTS: "localhost:9200"
```
* Opensearch environmental variables:
```
DISABLE_SECURITY_PLUGIN: "true"
OPENSEARCH_INITIAL_ADMIN_PASSWORD: "myStrongPassword123@456"
discovery.type: "single-node"
```

2.Security plugin enabled:
* Opensearch Sink Service environmental variables:
```
MP_MESSAGING_INCOMING_INDEXABLE-RESOURCES_REF: "persistent://streamx/outbox.indexable-resources"
MP_MESSAGING_INCOMING_INDEXABLE-RESOURCE-FRAGMENTS_REF: "persistent://streamx/outbox.indexable-resource-fragments"
QUARKUS_ELASTICSEARCH_USERNAME: "admin"
QUARKUS_ELASTICSEARCH_PASSWORD: "myStrongPassword123@456"
QUARKUS_ELASTICSEARCH_HOSTS: "localhost:9200"
```
* Opensearch environmental variables:
```
OPENSEARCH_INITIAL_ADMIN_PASSWORD: "myStrongPassword123@456"
discovery.type: "single-node"
plugins.security.ssl.http.enabled: "false"
```

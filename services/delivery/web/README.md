# Blueprint Web Delivery Service

Stores and serves pages, fragments, assets and web resources via HTTP at path resolved from Quasar key metadata value:
`<namespace>/<key-without-namespace>`

Note that the `{{#include ...}` expressions must contain same path as is used for storage/serving the fragment
or additional HTTP server with host for each namespace directory should be setup - then fragments should be resolved using
the namespace directory.

It recommended to configure `streamx.blueprints.web.default-namespace` when working with namespaces.

## Configuration

`streamx.blueprints.web.resources.directory` - web resources storage location,
default: `/tmp/streamx`

`streamx.blueprints.web.default-namespace` - optional value, will be used as fallback value if resource has not namespace

`streamx.url-include-replacement-provider` - an URL Include Replacement Provider. Available predefined values are: `EsiInclude` and `SsiInclude`.
This is an optional configuration entry. When specified, the service receives access to `urlIncludeReplacer` object.
This object is capable of parsing incoming pages and replace URL include directives (`{{#include src="url"}}`) in the page content with supplied replacement, such as Server Side Include tags or Edge Side Include tags.
A service can also define its own custom `UrlIncludeReplacer`. See `WebDeliverySink` and its unit tests for example use.

`streamx.url-include-replacement.allowed-types` - List of CloudEvent types eligible for URL include replacement. 
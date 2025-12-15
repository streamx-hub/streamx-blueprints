# Web Server Sink

Stores and serves pages, fragments, assets and web resources via HTTP at path resolved from Quasar key metadata value:
`<namespace>/<key-without-namespace>`

Note that the `{{#include ...}` expressions must contain same path as is used for storage/serving the fragment
or additional HTTP server with host for each namespace directory should be setup - then fragments should be resolved using
the namespace directory.

It is recommended to configure `streamx.blueprints.web-server-sink.default-namespace` when working with namespaces.

## Configuration

`streamx.blueprints.web-server-sink.storage-root-directory` - web resources storage location,
default: `/tmp/streamx`
`streamx.blueprints.web-server-sink.html-resource-types` -  List of CloudEvent types eligible for URL include replacement. Resources with these types are stored in filesystem as 'index.html' when CloudEvent 'subject' has no url extension

`streamx.blueprints.web-server-sink.default-namespace` - optional value, will be used as fallback value if resource has not namespace

`streamx.blueprints.web-server-sink.process-namespace-in-ingestionKeys` - When set to `true`,
the subjects of incoming events are parsed to find a colon (`:`), which is treated as a namespace separator.
The colon is replaced with a slash (`/`) before the event data (incoming resource) is saved to disk.

Set this to `false` to store incoming event data using the unchanged subject as the resource path.

`streamx.url-include-replacement-provider` - an URL Include Replacement Provider. Available predefined values are: `EsiInclude` and `SsiInclude`.
This is an optional configuration entry. When specified, the service receives access to `urlIncludeReplacer` object.
This object is capable of parsing incoming pages and replace URL include directives (`{{#include src="url"}}`) in the page content with supplied replacement, such as Server Side Include tags or Edge Side Include tags.
A service can also define its own custom `UrlIncludeReplacer`. See `WebServerSink` and its unit tests for example use.

# Local References Rewriter

This service accepts HTML pages and edits them to use optimized version of each image, when the images are available
on the `optimized-assets` channel.

The service sends the adjusted pages as well as non-adjusted pages to outgoing channel with the same name and metadata as the original page.
Therefore, StreamX mesh should not define any additional relaying feature for pages.

Note: if the incoming event for the page adjustment feature is not a Page Publish event,
it will be forwarded to the outgoing channel without processing.

## Configuration

`streamx.blueprints.local-references-rewriter.processed-page-path-pattern` - a Regular Expression
describing pattern for pages that should be adjusted to use optimized images instead of the original images.
The pattern is applied to paths of incoming pages. The path is received in the subject of incoming CloudEvent.

Example value of the configuration property:
`/generated/.*`

The above pattern matches all pages that have paths starting with `/generated/`.

Note: the pattern is case-insensitive.

Example:

``` properties
streamx.blueprints.local-references-rewriter.processed-page-path-pattern=.*
```

### Channels

Incoming channels:

- `incoming-pages`
- `optimized-assets`

Outgoing channels:

- `adjusted-pages`

### Example environment variables config

```
MP_MESSAGING_INCOMING_INCOMING-PAGES_REF: "persistent://streamx/inbox.pages"
MP_MESSAGING_OUTGOING_OPTIMIZED-ASSETS_REF: "persistent://streamx/relay.optimized-assets"
MP_MESSAGING_OUTGOING_ADJUSTED-PAGES_REF: "persistent://streamx/outbox.pages"
STREAMX_BLUEPRINTS_LOCAL_REFERENCES_REWRITER_PROCESSED_PAGE_PATH_PATTERN: ".*"
```

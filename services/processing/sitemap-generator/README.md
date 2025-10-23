# Sitemap Generating Service

Generates sitemap from exposed pages.

## Configuration

`streamx.blueprints.sitemap-generator.base-url` - base url of generated sitemap
`streamx.blueprints.sitemap-generator.match-key-patterns.anyPatternId` - optional key pattern to match if page should be added to sitemap. If not defined all resources are added to sitemap. You can define multiple patterns by adding the same property with different patternId.
`streamx.blueprints.sitemap-generator.output-key` - where result sitemap should be placed
`streamx.blueprints.sitemap-generator.output-type` - type of sitemap resource; optional
`streamx.blueprints.sitemap-generator.generate-lastmod-attribute` - flag if generated sitemap should contain lastmod attribute. Uses event publication date as the last mod. Default value: false

`streamx.blueprints.sitemap-generator.dirty-check.interval` - how often new publications should be checked  
`streamx.blueprints.sitemap-generator.dirty-check.delay` - when first new publications should be checked
`streamx.blueprints.sitemap-generator.dirty-check.max-dirty-sequence-count` - how long wait for new publications to make batch sitemap update

### Channels

Incoming channels:
- `incoming-pages` - watching newly published pages

Outgoing channels:
- `outgoing-sitemaps` - resource with result sitemap

### Example environment variables config

```
STREAMX_BLUEPRINTS_SITEMAP-GENERATOR_MATCH-KEY-PATTERNS_PATTERN1: "/*.html"
MP_MESSAGING_INCOMING_INCOMING-PAGES_REF: "persistent://streamx/inbox.pages"
MP_MESSAGING_OUTGOING_OUTGOING-SITEMAPS_REF: "persistent://streamx/outbox.web-resources"
STREAMX_BLUEPRINTS_SITEMAP_GENERATOR_BASE_URL: "http://localhost:8081"
STREAMX_BLUEPRINTS_SITEMAP_GENERATOR_OUTPUT_KEY: "sitemap.xml"
```
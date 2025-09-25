# External Resources Processing Service

The service is designed for scenarios where you want to publish a resource to StreamX
**along with all its referenced assets** - such as images, stylesheets, and scripts.
This ensures that all dependencies are also published to StreamX,
allowing you to receive a fully functional copy of the original page.
All links will be updated to point to local resources within the same StreamX instance,
making the page self-contained and operational without external dependencies.

**Note**: This service is **not intended to discover or publish web pages** that are linked from the input page.
Instead, each page should be published individually.

One valid workaround is to **publish a sitemap XML file** to StreamX.
To support this, your mesh file can include an instance of `external-resources-processing-service` configured to handle sitemap XML files.
This instance will extract the page URLs, download and publish their HTML content,
and then pass that content to a second instance of `external-resources-processing-service`.
The second instance is responsible for extracting, downloading and publishing any referenced assets, such as images, stylesheets, and scripts.

Below is a sample `mesh.yaml` file demonstrating a basic service setup that enables
the `external-resources-processing-service` instances to work together as described.

```yaml
defaultRegistry: ghcr.io/streamx-dev/streamx-blueprints
defaultImageTag: latest-jvm

sources:
  cli:
    outgoing:
      - "pages"
      - "web-resources"

processing:
  # Instance 1: process web resources
  blueprint-web-resources-external-resources-processor:
    image: external-resources-processing-service
    incoming:
      incoming-resources:
        topic: inboxes/resources
    outgoing:
      outgoing-resources:
        topic: relays/resources
    environment:
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_BASE_URL_FOR_RELATIVE_PATHS: "https://www.streamx.dev/"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_PROCESSABLE_PAYLOAD_TYPES: "web-resource"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_XML_EXTERNAL_RESOURCE_XPATH_SELECTORS: "/*[local-name()='urlset']/*[local-name()='url']/*[local-name()='loc']"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_PAGE_PUBLISH_PAYLOAD_TYPE: "page/external"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_WEB_RESOURCE_PUBLISH_PAYLOAD_TYPE: "web-resource/external"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_ASSET_PUBLISH_PAYLOAD_TYPE: "asset/external"

  # Instance 2: process pages
  blueprint-pages-external-resources-processor:
    image: external-resources-processing-service
    incoming:
      incoming-resources:
        topic: relays/resources
    outgoing:
      outgoing-resources:
        topic: outboxes/resources
    environment:
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_BASE_URL_FOR_RELATIVE_PATHS: "https://www.streamx.dev/"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_PROCESSABLE_PAYLOAD_TYPES: "page/external"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_HTML_EXTERNAL_RESOURCE_XPATH_SELECTORS: "//img/@src,//link[@rel='stylesheet']/@href,//script/@src"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_PAGE_PUBLISH_PAYLOAD_TYPE: "page/external"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_WEB_RESOURCE_PUBLISH_PAYLOAD_TYPE: "web-resource/external"
      STREAMX_BLUEPRINTS_EXTERNAL_RESOURCES_PROCESSING_SERVICE_EXTERNAL_ASSET_PUBLISH_PAYLOAD_TYPE: "asset/external"

delivery:
  blueprint-web:
    image: web-delivery-service
    incoming:
      resources:
        topic: outboxes/resources
    port: 8081
```

## Configuration

- `streamx.blueprints.external-resources-processing-service.base-url-for-relative-paths`  
Specifies the base URL used to resolve relative URLs found in the input resource content.
This allows the service to construct absolute URLs for downloading linked resources. **This setting is required**.


- `streamx.blueprints.external-resources-processing-service.processable-payload-types`  
A comma-separated list of payload types for incoming resources that the service will process.
Only resources matching one of these types will be handled. **This setting is required**.


- `streamx.blueprints.external-resources-processing-service.html-external-resource-xpath-selectors`  
A comma-separated list of `XPath` selectors used to locate external resources within incoming HTML pages.
Optional - specify this only if your service instance is intended to process HTML pages.


- `streamx.blueprints.external-resources-processing-service.html-external-resource-url-exclusions-pattern`  
A comma-separated list of regular expressions used to exclude certain external resource URLs (such as images) from processing.
Optional — this setting further filters the URLs found by the XPath selectors, limiting which resources are processed.


- `streamx.blueprints.external-resources-processing-service.xml-external-resource-xpath-selectors`  
A comma-separated list of `XPath` selectors used to locate external resources within incoming XML resources.
Optional - specify this only if your service instance is intended to process XML resources.


- `streamx.blueprints.external-resources-processing-service.xml-external-resource-url-exclusions-pattern`  
A comma-separated list of regular expressions used to exclude certain external resource URLs (such as images) from processing.
Optional — this setting further filters the URLs found by the XPath selectors, limiting which resources are processed.


- `streamx.blueprints.external-resources-processing-service.json-external-resource-jsonpath-selectors`  
A comma-separated list of `JSONPath` selectors used to locate external resources within incoming JSON resources.
Optional - specify this only if your service instance is intended to process JSON resources.


- `streamx.blueprints.external-resources-processing-service.json-external-resource-url-exclusions-pattern`  
A comma-separated list of regular expressions used to exclude certain external resource URLs (such as images) from processing.
Optional — this setting further filters the URLs found by the JSONPath selectors, limiting which resources are processed.


- `streamx.blueprints.external-resources-processing-service.external-page-publish-payload-type`  
Specifies the payload type used when publishing downloaded external pages referenced by incoming resources.
This setting is required, even if the service instance is not expected to publish any external pages.


- `streamx.blueprints.external-resources-processing-service.external-web-resource-publish-payload-type`  
Specifies the payload type used for publishing downloaded external web resources (non-pages) referenced in incoming resources.
This setting is required, even if the service instance is not expected to publish any external web resources.


- `streamx.blueprints.external-resources-processing-service.external-asset-publish-payload-type`  
Specifies the payload type used for publishing downloaded external assets referenced in incoming resources.
This setting is required, even if the service instance is not expected to publish any external assets.


- `streamx.blueprints.external-resources-processing-service.external-resource-download-timeout-milliseconds`  
Specifies the maximum time, in milliseconds, the service will wait for content to be downloaded from the source server.
Optional — the default value is 5000 (5 seconds).

All settings can be provided either through the `environment:` section of the service definition in `mesh.yaml` or via a properties file.
For reference, an example configuration is available in the `src/test/resources/application.properties` file.

### Channels

Incoming channels:

- `incoming-pages`
- `incoming-web-resources`
- `incoming-data`

Outgoing channels:

- `outgoing-pages`
- `outgoing-web-resources`
- `outgoing-data`
- `outgoing-assets`
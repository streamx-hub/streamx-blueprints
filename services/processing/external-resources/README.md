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

The project includes a sample [mesh.yaml](../../../mesh.yaml) file that demonstrates a basic service configuration,
enabling multiple `external-resources-processing-service` instances to operate together as described.

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


- `streamx.blueprints.external-resources-processing-service.external-page-emit-payload-type`  
Specifies the payload type used when emitting downloaded external pages referenced by incoming resources.
This setting is required, even if the service instance is not expected to publish any external pages.


- `streamx.blueprints.external-resources-processing-service.external-web-resource-emit-payload-type`  
Specifies the payload type used for emitting downloaded external web resources (non-pages) referenced in incoming resources.
This setting is required, even if the service instance is not expected to publish any external web resources.


- `streamx.blueprints.external-resources-processing-service.external-asset-emit-payload-type`  
Specifies the payload type used for emitting downloaded external assets referenced in incoming resources.
This setting is required, even if the service instance is not expected to publish any external assets.


- `streamx.blueprints.external-resources-processing-service.external-resource-download-timeout-milliseconds`  
Specifies the maximum time, in milliseconds, the service will wait for content to be downloaded from the source server.
Optional — the default value is 5000 (5 seconds).

All settings can be provided either through the `environment:` section of the service definition in `mesh.yaml` or via a properties file.
For reference, an example configuration is available in the `src/test/resources/application.properties` file.

### Channels

Incoming channels:

- `incoming-resources`

Outgoing channels:

- `outgoing-resources`
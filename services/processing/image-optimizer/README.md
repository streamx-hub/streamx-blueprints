# Image Optimizer

This service performs image optimization, by generating a webp version for each incoming image.
The paths and extensions of image files that should be optimized are configurable, as well as the optimization algorithm settings.
Page and asset metadata are transferred to optimized versions.

The designed behavior of optimizing images is to publish the generated webp images on the same destination (e.g. Pulsar topic) where the source images are read from.

Therefore, the source (e.g. Pulsar topic) assigned to `incoming-assets` channel should be same as the destination assigned to `optimized-assets` outgoing channel.
This is crucial for the service because incoming pages are adjusted to use the optimized images only when the images are available
on the `incoming-assets` channel.

The page adjustment feature requires its input `incoming-pages` channel and output `outgoing-pages` channel must not be using the same source and destination (e.g. same Pulsar topic).
The service sends the adjusted pages as well as non-adjusted pages to outgoing channel with the same name and metadata as the original page.
Therefore, StreamX mesh should not define any additional relaying feature for pages.

Note: if the incoming event for the page adjustment feature is not a Page Publish event,
it will be forwarded to the outgoing channel without processing.

## Configuration

`streamx.blueprints.image-optimizer.optimized-file-paths-pattern` - a Regular Expression
describing pattern for images that should be optimized. The pattern is applied to paths of incoming files.
The path is received in Key field of incoming message. The path may contain parent directories and file name, or just the file name.

Example value of the configuration property:
`/generated/.*(png|gif|jpg|jpeg)$`

The above pattern matches all files that have:
 - path starting with `/generated/`
 - any base name of file (and any intermediate directories)
 - a standard extension for image files.

Note: the pattern is case-insensitive.

`streamx.blueprints.image-optimizer.optimized-image-file-name-suffix` - a suffix that will be added
to names of optimized images.

Default value:
`-optimized`

The above setting will result in generating file name for the optimized image as `image-optimized.webp`, if the input image file is named `image.png`.

`streamx.blueprints.image-optimizer.adjusted-page-paths-pattern` - a Regular Expression
describing pattern for pages that should be adjusted to use optimized images instead of the original images.
The pattern is applied to paths of incoming pages. The path is received in Key field of incoming message

Example value of the configuration property:
`/generated/.*`

The above pattern matches all pages that have paths starting with `/generated/`.

Note: the pattern is case-insensitive.

The below configuration options refer to the cwebp library, used by image-optimizer internally.
Please refer to Webp for documentation about the below webp-related parameters and their meaning:
[Official cwebp documentation](https://developers.google.com/speed/webp/docs/cwebp)

`streamx.blueprints.image-optimizer.webp-conversion-speed` - how fast the cwebp image generation should be.
`cwebp` parameter equivalent: `-z`
Default value: `6`

`streamx.blueprints.image-optimizer.webp-conversion-quality` - specifies the compression factor while generating cwebp images.
`cwebp` parameter equivalent: `-q`
Default value: `75`

`streamx.blueprints.image-optimizer.webp-conversion-method` - specifies the compression method to use.
`cwebp` parameter equivalent: `-m`
Default value: `4`

`streamx.blueprints.image-optimizer.webp-conversion-lossless` - should the conversion of images to Webp images be loseless.
`cwebp` parameter equivalent: `-lossless`
Default value: `false`.

`streamx.blueprints.image-optimizer.webp-conversion-no-alpha` - should alpha channel be removed.
`cwebp` parameter equivalent: `-noalpha`
Default value: `false`.

`streamx.blueprints.image-optimizer.webp-conversion-multi-thread` - should the processing be performed in multiple threads.
`cwebp` parameter equivalent: `-mt`
Default value: `false`.

Example:

``` properties
streamx.blueprints.image-optimizer.optimized-file-paths-pattern=.*(png|gif|jpg|jpeg)$
streamx.blueprints.image-optimizer.optimized-image-file-name-suffix=-optimized
streamx.blueprints.image-optimizer.adjusted-page-paths-pattern=.*
streamx.blueprints.image-optimizer.webp-conversion-speed=6
streamx.blueprints.image-optimizer.webp-conversion-quality=75
streamx.blueprints.image-optimizer.webp-conversion-method=4
streamx.blueprints.image-optimizer.webp-conversion-lossless=false
streamx.blueprints.image-optimizer.webp-conversion-no-alpha=false
streamx.blueprints.image-optimizer.webp-conversion-multi-thread=false
```

### Channels

Incoming channels:

- `incoming-assets`
- `incoming-pages`

Outgoing channels:

- `optimized-assets`
- `outgoing-pages`

### Example environment variables config

```
MP_MESSAGING_INCOMING_INCOMING-ASSETS_TOPIC: "persistent://streamx/inboxes/assets"
MP_MESSAGING_OUTGOING_OPTIMIZED-ASSETS_TOPIC: "persistent://streamx/inboxes/assets"
MP_MESSAGING_INCOMING_INCOMING-PAGES_TOPIC: "persistent://streamx/inboxes/pages"
MP_MESSAGING_OUTGOING_OUTGOING-PAGES_TOPIC: "persistent://streamx/outboxes/pages"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_OPTIMIZED_FILE_PATHS_PATTERN: ".*(png|gif|jpg|jpeg)$"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_OPTIMIZED_IMAGE_FILE_NAME-SUFFIX: "-optimized"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_ADJUSTED_PAGE_PATHS_PATTERN: ".*"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_SPEED: "6"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_QUALITY: "75"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_METHOD: "4"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_LOSSLESS: "false"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_NO_ALPHA: "false"
STREAMX_BLUEPRINTS_IMAGE_OPTIMIZER_WEBP_CONVERSION_MULTI_THREAD: "false"
```

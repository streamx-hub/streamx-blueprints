# Optimized Images Generator

This service performs image optimization, by generating a webp version for each incoming image.
The paths and extensions of image files that should be optimized are configurable, as well as the optimization algorithm settings.
Asset metadata are transferred to optimized versions.

## Configuration

`streamx.blueprints.optimized-images-generator.processed-image-path-pattern` - a Regular Expression
describing pattern for images that should be optimized. The pattern is applied to paths of incoming files.
The path is received in Subject field of incoming CloudEvent.
The pattern may contain parent directories and file name, or just the file name.

Note: Only files with a non-empty name and a non-empty extension (separated by a dot) will be processed.

Example value of the configuration property:
`/generated/.*(png|gif|jpg|jpeg)$`

The above pattern matches all files that have:
 - path starting with `/generated/`
 - any base name of file (and any intermediate directories)
 - a standard extension for image files.

Note: the pattern is case-insensitive.

`streamx.blueprints.optimized-images-generator.optimized-image-file-name-suffix` - a suffix that will be added
to names of optimized images.

Default value:
`-optimized`

The above setting will result in generating file name for the optimized image as `image-optimized.webp`, if the input image file is named `image.png`.

The below configuration options refer to the cwebp library, used by optimized-images-generator internally.
Please refer to Webp for documentation about the below webp-related parameters and their meaning:
[Official cwebp documentation](https://developers.google.com/speed/webp/docs/cwebp)

`streamx.blueprints.optimized-images-generator.webp-conversion-speed` - how fast the cwebp image generation should be.
`cwebp` parameter equivalent: `-z`
Default value: `6`

`streamx.blueprints.optimized-images-generator.webp-conversion-quality` - specifies the compression factor while generating cwebp images.
`cwebp` parameter equivalent: `-q`
Default value: `75`

`streamx.blueprints.optimized-images-generator.webp-conversion-method` - specifies the compression method to use.
`cwebp` parameter equivalent: `-m`
Default value: `4`

`streamx.blueprints.optimized-images-generator.webp-conversion-lossless` - should the conversion of images to Webp images be loseless.
`cwebp` parameter equivalent: `-lossless`
Default value: `false`.

`streamx.blueprints.optimized-images-generator.webp-conversion-no-alpha` - should alpha channel be removed.
`cwebp` parameter equivalent: `-noalpha`
Default value: `false`.

Example:

``` properties
streamx.blueprints.optimized-images-generator.processed-image-path-pattern=.*(png|gif|jpg|jpeg)$
streamx.blueprints.optimized-images-generator.optimized-image-file-name-suffix=-optimized
streamx.blueprints.optimized-images-generator.webp-conversion-speed=6
streamx.blueprints.optimized-images-generator.webp-conversion-quality=75
streamx.blueprints.optimized-images-generator.webp-conversion-method=4
streamx.blueprints.optimized-images-generator.webp-conversion-lossless=false
streamx.blueprints.optimized-images-generator.webp-conversion-no-alpha=false
```

### Channels

Incoming channels:

- `incoming-assets`

Outgoing channels:

- `optimized-assets`

### Example environment variables config

```
MP_MESSAGING_INCOMING_INCOMING-ASSETS_REF: "persistent://streamx/inbox.assets"
MP_MESSAGING_OUTGOING_OPTIMIZED-ASSETS_REF: "persistent://streamx/relay.optimized-assets"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_PROCESSED_IMAGE_PATH_PATTERN: ".*(png|gif|jpg|jpeg)$"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_OPTIMIZED_IMAGE_FILE_NAME-SUFFIX: "-optimized"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_WEBP_CONVERSION_SPEED: "6"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_WEBP_CONVERSION_QUALITY: "75"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_WEBP_CONVERSION_METHOD: "4"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_WEBP_CONVERSION_LOSSLESS: "false"
STREAMX_BLUEPRINTS_OPTIMIZED_IMAGES_GENERATOR_WEBP_CONVERSION_NO_ALPHA: "false"
```

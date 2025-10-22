# Rendering Engine

Purpose of this service is to generate outgoing resource to one of the available outgoing resource
channels.

The service processes incoming renderers, data and rendering contexts. The rendering context defines
what and how should be rendered.

Outgoing resources are generated according to rendering contexts describing what and how should be
rendered (also referencing data and renderer). The rendering context contains:

- output type - page or fragment (indicates the outgoing channel)
- output key template - template used to generate the outgoing resource key; template is evaluated
  same way as the rendered template, with same data object
- renderer key - used to find related renderer containing the template, used to generate outgoing
  resource content
- data key match pattern and data type match pattern - regexes to find data (by key and/or type 
  match - one of the patterns is required, otherwise context will be ignored) to be used to generate
  output resources; 1 output resource for each matched data object

Outgoing resource content is generated using template from renderer. Outgoing resource key is
generated using the output key template from the rendering context. Both templates are evaluated
with the same data object matched according to rendering context (by data key match pattern).

The output generation may be triggered by any of incoming events for renderers, data and rendering
contexts, as soon as all needed objects are published. For example data and rendering contexts may
be waiting for renderer publication to trigger output publication. Or renderer and data may wait for
rendering contexts, etc.

Unpublish of one of elements needed for the output resource, result with unpublish of the output
object. For example unpublish of data results with unpublish of all the outputs related to this
data. Unpublish of the rendered result with unpublish of all the outputs rendered with this
renderer, etc.

Publication of new rendering context version, for example with new output key template is not
resulting with any output resources unpublishing. If needed the output resources must be unpublished
by unpublishing of the previous context first.

## Rendering requests

Processing of the incoming renderers, data and rendering contexts is not producing the outputs
events directly. Instead, the rendering requests events are created and processed by the service
using `incoming-rendering-requests` and `outgoing-rendering-requests` which should correspond to
same relay topic.

This approach is used to avoid unneeded outputs regenerations and single output generation per event
processing.

## Configuration

### Channels

Incoming channels:

- `renderers`
- `data`
- `rendering-contexts`
- `incoming-rendering-requests` - should be configured to same relay topic
  as `outgoing-rendering-requests` channel

Outgoing channels:

- `outgoing-rendering-requests` - should be configured to same relay topic
  as `incoming-rendering-requests` channel
- `pages` - generated content of 'page' output type
- `fragments` - generated content of 'fragment' output type

### Example environment variables config

```
MP_MESSAGING_INCOMING_DATA_TOPIC: "persistent://streamx/inboxes/data"
MP_MESSAGING_INCOMING_RENDERERS_TOPIC: "persistent://streamx/inboxes/renderers"
MP_MESSAGING_INCOMING_RENDERING-CONTEXTS_TOPIC: "persistent://streamx/inboxes/rendering-contexts"
MP_MESSAGING_INCOMING_INCOMING-RENDERING-REQUESTS_TOPIC: "persistent://streamx/relays/rendering-requests"
MP_MESSAGING_OUTGOING_OUTGOING-RENDERING-REQUESTS_TOPIC: "persistent://streamx/relays/rendering-requests"
MP_MESSAGING_OUTGOING_PAGES_TOPIC: "persistent://streamx/inboxes/pages"
MP_MESSAGING_OUTGOING_FRAGMENTS_TOPIC: "persistent://streamx/inboxes/fragments"
```
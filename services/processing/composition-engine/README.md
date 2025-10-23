# Composition Engine

This service generates pages from compositions and layouts.
Layout is a html page with placeholders for segments.
Placeholders are configures by using insert tags.
Outgoing page use composition key. During page publication page type is set to layout type.
Example of a layout with insert segment tags is:
```
<html>
Hello.
{{#insert name="segment-1.html"}}
  Fallback value when the segment is not found
{{}}
Here is an insert tag without a fallback value:
{{#insert name="segment-2.html"}}
And this is an insert tag with a not matching segment:
{{#insert name="segment-3.html"}}
  Not found
{{}}
</html>
```

Note:
 - fallback value is optional in the `#insert` tag (it can be empty)
 - the closing `{{}}` tag is mandatory only when fallback value is specified

To generate a page, you publish a composition that references the desired layout and supplies contents of segments that the template requires. The resulting page will be published reusing the composition's name and will have the shape of the source layout with all insert placeholders replaced with segment contents. If a segment definition is missing in the composition - the service inserts the specified fallback value, or in case of missing fallback value, it removes the insert tag completely.

A sample composition that delivers content for `segment-1.html` and `segment-2.html`:
```
{{#define name="segment-1.html"}}
<b>This is the content
of segment-1</b>

{{#define name="segment-2.html"}}
Content of segment-2
```
The layout information is sent with the composition message as a separate `layoutName` String field.

The resulting page published to the outgoing pages channel is:
```
<html>
Hello.
<b>This is the content
of segment-1</b>
Here is an insert tag without a fallback value:
Content of segment-2
And this is an insert tag with a not matching segment:
Not found
</html>
```

### Channels

Incoming channels:

- `layouts`
- `compositions`
- `incoming-page-compose-requests`

Outgoing channels:

- `pages`
- `outgoing-page-compose-requests`

Note: for the service to function correctly, the source (e.g. Pulsar topic) assigned to `incoming-page-compose-requests` channel must be same as the destination assigned to `outgoing-page-compose-requests` outgoing channel.

### Example environment variables config

```
MP_MESSAGING_INCOMING_LAYOUTS_REF: "persistent://streamx/inbox.layouts"
MP_MESSAGING_INCOMING_COMPOSITIONS_REF: "persistent://streamx/inbox.compositions"
MP_MESSAGING_INCOMING_INCOMING-PAGE-COMPOSE-REQUESTS_REF: "persistent://streamx/relay.page-compose-requests"
MP_MESSAGING_OUTGOING_PAGES_REF: "persistent://streamx/outbox.pages"
MP_MESSAGING_OUTGOING_OUTGOING-PAGE-COMPOSE-REQUESTS_REF: "persistent://streamx/relay.page-compose-requests"
```

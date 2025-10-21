package com.streamx.blueprints.opensearch.sink.store;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.Resource;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PublishedIndexableResourceFragmentsStore {

  private static final Map<String, ResourceContentAndEventTime> store = new ConcurrentHashMap<>();

  public void register(CloudEvent event) {
    String key = CloudEventUtils.getSubject(event);
    var fragment = extractPublishedFragmentContent(event);
    if (fragment != null) {
      store.put(key, new ResourceContentAndEventTime(fragment, event.getTime()));
    } else {
      store.remove(key);
    }
  }

  private String extractPublishedFragmentContent(CloudEvent event) {
    if (IndexableResourceFragment.TYPE_PUBLISHED.equals(event.getType())) {
      var fragment = CloudEventUtils.getData(event, IndexableResourceFragment.class);
      if (!Resource.isEmpty(fragment)) {
        return fragment.getContentAsString();
      }
    }
    return null;
  }

  public ResourceContentAndEventTime get(String key) {
    return store.get(key);
  }
}
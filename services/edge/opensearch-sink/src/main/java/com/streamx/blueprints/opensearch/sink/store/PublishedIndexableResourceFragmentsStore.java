package com.streamx.blueprints.opensearch.sink.store;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.opensearch.sink.Channels;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class PublishedIndexableResourceFragmentsStore {

  @Inject
  RepositoryFactory repositoryFactory;

  private StateRepository<ResourceData> store;

  @PostConstruct
  void initRepository() {
    store = repositoryFactory.getOrCreate("resource-data", ResourceData.class);
  }

  @Incoming(Channels.INDEXABLE_RESOURCE_FRAGMENTS_STATE)
  void register(CloudEvent event) {
    String key = CloudEventUtils.getSubject(event);
    var fragment = extractPublishedFragmentContent(event);
    if (fragment != null) {
      store.put(key, new ResourceData(fragment, event.getTime()));
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

  public ResourceData get(String key) {
    return store.get(key);
  }
}
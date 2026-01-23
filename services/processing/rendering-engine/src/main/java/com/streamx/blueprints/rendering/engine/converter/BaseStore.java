package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import jakarta.inject.Inject;

abstract class BaseStore<T> {

  protected StateRepository<T> store;

  @Inject
  RepositoryFactory repositoryFactory;

  protected void initRepository(String identifier, Class<T> valueClass) {
    store = repositoryFactory.getOrCreate(identifier, valueClass);
  }
}

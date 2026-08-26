package com.streamx.blueprints.state.sql.repository;

import com.streamx.blueprints.state.sql.EntityMapper;
import java.util.List;

public interface SqlRepository {

  void executeQuery(String sqlQuery);

  <T> void save(String sqlQuery, EntityMapper<T> mapper, T resource);

  <T> List<T> read(String sqlQuery, EntityMapper<T> mapper);

  String getIdentifier();
}

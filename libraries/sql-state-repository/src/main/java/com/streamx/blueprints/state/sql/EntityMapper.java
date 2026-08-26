package com.streamx.blueprints.state.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface EntityMapper<T> {

  PreparedStatement toStatement(PreparedStatement statement, T entity) throws SQLException;

  T map(ResultSet rs) throws SQLException;
}

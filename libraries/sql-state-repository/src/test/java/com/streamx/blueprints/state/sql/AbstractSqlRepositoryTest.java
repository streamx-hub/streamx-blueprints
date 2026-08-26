package com.streamx.blueprints.state.sql.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.state.sql.repository.SqlRepository.RowMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AbstractSqlRepositoryTest {

  @Inject
  TestSqlRepository repository;

  @InjectMock
  DataSource dataSource;

  @Test
  void shouldExecuteQuery() throws Exception {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);

    String sql = "CREATE TABLE test (id INT)";

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);

    repository.executeQuery(sql);

    verify(statement).execute(sql);
  }

  @Test
  void shouldThrowExceptionWhenExecuteQueryFails() throws Exception {
    when(dataSource.getConnection())
        .thenThrow(new SQLException("Connection failed"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.executeQuery("INVALID SQL"));

    assertInstanceOf(SQLException.class, exception.getCause());
    assertEquals(
        "Connection failed",
        exception.getCause().getMessage());
  }

  @Test
  void shouldQueryAndMapResults() throws Exception {
    Connection connection = mock(Connection.class);
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);

    String sql = "SELECT id, name FROM test WHERE id = ?";

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(sql))
        .thenReturn(preparedStatement);
    when(preparedStatement.executeQuery())
        .thenReturn(resultSet);

    when(resultSet.next())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);

    when(resultSet.getString("name"))
        .thenReturn("John")
        .thenReturn("Jane");

    RowMapper<String> mapper =
        rs -> rs.getString("name");

    List<String> result =
        repository.query(sql, mapper, 123);

    assertEquals(
        List.of("John", "Jane"),
        result);

    verify(preparedStatement)
        .setObject(1, 123);

    verify(preparedStatement)
        .executeQuery();

    verify(resultSet, org.mockito.Mockito.times(3))
        .next();
  }

  @Test
  void shouldReturnEmptyListWhenQueryReturnsNoRows()
      throws Exception {

    Connection connection = mock(Connection.class);
    PreparedStatement preparedStatement =
        mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);

    String sql = "SELECT id FROM test";

    when(dataSource.getConnection())
        .thenReturn(connection);

    when(connection.prepareStatement(sql))
        .thenReturn(preparedStatement);

    when(preparedStatement.executeQuery())
        .thenReturn(resultSet);

    when(resultSet.next())
        .thenReturn(false);

    RowMapper<Integer> mapper =
        rs -> rs.getInt("id");

    List<Integer> result =
        repository.query(sql, mapper);

    assertTrue(result.isEmpty());

    verify(preparedStatement)
        .executeQuery();

    verify(resultSet)
        .next();
  }

  @Test
  void shouldSetAllQueryParameters()
      throws Exception {

    Connection connection = mock(Connection.class);
    PreparedStatement preparedStatement =
        mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);

    String sql =
        "SELECT * FROM test WHERE id = ? AND name = ?";

    when(dataSource.getConnection())
        .thenReturn(connection);

    when(connection.prepareStatement(sql))
        .thenReturn(preparedStatement);

    when(preparedStatement.executeQuery())
        .thenReturn(resultSet);

    when(resultSet.next())
        .thenReturn(false);

    repository.query(
        sql,
        rs -> rs.getInt("id"),
        123,
        "John");

    verify(preparedStatement)
        .setObject(1, 123);

    verify(preparedStatement)
        .setObject(2, "John");

    verify(preparedStatement)
        .executeQuery();
  }

  @Test
  void shouldThrowExceptionWhenQueryFails()
      throws Exception {

    Connection connection = mock(Connection.class);

    String sql = "SELECT * FROM test";

    when(dataSource.getConnection())
        .thenReturn(connection);

    when(connection.prepareStatement(sql))
        .thenThrow(new SQLException("SQL error"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.query(
                sql,
                rs -> rs.getString("name")));

    assertEquals(
        "Query failed",
        exception.getMessage());

    assertInstanceOf(
        SQLException.class,
        exception.getCause());
  }

  @Test
  void shouldCommitTransaction()
      throws Exception {

    Connection connection = mock(Connection.class);

    when(dataSource.getConnection())
        .thenReturn(connection);

    String result =
        repository.transaction(conn -> "success");

    assertEquals("success", result);

    verify(connection)
        .setAutoCommit(false);

    verify(connection)
        .commit();

    verify(connection, never())
        .rollback();
  }

  @Test
  void shouldRollbackTransactionWhenTransactionFails()
      throws Exception {

    Connection connection = mock(Connection.class);

    when(dataSource.getConnection())
        .thenReturn(connection);

    RuntimeException expected =
        new RuntimeException("Something went wrong");

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.transaction(conn -> {
              throw expected;
            }));

    assertEquals(
        "Transaction failed",
        exception.getMessage());

    assertSame(
        expected,
        exception.getCause());

    verify(connection)
        .setAutoCommit(false);

    verify(connection)
        .rollback();

    verify(connection, never())
        .commit();
  }

  @Test
  void shouldRollbackWhenCommitFails()
      throws Exception {

    Connection connection = mock(Connection.class);

    when(dataSource.getConnection())
        .thenReturn(connection);

    doThrow(new SQLException("Commit failed"))
        .when(connection)
        .commit();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.transaction(
                conn -> "success"));

    assertEquals(
        "Transaction failed",
        exception.getMessage());

    assertInstanceOf(
        SQLException.class,
        exception.getCause());

    verify(connection)
        .setAutoCommit(false);

    verify(connection)
        .commit();

    verify(connection)
        .rollback();
  }

  @ApplicationScoped
  static class TestSqlRepository
      extends AbstractSqlRepository {

    @Override
    public String getIdentifier() {
      return "test";
    }
  }
}

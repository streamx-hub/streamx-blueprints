package com.streamx.blueprints.state.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.state.sql.repository.AbstractSqlRepository;
import com.streamx.blueprints.state.sql.repository.SqlRepository.RowMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractSqlRepositoryTest {

  private Connection connection;
  private Statement statement;
  private TestSqlRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    connection = mock(Connection.class);
    statement = mock(Statement.class);

    when(connection.createStatement())
        .thenReturn(statement);

    repository = new TestSqlRepository(connection);
  }

  @Test
  void shouldExecuteQuery() throws Exception {
    String sql = "CREATE TABLE test (id INT)";

    repository.executeQuery(sql);

    verify(connection).createStatement();
    verify(statement).execute(sql);
  }

  @Test
  void shouldThrowExceptionWhenExecuteQueryFails() throws Exception {
    when(connection.createStatement())
        .thenThrow(new SQLException("Connection failed"));

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> repository.executeQuery("INVALID SQL"));

    assertEquals("SQL execution failed", exception.getMessage());
    assertInstanceOf(SQLException.class, exception.getCause());
    assertEquals("Connection failed", exception.getCause().getMessage());
  }

  @Test
  void shouldQueryAndMapResults() throws Exception {
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);

    String sql = "SELECT id, name FROM test WHERE id = ?";

    when(connection.prepareStatement(sql)).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getString("name")).thenReturn("John", "Jane");

    RowMapper<String> mapper = rs -> rs.getString("name");

    List<String> result = repository.query(sql, mapper, 123);

    assertEquals(List.of("John", "Jane"), result);

    verify(preparedStatement).setObject(1, 123);
    verify(preparedStatement).executeQuery();
    verify(resultSet, times(3)).next();
  }

  @Test
  void shouldReturnEmptyListWhenQueryReturnsNoRows() throws Exception {
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    String sql = "SELECT id FROM test";

    when(connection.prepareStatement(sql)).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    RowMapper<Integer> mapper = rs -> rs.getInt("id");

    List<Integer> result = repository.query(sql, mapper);

    assertTrue(result.isEmpty());
    verify(preparedStatement).executeQuery();
    verify(resultSet).next();
  }

  @Test
  void shouldSetAllQueryParameters() throws Exception {
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);

    String sql = "SELECT * FROM test WHERE id = ? AND name = ?";

    when(connection.prepareStatement(sql)).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    repository.query(sql, rs -> rs.getInt("id"), 123, "John");

    verify(preparedStatement).setObject(1, 123);
    verify(preparedStatement).setObject(2, "John");
    verify(preparedStatement).executeQuery();
  }

  @Test
  void shouldThrowExceptionWhenQueryFails() throws Exception {
    String sql = "SELECT * FROM test";

    when(connection.prepareStatement(sql))
        .thenThrow(new SQLException("SQL error"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.query(sql, rs -> rs.getString("name")));

    assertEquals("Query failed", exception.getMessage());
    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  void shouldCommitTransaction() throws Exception {
    String result = repository.transaction(conn -> "success");

    assertEquals("success", result);

    verify(connection).setAutoCommit(false);
    verify(connection, never()).createStatement();
    verify(statement, never()).execute("PRAGMA foreign_keys = ON");
    verify(connection).commit();
    verify(connection, never()).rollback();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void shouldRollbackTransactionWhenTransactionFails() throws Exception {
    RuntimeException expected = new RuntimeException("Something went wrong");

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.transaction(conn -> {
              throw expected;
            }));

    assertEquals("Transaction failed", exception.getMessage());
    assertSame(expected, exception.getCause());

    verify(connection).setAutoCommit(false);
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void shouldRollbackWhenCommitFails() throws Exception {
    doThrow(new SQLException("Commit failed"))
        .when(connection)
        .commit();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.transaction(conn -> "success"));

    assertEquals("Transaction failed", exception.getMessage());
    assertInstanceOf(SQLException.class, exception.getCause());
    assertEquals("Commit failed", exception.getCause().getMessage());

    verify(connection).setAutoCommit(false);
    verify(connection).commit();
    verify(connection).rollback();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void shouldRestoreAutoCommitAfterTransactionFailure() throws Exception {
    RuntimeException expected = new RuntimeException("Transaction failed");

    assertThrows(
        RuntimeException.class,
        () -> repository.transaction(conn -> {
          throw expected;
        }));

    verify(connection).setAutoCommit(false);
    verify(connection).rollback();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void shouldThrowWhenRollbackFails() throws Exception {
    RuntimeException expected = new RuntimeException("Transaction failed");

    doThrow(new SQLException("Rollback failed"))
        .when(connection)
        .rollback();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> repository.transaction(conn -> {
              throw expected;
            }));

    assertEquals("Transaction failed", exception.getMessage());
    assertInstanceOf(SQLException.class, exception.getCause());
    assertEquals("Rollback failed", exception.getCause().getMessage());

    verify(connection).setAutoCommit(false);
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).setAutoCommit(true);
  }

  static class TestSqlRepository extends AbstractSqlRepository {

    TestSqlRepository(Connection connection) {
      super(connection);
    }
  }
}
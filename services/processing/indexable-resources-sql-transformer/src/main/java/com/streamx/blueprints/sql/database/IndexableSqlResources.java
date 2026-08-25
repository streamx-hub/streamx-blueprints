package com.streamx.blueprints.sql.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@RegisterForReflection
public class IndexableSqlResources {

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  private String subject;
  private String title;
  private String url;
  private String description;
  private String publicationDate;
  private String modificationDate;
  private String tags;
  private String author;
  private String image;
  private String language;
  private String contentType;
  private String metadata;

  public IndexableSqlResources(String subject, String title, String url, String description,
      String publicationDate, String modificationDate, String tags, String author, String image,
      String language, String contentType, String metadata) {
    this.subject = subject;
    this.title = title;
    this.url = url;
    this.description = description;
    this.publicationDate = publicationDate;
    this.modificationDate = modificationDate;
    this.tags = tags;
    this.author = author;
    this.image = image;
    this.language = language;
    this.contentType = contentType;
    this.metadata = metadata;
  }

  public PreparedStatement toStatement(PreparedStatement statement) throws SQLException {
    statement.setString(1, subject);
    statement.setString(2, title);
    statement.setString(3, url);
    statement.setString(4, description);
    statement.setString(5, publicationDate);
    statement.setString(6, modificationDate);
    statement.setString(7, tags);
    statement.setString(8, author);
    statement.setString(9, image);
    statement.setString(10, language);
    statement.setString(11, contentType);
    statement.setString(12, metadata);
    return statement;
  }

  public static IndexableSqlResources toEntity(String subject, String title,
      Map<String, Object> fields)
      throws JsonProcessingException {
    return new IndexableSqlResources(subject,
        title,
        toString(fields.get("url")),
        toString(fields.get("description")),
        toString(fields.get("publication_date")),
        toString(fields.get("modification_date")),
        fields.containsKey("tags") ? objectMapper.writeValueAsString(fields.get("tags")) : null,
        toString(fields.get("author")),
        toString(fields.get("image")),
        toString(fields.get("language")),
        toString(fields.get("content_type")),
        fields.containsKey("metadata")
            ? objectMapper.writeValueAsString(fields.get("metadata"))
            : null);
  }

  public static IndexableSqlResources toEntity(ResultSet resultSet) throws SQLException {
    return new IndexableSqlResources(
        resultSet.getString("subject"),
        resultSet.getString("title"),
        resultSet.getString("url"),
        resultSet.getString("description"),
        resultSet.getString("publication_date"),
        resultSet.getString("modification_date"),
        resultSet.getString("tags"),
        resultSet.getString("author"),
        resultSet.getString("image"),
        resultSet.getString("language"),
        resultSet.getString("content_type"),
        resultSet.getString("metadata")
    );
  }

  private static String toString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  public String getTitle() {
    return title;
  }

  public String getSubject() {
    return subject;
  }

  public String getUrl() {
    return url;
  }

  public String getDescription() {
    return description;
  }

  public String getPublicationDate() {
    return publicationDate;
  }

  public String getModificationDate() {
    return modificationDate;
  }

  public String getTags() {
    return tags;
  }

  public String getAuthor() {
    return author;
  }

  public String getImage() {
    return image;
  }

  public String getLanguage() {
    return language;
  }

  public String getContentType() {
    return contentType;
  }

  public String getMetadata() {
    return metadata;
  }
}

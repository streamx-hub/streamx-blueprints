package com.streamx.blueprints.state.repository.rocksdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.ce.serialization.CloudEventDeserializer;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonDeserializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.core.v1.CloudEventV1;
import jakarta.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValueWrapper<T> implements Serializable {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final CloudEventSerializer eventSerializer = new CloudEventJsonSerializer();
  private static final CloudEventDeserializer eventDeserializer = new CloudEventJsonDeserializer();
  private static final Map<String, Class<?>> valueClasses = new ConcurrentHashMap<>();

  @Nonnull
  private transient T value;

  public ValueWrapper(@Nonnull T value) {
    this.value = value;
  }

  public byte[] toByteArray() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(this);
    }
    return baos.toByteArray();
  }

  public static <T> ValueWrapper<T> fromByteArray(byte[] data) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    try (ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (ValueWrapper<T>) ois.readObject();
    }
  }

  @Serial
  private void writeObject(ObjectOutputStream out) throws Exception {
    out.defaultWriteObject();
    out.writeUTF(value.getClass().getName());

    byte[] serializedValue = serializeValue();
    out.writeInt(serializedValue.length);
    out.write(serializedValue);
  }

  private byte[] serializeValue() throws JsonProcessingException {
    if (value instanceof CloudEventV1 event) {
      return eventSerializer.serialize(event);
    }
    return objectMapper.writeValueAsBytes(value);
  }

  @Serial
  private void readObject(ObjectInputStream in) throws Exception {
    in.defaultReadObject();
    String className = in.readUTF();

    int length = in.readInt();
    byte[] value = new byte[length];
    in.readFully(value);
    this.value = deserializeValue(value, className);
  }

  private T deserializeValue(byte[] value, String className) throws Exception {
    if (className.equals(CloudEventV1.class.getName())) {
      return (T) eventDeserializer.deserialize(value);
    }
    Class<?> valueClass = getClassObject(className);
    return (T) objectMapper.readValue(value, valueClass);
  }

  private static Class<?> getClassObject(String className) throws ClassNotFoundException {
    if (!valueClasses.containsKey(className)) {
      Class<?> classObject = Class.forName(className);
      valueClasses.put(className, classObject);
    }
    return valueClasses.get(className);
  }

  public T getRawValue() {
    return value;
  }
}

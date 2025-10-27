package com.streamx.blueprints.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

public class DataModelClassesTest {

  private static final File reflectConfigFile = new File(
      "src/main/resources/META-INF/native-image/reflect-config.json");

  private static final String DATA_MODEL_CLASSES_PACKAGE = DataModelClassesTest.class
      .getPackageName();

  private static final Set<Class<?>> dataModelClasses =
      new Reflections(DATA_MODEL_CLASSES_PACKAGE)
          .getAll(Scanners.SubTypes)
          .stream()
          .filter(type -> type.startsWith(DATA_MODEL_CLASSES_PACKAGE))
          .map(DataModelClassesTest::toClass)
          .collect(Collectors.toSet());

  private static final Set<Class<?>> plainClassDataModelClasses = dataModelClasses
      .stream()
      .filter(cls -> !cls.isRecord())
      .filter(cls -> !cls.isEnum())
      .collect(Collectors.toSet());

  private static Class<?> toClass(String cls) {
    try {
      return Class.forName(cls);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Set<Class<?>> getDataModelClasses() {
    return dataModelClasses;
  }

  public static Set<Class<?>> getPlainClassDataModelClasses() {
    return plainClassDataModelClasses;
  }

  @ParameterizedTest
  @MethodSource("getDataModelClasses")
  void verifyReflectConfigJson(Class<?> dataModelClass) {
    assertThat(contentOf(reflectConfigFile))
        .contains(dataModelClass.getName());
  }

  @ParameterizedTest
  @MethodSource("getPlainClassDataModelClasses")
  void nonRecordDataModelClassesShouldContainJsonCreatorAnnotation(Class<?> dataModelClass) {
    assertThat(dataModelClass.getConstructors())
        .anyMatch(c -> c.isAnnotationPresent(JsonCreator.class));
  }

  @ParameterizedTest
  @MethodSource("getDataModelClasses")
  void dataModelClassesShouldBeNullSafe(Class<?> dataModelClass) throws Exception {
    if (Modifier.isAbstract(dataModelClass.getModifiers())) {
      return;
    }
    for (Constructor<?> constructor : dataModelClass.getConstructors()) {
      Object[] nulls = IntStream
          .rangeClosed(1, constructor.getParameterCount())
          .mapToObj(i -> null)
          .toArray(Object[]::new);
      Object dataClassInstance = constructor.newInstance(nulls);
      if (dataClassInstance instanceof Resource resource) {
        assertThat(Resource.isEmpty(resource));
        assertThat(resource.getContent()).isNull();
        assertThat(resource.getContentAsBytes()).isNull();
        assertThat(resource.getContentAsString()).isNull();
      }
      if (dataClassInstance instanceof Typed typed) {
        assertThat(typed.getType()).isNull();
      }
    }
  }

}

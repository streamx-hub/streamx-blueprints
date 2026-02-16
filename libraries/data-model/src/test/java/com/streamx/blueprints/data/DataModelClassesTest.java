package com.streamx.blueprints.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.quarkus.runtime.annotations.RegisterForReflection;
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

  private static final String DATA_MODEL_CLASSES_PACKAGE = DataModelClassesTest.class
      .getPackageName();

  private static final Set<Class<?>> dataModelClasses =
      new Reflections(DATA_MODEL_CLASSES_PACKAGE)
          .getAll(Scanners.SubTypes)
          .stream()
          .filter(type -> type.startsWith(DATA_MODEL_CLASSES_PACKAGE))
          .map(DataModelClassesTest::toClass)
          .filter(cls -> !cls.isEnum())
          .collect(Collectors.toSet());

  private static final Set<Class<?>> nonRecordDataModelClasses = dataModelClasses
      .stream()
      .filter(cls -> !cls.isRecord())
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

  public static Set<Class<?>> getNonRecordDataModelClasses() {
    return nonRecordDataModelClasses;
  }

  @ParameterizedTest
  @MethodSource("getDataModelClasses")
  void verifyReflectConfigJson(Class<?> dataModelClass) {
    assertThat(dataModelClass)
        .hasAnnotation(RegisterForReflection.class);
  }

  @ParameterizedTest
  @MethodSource("getNonRecordDataModelClasses")
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
      Object dataClassInstance = instantiateWithNullParameters(constructor);
      if (dataClassInstance instanceof Resource resource) {
        assertThat(Resource.isEmpty(resource)).isTrue();
        assertThat(resource.getContent()).isNull();
        assertThat(resource.getContentAsBytes()).isNull();
        assertThat(resource.getContentAsString()).isNull();
      }
      if (dataClassInstance instanceof Renderer renderer) {
        assertThat(renderer.template()).isNull();
        assertThat(renderer.getTemplateAsString()).isNull();
      }
      if (dataClassInstance instanceof Composition composition) {
        assertThat(composition.getLayoutKey()).isNull();
      }
      if (dataClassInstance instanceof OptimizedAsset optimizedAsset) {
        assertThat(optimizedAsset.getOriginalPath()).isNull();
      }
      if (dataClassInstance instanceof IndexableResource indexableResource) {
        assertThat(indexableResource.getFragmentKeys()).isNull();
      }
      if (dataClassInstance instanceof Typed typed) {
        assertThat(typed.getType()).isNull();
      }
    }
  }

  private static Object instantiateWithNullParameters(Constructor<?> constructor) throws Exception {
    Object[] nulls = IntStream
        .rangeClosed(1, constructor.getParameterCount())
        .mapToObj(i -> null)
        .toArray(Object[]::new);
    return constructor.newInstance(nulls);
  }

}

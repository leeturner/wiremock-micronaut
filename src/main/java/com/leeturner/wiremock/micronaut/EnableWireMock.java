package com.leeturner.wiremock.micronaut;

import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts WireMock servers for a test class. Use alongside {@code @MicronautTest}. With no {@link
 * ConfigureWireMock} anywhere on the class, one server named {@code "wiremock"} is started.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface EnableWireMock {

  /** The servers to start. */
  ConfigureWireMock[] value() default {};
}

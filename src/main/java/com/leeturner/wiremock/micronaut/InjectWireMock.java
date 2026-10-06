package com.leeturner.wiremock.micronaut;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injects the named {@code WireMockServer} into a test field or test method parameter. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWireMock {

  /** The server name. */
  String value() default "wiremock";
}

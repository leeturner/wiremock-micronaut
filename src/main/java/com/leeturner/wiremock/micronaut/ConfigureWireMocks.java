package com.leeturner.wiremock.micronaut;

import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/** Container for repeated {@link ConfigureWireMock}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMocks {
  ConfigureWireMock[] value();
}

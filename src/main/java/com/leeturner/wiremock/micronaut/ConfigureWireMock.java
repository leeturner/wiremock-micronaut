package com.leeturner.wiremock.micronaut;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;

/** Configures one WireMock server. Attributes mirror wiremock-spring-boot. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Repeatable(ConfigureWireMocks.class)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMock {

  /** Directories searched, in order, when no files location is configured. */
  List<String> DEFAULT_FILES_UNDER_DIRECTORY =
      List.of(
          "wiremock",
          "stubs",
          "mappings",
          "src/test/resources/wiremock",
          "src/test/resources/stubs",
          "src/test/resources/mappings",
          "src/integtest/resources/wiremock",
          "src/integtest/resources/stubs",
          "src/integtest/resources/mappings");

  /** Server name, used by {@link InjectWireMock}. */
  String name() default "wiremock";

  /** HTTP port: {@code 0} dynamic, {@code -1} disabled, otherwise static. */
  int port() default 0;

  /** HTTPS port: {@code -1} disabled, {@code 0} dynamic, otherwise static. */
  int httpsPort() default -1;

  /** Properties set to the HTTP port. */
  String[] portProperties() default {"wiremock.server.port"};

  /** Properties set to the HTTPS port. */
  String[] httpsPortProperties() default {"wiremock.server.httpsPort"};

  /** Properties set to {@code http://localhost:<port>}. */
  String[] baseUrlProperties() default {"wiremock.server.baseUrl"};

  /** Properties set to {@code https://localhost:<httpsPort>}. */
  String[] httpsBaseUrlProperties() default {"wiremock.server.httpsBaseUrl"};

  /** Classpath root holding {@code mappings}/{@code __files}. */
  String filesUnderClasspath() default "";

  /** Directories holding {@code mappings}/{@code __files}; the first existing one is used. */
  String[] filesUnderDirectory() default {};

  /** WireMock extensions; each needs a public no-arg constructor. */
  Class<? extends Extension>[] extensions() default {};

  /** WireMock extension factories; each needs a public no-arg constructor. */
  Class<? extends ExtensionFactory>[] extensionFactories() default {};

  /** Customizers applied last; each needs a public no-arg constructor. */
  Class<? extends WireMockConfigurationCustomizer>[] configurationCustomizers() default {};

  /** Reset the server before each test. */
  boolean resetWireMockServer() default true;

  /** Register the server as a Micronaut bean qualified {@code @Named(name)}. */
  boolean registerBean() default false;

  /** Apply response templating to every stub. */
  boolean globalTemplating() default false;

  String keystorePath() default "";

  String keystorePassword() default "";

  String keystoreType() default "";

  String keyManagerPassword() default "";

  String trustStorePath() default "";

  String trustStorePassword() default "";

  String trustStoreType() default "";

  boolean needClientAuth() default false;
}

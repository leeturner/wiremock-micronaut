package com.leeturner.wiremock.micronaut;

import static com.leeturner.wiremock.micronaut.testsupport.FixtureRunner.failureMessages;
import static org.assertj.core.api.Assertions.assertThat;

import com.leeturner.wiremock.micronaut.fixtures.Boom;
import com.leeturner.wiremock.micronaut.fixtures.ContextFailureFixture;
import com.leeturner.wiremock.micronaut.fixtures.DuplicateNameFixture;
import com.leeturner.wiremock.micronaut.fixtures.InjectParameterWithBeanFixture;
import com.leeturner.wiremock.micronaut.fixtures.MissingDirectoryFixture;
import com.leeturner.wiremock.micronaut.fixtures.NestedDeclarationFixture;
import com.leeturner.wiremock.micronaut.fixtures.NestedInjectParameterFixture;
import com.leeturner.wiremock.micronaut.fixtures.NoNoArgConstructorFixture;
import com.leeturner.wiremock.micronaut.fixtures.PropertyClashFixture;
import com.leeturner.wiremock.micronaut.fixtures.StartFailureFixture;
import com.leeturner.wiremock.micronaut.fixtures.UnknownServerFixture;
import com.leeturner.wiremock.micronaut.fixtures.WrongTypeFixture;
import com.leeturner.wiremock.micronaut.internal.WireMockServers;
import org.junit.jupiter.api.Test;

class FailureModesTest {

  @Test
  void unknownServerName() {
    assertThat(failureMessages(UnknownServerFixture.class))
        .contains("No WireMock server named 'nope'")
        .contains("Configured servers: [wiremock]");
  }

  @Test
  void wrongInjectionType() {
    assertThat(failureMessages(WrongTypeFixture.class))
        .contains("field 'server'")
        .contains("java.lang.String")
        .contains("only WireMockServer");
  }

  @Test
  void duplicateNamesInAMicronautTest() {
    assertThat(failureMessages(DuplicateNameFixture.class))
        .contains("Duplicate WireMock server name(s) [a]");
  }

  @Test
  void injectWireMockParameterWithRegisterBeanServer() {
    assertThat(failureMessages(InjectParameterWithBeanFixture.class))
        .contains("InjectParameterWithBeanFixture")
        .contains("not supported in test classes with a registerBean server")
        .contains("@Named");
  }

  @Test
  void injectWireMockParameterOnNestedClassWithRegisterBeanServer() {
    assertThat(failureMessages(NestedInjectParameterFixture.class))
        .contains("NestedInjectParameterFixture$Inner")
        .contains("not supported in test classes with a registerBean server");
  }

  @Test
  void explicitPropertyClash() {
    assertThat(failureMessages(PropertyClashFixture.class))
        .contains("Property 'x.url' is bound by more than one WireMock server");
  }

  @Test
  void declarationOnNestedClass() {
    assertThat(failureMessages(NestedDeclarationFixture.class))
        .contains("@ConfigureWireMock cannot be declared on the @Nested class")
        .contains("Move it to NestedDeclarationFixture");
  }

  @Test
  void extensionWithoutNoArgConstructor() {
    assertThat(failureMessages(NoNoArgConstructorFixture.class))
        .contains("public no-arg constructor")
        .contains("'ext'");
  }

  @Test
  void missingStubDirectory() {
    assertThat(failureMessages(MissingDirectoryFixture.class))
        .contains("None of filesUnderDirectory [does-not-exist]");
  }

  @Test
  void serverStartFailureLeavesNothingRunning() {
    assertThat(failureMessages(StartFailureFixture.class)).contains("'bad'");
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(StartFailureFixture.class.getName());
  }

  @Test
  void contextStartupFailureStillStopsServers() {
    Boom.runningWhenFailing = null;
    assertThat(failureMessages(ContextFailureFixture.class)).contains("boom");
    assertThat(Boom.runningWhenFailing).contains(ContextFailureFixture.class.getName());
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(ContextFailureFixture.class.getName());
  }
}

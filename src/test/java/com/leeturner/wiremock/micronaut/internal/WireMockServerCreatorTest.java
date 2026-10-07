package com.leeturner.wiremock.micronaut.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.message.MessageStubMapping;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.WireMockConfigurationCustomizer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.io.TempDir;

class WireMockServerCreatorTest {

  public static class UppercaseTransformer implements ResponseTransformerV2 {
    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return Response.Builder.like(response).body(response.getBodyAsString().toUpperCase()).build();
    }

    @Override
    public String getName() {
      return "uppercase";
    }
  }

  public static class NeedsArgs implements ResponseTransformerV2 {
    public NeedsArgs(String unused) {}

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return response;
    }

    @Override
    public String getName() {
      return "needs-args";
    }
  }

  public static class RecordingCustomizer implements WireMockConfigurationCustomizer {
    static final AtomicReference<String> SEEN = new AtomicReference<>();

    @Override
    public void customize(
        @NonNull WireMockConfiguration configuration, @NonNull ConfigureWireMock options) {
      SEEN.set(options.name());
      configuration.extensions(new UppercaseTransformer());
    }
  }

  public static class DisableTemplating implements WireMockConfigurationCustomizer {
    @Override
    public void customize(
        @NonNull WireMockConfiguration configuration, @NonNull ConfigureWireMock options) {
      configuration.globalTemplating(false);
    }
  }

  @ConfigureWireMock(globalTemplating = true, configurationCustomizers = DisableTemplating.class)
  static class CustomizerOverrides {}

  @ConfigureWireMock
  static class Defaults {}

  @ConfigureWireMock(port = -1, httpsPort = 0)
  static class HttpsOnly {}

  @ConfigureWireMock(filesUnderClasspath = "classpath-stubs")
  static class ClasspathStubs {}

  @ConfigureWireMock(filesUnderClasspath = "missing-stubs")
  static class MissingClasspath {}

  @ConfigureWireMock(filesUnderDirectory = {"does-not-exist", "src/test/directory-stubs"})
  static class DirectoryStubs {}

  @ConfigureWireMock(filesUnderDirectory = "does-not-exist")
  static class MissingDirectory {}

  @ConfigureWireMock(filesUnderDirectory = "src/test/message-stubs-only")
  static class MessageStubsOnlyDirectory {}

  @ConfigureWireMock(extensions = UppercaseTransformer.class)
  static class WithExtension {}

  @ConfigureWireMock(extensions = NeedsArgs.class)
  static class WithBadExtension {}

  @ConfigureWireMock(name = "custom", configurationCustomizers = RecordingCustomizer.class)
  static class WithCustomizer {}

  @ConfigureWireMock(globalTemplating = true)
  static class Templated {}

  @ConfigureWireMock(name = "tls", port = -1, httpsPort = 0, keystorePath = "does-not-exist.jks")
  static class BadKeystore {}

  private final List<WireMockServer> started = new ArrayList<>();

  @AfterEach
  void stopServers() {
    started.forEach(WireMockServer::stop);
  }

  private WireMockServer create(Class<?> fixture) {
    WireMockServer server =
        WireMockServerCreator.create(
            fixture.getAnnotation(ConfigureWireMock.class), fixture.getName());
    started.add(server);
    return server;
  }

  @Test
  void defaultsGiveARunningDynamicHttpServer() {
    WireMockServer server = create(Defaults.class);
    assertThat(server.isRunning()).isTrue();
    assertThat(server.port()).isPositive();
    assertThat(server.isHttpsEnabled()).isFalse();
  }

  @Test
  void httpCanBeDisabledAndHttpsMadeDynamic() {
    WireMockServer server = create(HttpsOnly.class);
    assertThat(server.isHttpEnabled()).isFalse();
    assertThat(server.isHttpsEnabled()).isTrue();
    assertThat(server.httpsPort()).isPositive();
  }

  @Test
  void loadsStubsFromTheClasspath() {
    WireMockServer server = create(ClasspathStubs.class);
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
  }

  @Test
  void missingClasspathLocationFails() {
    assertThatThrownBy(() -> create(MissingClasspath.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("missing-stubs")
        .hasMessageContaining("'wiremock'");
  }

  @Test
  void usesTheFirstExistingConfiguredDirectory() {
    WireMockServer server = create(DirectoryStubs.class);
    assertThat(Http.get(server.baseUrl() + "/from-directory").body()).isEqualTo("directory");
  }

  @Test
  void missingDirectoryFails() {
    assertThatThrownBy(() -> create(MissingDirectory.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("None of filesUnderDirectory [does-not-exist]")
        .hasMessageContaining("'message-mappings'");
  }

  @Test
  void directoryWithOnlyMessageMappingsIsUsed() {
    WireMockServer server = create(MessageStubsOnlyDirectory.class);
    assertThat(server.getMessageStubMappingsList())
        .extracting(MessageStubMapping::getName)
        .containsExactly("message-only");
  }

  @Test
  void appliesExtensions() {
    WireMockServer server = create(WithExtension.class);
    server.stubFor(get("/shout").willReturn(ok("hello")));
    assertThat(Http.get(server.baseUrl() + "/shout").body()).isEqualTo("HELLO");
  }

  @Test
  void extensionWithoutNoArgConstructorFails() {
    assertThatThrownBy(() -> create(WithBadExtension.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining(NeedsArgs.class.getName())
        .hasMessageContaining("'wiremock'")
        .hasMessageContaining("public no-arg constructor");
  }

  @Test
  void appliesCustomizersLast() {
    WireMockServer server = create(WithCustomizer.class);
    server.stubFor(get("/shout").willReturn(ok("hello")));
    assertThat(RecordingCustomizer.SEEN.get()).isEqualTo("custom");
    assertThat(Http.get(server.baseUrl() + "/shout").body()).isEqualTo("HELLO");
  }

  @Test
  void globalTemplatingRendersEveryStub() {
    WireMockServer server = create(Templated.class);
    server.stubFor(get("/templated").willReturn(ok("{{request.path}}")));
    assertThat(Http.get(server.baseUrl() + "/templated").body()).isEqualTo("/templated");
  }

  @Test
  void startFailureNamesTheServer() {
    assertThatThrownBy(() -> create(BadKeystore.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'tls'");
  }

  @Test
  void customizerWinsOverAnnotationSettings() {
    WireMockServer server = create(CustomizerOverrides.class);
    server.stubFor(get("/t").willReturn(ok("{{request.path}}")));
    // The annotation turned templating on; the customizer ran afterwards and turned it off.
    assertThat(Http.get(server.baseUrl() + "/t").body()).isEqualTo("{{request.path}}");
  }

  @Test
  void firstCandidateWithAnyStubFolderWins(@TempDir Path tmp) throws IOException {
    Path empty = Files.createDirectory(tmp.resolve("empty"));
    Path withFiles = Files.createDirectories(tmp.resolve("withFiles/__files")).getParent();
    Path withMappings = Files.createDirectories(tmp.resolve("withMappings/mappings")).getParent();
    assertThat(
            WireMockServerCreator.firstExistingStubDirectory(
                List.of(
                    tmp.resolve("missing").toString(),
                    empty.toString(),
                    withFiles.toString(),
                    withMappings.toString())))
        .contains(withFiles.toString());
    assertThat(
            WireMockServerCreator.firstExistingStubDirectory(
                List.of(empty.toString(), withMappings.toString())))
        .contains(withMappings.toString());
    Path withMessages =
        Files.createDirectories(tmp.resolve("withMessages/message-mappings")).getParent();
    assertThat(
            WireMockServerCreator.firstExistingStubDirectory(
                List.of(empty.toString(), withMessages.toString())))
        .contains(withMessages.toString());
    assertThat(WireMockServerCreator.firstExistingStubDirectory(List.of(empty.toString())))
        .isEmpty();
  }
}

package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// The configs the jar embeds are the bytes the repository holds, indexed whole, held to
/// their environment, and served by createMapper with no directory.
final class EmbeddedMappingsTests {

  private static final PublicKey SYSTEM_PROGRAM = fromBase58Encoded("11111111111111111111111111111111");
  private static final PublicKey KAMINO_LENDING = fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD");

  private static Path repositorySet(final GlamEnv env) {
    // the test runs with the module directory as its working directory
    return Path.of("..", "mapping", env == GlamEnv.PRODUCTION ? "production" : "staging");
  }

  @Test
  void theIndexNamesEveryEmbeddedFileAndEachOneParses() {
    final var index = EmbeddedMappings.index();
    assertFalse(index.production().isEmpty(), "no production configs indexed");
    assertFalse(index.staging().isEmpty(), "no staging configs indexed");
    assertFalse(index.source().isBlank());
    for (final var env : GlamEnv.values()) {
      final var configs = EmbeddedMappings.load(env);
      assertEquals(index.files(env).size(), configs.size(), env + ": one config per indexed file");
      for (final var config : configs) {
        assertFalse(config.ixMapConfigs().isEmpty(), env + ": a config with no instructions");
      }
    }
  }

  /// The embedded set is the repository set, byte for byte and file for file, in both
  /// environments: the index names exactly the repository's files and each resource holds
  /// its file's bytes.
  @Test
  void theEmbeddedSetEqualsTheRepositorySet() throws IOException {
    final var index = EmbeddedMappings.index();
    for (final var env : GlamEnv.values()) {
      final var directory = repositorySet(env);
      final var repositoryFiles = new TreeSet<String>();
      try (final var files = Files.list(directory)) {
        files.filter(Files::isRegularFile)
            .map(file -> file.getFileName().toString())
            .filter(name -> name.endsWith(".json"))
            .forEach(repositoryFiles::add);
      }
      assertEquals(repositoryFiles, new TreeSet<>(index.files(env)), env + ": the index does not name the repository's files");
      for (final var name : repositoryFiles) {
        final var resource = EmbeddedMappings.RESOURCE_ROOT + env.name().toLowerCase(Locale.ROOT) + '/' + name;
        assertArrayEquals(
            Files.readAllBytes(directory.resolve(name)),
            EmbeddedMappings.readResource(resource),
            env + ": " + name + " in the jar differs from the repository file"
        );
      }
      assertEquals(
          GlamVaultAccounts.loadMappingConfigs(directory).size(),
          EmbeddedMappings.load(env).size(),
          env + ": the directory loader and the embedded loader disagree on the count"
      );
    }
  }

  /// Every config proxies through a GLAM program of its own environment, and the other
  /// environment's programs refuse the set by file name: a production set can never be
  /// served for a staging vault.
  @Test
  void eachEnvironmentHoldsToItsOwnGlamPrograms() {
    for (final var env : GlamEnv.values()) {
      final var own = EmbeddedMappings.glamPrograms(env.glamAccounts());
      for (final var config : EmbeddedMappings.load(env, own)) {
        final var proxy = config.invokedProxyProgram();
        assertNotNull(proxy, env + ": a config without a proxy program");
        assertTrue(own.contains(proxy.publicKey()), env + ": " + proxy.publicKey().toBase58() + " is not a GLAM program of this environment");
      }
      final var other = env == GlamEnv.PRODUCTION ? GlamEnv.STAGING : GlamEnv.PRODUCTION;
      final var refused = assertThrows(IllegalStateException.class,
          () -> EmbeddedMappings.load(env, EmbeddedMappings.glamPrograms(other.glamAccounts())));
      assertTrue(refused.getMessage().contains("is not a " + env + " GLAM program"), refused.getMessage());
      assertTrue(refused.getMessage().startsWith(EmbeddedMappings.RESOURCE_ROOT), refused.getMessage());
    }
  }

  /// A narrower allowance names the first file that steps outside it.
  @Test
  void aForeignProxyIsRefusedByName() {
    final var protocolOnly = Set.of(GlamAccounts.MAIN_NET.protocolProgram());
    final var refused = assertThrows(IllegalStateException.class,
        () -> EmbeddedMappings.load(GlamEnv.PRODUCTION, protocolOnly));
    assertTrue(refused.getMessage().contains(".json proxies through "), refused.getMessage());
  }

  @Test
  void createMapperWithoutAPathServesBothEnvironments() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var factory = DynamicGlamAccountFactory.createFactory(glamAccounts.integrationAuthorities(), 8);
      final var mapper = glamAccounts.createMapper(factory);
      assertEquals(glamAccounts.protocolProgram(), mapper.invokedProxyProgram());
      assertNotNull(mapper.programProxy(SYSTEM_PROGRAM), env + ": no System program proxy");
      assertNotNull(mapper.programProxy(KAMINO_LENDING), env + ": no Kamino Lending proxy");
      final var viaProgram = GlamVaultAccounts.createMapper(glamAccounts.invokedProtocolProgram(), factory);
      assertEquals(mapper.invokedProxyProgram(), viaProgram.invokedProxyProgram());
      assertNotNull(viaProgram.programProxy(SYSTEM_PROGRAM));
    }
  }
  /// The index records the glam commit the sets were projected from as the tracked
  /// mapping/SOURCE states it; a build never consults git history for it.
  @Test
  void theIndexRecordsTheSourceTheTrackedFileStates() throws IOException {
    final var stated = Files.readString(Path.of("..", "mapping", "SOURCE")).trim();
    assertTrue(stated.matches("glamsystems/glam@[0-9a-f]{40}"), stated);
    assertEquals(stated, EmbeddedMappings.index().source());
  }

  /// A protocol program the sdk does not know is refused, not served staging's set.
  @Test
  void anUnknownProtocolProgramIsRefused() {
    final var custom = AccountMeta.createInvoked(fromBase58Encoded("So11111111111111111111111111111111111111112"));
    final var factory = DynamicGlamAccountFactory.createFactory(GlamAccounts.MAIN_NET.integrationAuthorities(), 8);
    final var refused = assertThrows(IllegalArgumentException.class, () -> GlamVaultAccounts.createMapper(custom, factory));
    assertTrue(refused.getMessage().contains(custom.publicKey().toBase58() + " is neither GLAM protocol program"), refused.getMessage());
    final var accounts = GlamAccountsBuilder.builder()
        .protocolProgram(custom.publicKey())
        .configProgram(GlamAccounts.MAIN_NET.configProgram())
        .mintProgram(GlamAccounts.MAIN_NET.mintProgram())
        .policyProgram(GlamAccounts.MAIN_NET.policyProgram())
        .create();
    assertThrows(IllegalArgumentException.class, () -> accounts.createMapper(factory));
    assertTrue(GlamEnv.ofProtocolProgram(custom.publicKey()).isEmpty());
  }
  private static byte[] utf8(final String text) {
    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /// The index names source, production and staging, every one: an index another writer
  /// produced without one of them cannot load half a set silently.
  @Test
  void theIndexParserRefusesAMissingField() {
    final var whole = "{\"source\": \"s\", \"production\": [\"a.json\"], \"staging\": [\"b.json\"]}";
    assertEquals(List.of("a.json"), EmbeddedMappings.parseIndex(utf8(whole)).production());
    for (final var missing : List.of("source", "production", "staging")) {
      final var json = whole.replaceFirst("\"" + missing + "\": [^,}]+,? ?", "").replace(", }", "}");
      final var refused = assertThrows(IllegalStateException.class, () -> EmbeddedMappings.parseIndex(utf8(json)), json);
      assertTrue(refused.getMessage().contains("must name source, production and staging"), refused.getMessage());
    }
  }

  @Test
  void theIndexParserRefusesAnUnknownField() {
    final var refused = assertThrows(IllegalStateException.class, () -> EmbeddedMappings.parseIndex(
        utf8("{\"source\": \"s\", \"production\": [], \"staging\": [], \"extra\": 1}")));
    assertTrue(refused.getMessage().contains("declares an unknown field extra"), refused.getMessage());
  }

  /// A file name is a plain `<program>.json`: no path, no other extension, no null. The loader
  /// joins it to the environment's directory, so anything else would read outside the set.
  @Test
  void theIndexParserRefusesANameThatIsNotAConfigFileName() {
    for (final var name : List.of("null", "\"x.txt\"", "\"a/b.json\"", "\"a\\\\b.json\"")) {
      final var json = "{\"source\": \"s\", \"production\": [" + name + "], \"staging\": []}";
      final var refused = assertThrows(IllegalStateException.class, () -> EmbeddedMappings.parseIndex(utf8(json)), json);
      assertTrue(refused.getMessage().contains("which is not a config file name"), refused.getMessage());
    }
  }

  @Test
  void aMissingResourceIsRefusedByName() {
    final var refused = assertThrows(IllegalStateException.class, () -> EmbeddedMappings.readResource("glam/ix-mappings/nope.json"));
    assertEquals("The sdk jar does not carry glam/ix-mappings/nope.json.", refused.getMessage());
  }

  /// The set's directory in the jar is the lowercase environment name the build writes;
  /// a case-insensitive filesystem would hide a wrong case until the jar is read on Linux.
  @Test
  void theSetDirectoryIsTheLowercaseEnvironmentName() {
    assertEquals("glam/ix-mappings/production/", EmbeddedMappings.directory(GlamEnv.PRODUCTION));
    assertEquals("glam/ix-mappings/staging/", EmbeddedMappings.directory(GlamEnv.STAGING));
  }

  /// An environment the index lists no file for is refused rather than served an empty mapper,
  /// and a config that names no proxy program is refused by its resource name.
  @Test
  void anEmptySetOrAConfigWithoutAProxyIsRefused() {
    final var own = EmbeddedMappings.glamPrograms(GlamAccounts.MAIN_NET);
    final var empty = new EmbeddedMappings.Index("s", List.of(), List.of("x.json"));
    final var none = assertThrows(IllegalStateException.class,
        () -> EmbeddedMappings.load(empty, GlamEnv.PRODUCTION, own, EmbeddedMappings::readResource));
    assertEquals("The sdk jar embeds no PRODUCTION mapping configs.", none.getMessage());
    final var index = new EmbeddedMappings.Index("s", List.of("x.json"), List.of());
    final var noProxy = assertThrows(IllegalStateException.class,
        () -> EmbeddedMappings.load(index, GlamEnv.PRODUCTION, own,
            resource -> utf8("{\"program_id\": \"11111111111111111111111111111111\", \"instructions\": []}")));
    assertEquals("glam/ix-mappings/production/x.json names no proxy program.", noProxy.getMessage());
  }
}

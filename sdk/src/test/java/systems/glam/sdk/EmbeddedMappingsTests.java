package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    return Path.of("..").resolve(env == GlamEnv.PRODUCTION ? "mapping-configs-v1" : "mapping-configs-v1-staging");
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
}

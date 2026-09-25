package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.MappingDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// The documents the jar embeds are the bytes the download holds, indexed whole, held to
/// their environment, and served by createMapper with no directory.
final class EmbeddedMappingsTests {

  private static final PublicKey SYSTEM_PROGRAM = fromBase58Encoded("11111111111111111111111111111111");

  /// The set the build embedded from: the package layout under the tracked `ix-mapper-ts/`
  /// directory, or the root `-PglamMappingsDir` pointed the build at (the test runs with the
  /// module directory as its working directory).
  private static Path repositorySet(final GlamEnv env) {
    final var root = Path.of(System.getProperty("glam.mappings.dir", "../ix-mapper-ts"));
    return root.resolve("src").resolve("generated").resolve("mapping").resolve(EmbeddedMappings.environmentName(env));
  }

  private static byte[] utf8(final String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void theIndexNamesEveryEmbeddedFileAndEachOneParses() {
    final var index = EmbeddedMappings.index();
    assertFalse(index.production().isEmpty(), "no production documents indexed");
    assertFalse(index.staging().isEmpty(), "no staging documents indexed");
    assertFalse(index.source().isBlank());
    for (final var env : GlamEnv.values()) {
      final var documents = EmbeddedMappings.load(env);
      assertEquals(index.files(env).size(), documents.size(), env + ": one document per indexed file");
      for (final var document : documents) {
        assertFalse(document.instructions().isEmpty(), env + ": a document with no instructions");
        assertEquals(EmbeddedMappings.environmentName(env), document.environment());
      }
    }
  }

  /// The embedded set is the download's set, byte for byte and file for file, in both
  /// environments: the index names exactly the download's files and each resource holds its
  /// file's bytes.
  @Test
  void theEmbeddedSetEqualsTheDownloadedSet() throws IOException {
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
      assertEquals(repositoryFiles, new TreeSet<>(index.files(env)), env + ": the index does not name the download's files");
      for (final var name : repositoryFiles) {
        assertArrayEquals(
            Files.readAllBytes(directory.resolve(name)),
            EmbeddedMappings.readResource(EmbeddedMappings.directory(env) + name),
            env + ": " + name + " in the jar differs from the downloaded file"
        );
      }
      assertEquals(
          env.glamAccounts().createMapper(directory).documents().size(),
          EmbeddedMappings.load(env).size(),
          env + ": the directory loader and the embedded loader disagree on the count"
      );
    }
  }

  /// Every document proxies through a GLAM program of its own environment, and the other
  /// environment's programs refuse the set by file name: a production set can never be
  /// served for a staging vault.
  @Test
  void eachEnvironmentHoldsToItsOwnGlamPrograms() {
    for (final var env : GlamEnv.values()) {
      final var own = EmbeddedMappings.glamPrograms(env.glamAccounts());
      for (final var document : EmbeddedMappings.load(env, own)) {
        assertTrue(own.contains(document.proxyProgramId()),
            env + ": " + document.proxyProgramId().toBase58() + " is not a GLAM program of this environment");
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

  /// A document filed under one environment's directory that declares another is refused
  /// before its proxy program is looked at: the set's directory and its documents agree.
  @Test
  void aDocumentOfAnotherEnvironmentIsRefusedByName() {
    final var index = new EmbeddedMappings.Index("s", List.of("x.json"), List.of());
    final var staging = """
        {"schema_version": 1, "environment": "staging",
         "program_id": "11111111111111111111111111111111",
         "proxy_program_id": "GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz",
         "instructions": []}""";
    final var refused = assertThrows(IllegalStateException.class,
        () -> EmbeddedMappings.load(index, GlamEnv.PRODUCTION, EmbeddedMappings.glamPrograms(GlamAccounts.MAIN_NET), resource -> utf8(staging)));
    assertEquals("glam/ix-mappings/production/x.json declares environment staging, not production.", refused.getMessage());
  }

  /// createMapper with no directory serves the embedded set of the accounts' deployment:
  /// production for MAIN_NET, staging for MAIN_NET_STAGING, each a mapper over every
  /// embedded document and nothing else.
  @Test
  void createMapperServesTheEmbeddedSetOfTheDeployment() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var mapper = glamAccounts.createMapper();
      assertEquals(EmbeddedMappings.environmentName(env), mapper.environment());
      assertEquals(EmbeddedMappings.index().files(env).size(), mapper.documents().size(), env + ": one document per embedded file");
      final var system = mapper.documentOf(SYSTEM_PROGRAM);
      assertNotNull(system, env + ": no System program document");
      assertEquals(glamAccounts.protocolProgram(), system.proxyProgramId(), env + ": the System program is proxied by the protocol program");
      final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts,
          fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs"),
          fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT"));
      assertEquals(mapper.documents(), vaultAccounts.createMapper().documents(), env + ": the vault serves the same set");
    }
  }

  /// A protocol program the sdk does not know is refused, not served staging's set.
  @Test
  void anUnknownProtocolProgramIsRefused() {
    final var custom = fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var accounts = GlamAccountsBuilder.builder()
        .protocolProgram(custom)
        .configProgram(GlamAccounts.MAIN_NET.configProgram())
        .mintProgram(GlamAccounts.MAIN_NET.mintProgram())
        .policyProgram(GlamAccounts.MAIN_NET.policyProgram())
        .create();
    final var refused = assertThrows(IllegalArgumentException.class, accounts::createMapper);
    assertTrue(refused.getMessage().contains(custom.toBase58() + " is neither GLAM protocol program"), refused.getMessage());
    assertTrue(GlamEnv.ofProtocolProgram(custom).isEmpty());
    assertEquals(GlamEnv.PRODUCTION, GlamEnv.ofProtocolProgram(GlamAccounts.MAIN_NET.protocolProgram()).orElseThrow());
    assertEquals(GlamEnv.STAGING, GlamEnv.ofProtocolProgram(GlamAccounts.MAIN_NET_STAGING.protocolProgram()).orElseThrow());
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
  void theIndexParserRefusesANameThatIsNotADocumentFileName() {
    for (final var name : List.of("null", "\"x.txt\"", "\"a/b.json\"", "\"a\\\\b.json\"")) {
      final var json = "{\"source\": \"s\", \"production\": [" + name + "], \"staging\": []}";
      final var refused = assertThrows(IllegalStateException.class, () -> EmbeddedMappings.parseIndex(utf8(json)), json);
      assertTrue(refused.getMessage().contains("which is not a document file name"), refused.getMessage());
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

  /// An environment the index lists no file for is refused rather than served an empty
  /// mapper, and a document that does not admit is refused by its resource name.
  @Test
  void anEmptySetOrADocumentThatDoesNotAdmitIsRefused() {
    final var own = EmbeddedMappings.glamPrograms(GlamAccounts.MAIN_NET);
    final var empty = new EmbeddedMappings.Index("s", List.of(), List.of("x.json"));
    final var none = assertThrows(IllegalStateException.class,
        () -> EmbeddedMappings.load(empty, GlamEnv.PRODUCTION, own, EmbeddedMappings::readResource));
    assertEquals("The sdk jar embeds no PRODUCTION mapping documents.", none.getMessage());
    final var index = new EmbeddedMappings.Index("s", List.of("x.json"), List.of());
    final var refused = assertThrows(systems.glam.ix.proxy.MappingDocumentException.class,
        () -> EmbeddedMappings.load(index, GlamEnv.PRODUCTION, own,
            resource -> utf8("{\"program_id\": \"11111111111111111111111111111111\", \"instructions\": []}")));
    assertTrue(refused.getMessage().startsWith("glam/ix-mappings/production/x.json"), refused.getMessage());
  }

  /// The protocol program's authority lookup, as a mapping context uses it.
  @Test
  void theIntegrationAuthorityLookupFollowsTheAccounts() {
    final var accounts = GlamAccounts.MAIN_NET_STAGING;
    for (final var entry : accounts.integrationAuthorities().entrySet()) {
      assertEquals(entry.getValue().publicKey(), accounts.integrationAuthority(entry.getKey()));
    }
    assertNull(accounts.integrationAuthority(accounts.protocolProgram()), "the protocol program is not an integration");
    final AccountMeta kamino = accounts.readKaminoIntegrationAuthority();
    assertEquals(kamino.publicKey(), accounts.integrationAuthority(accounts.kaminoIntegrationProgram()));
    final MappingDocument document = accounts.createMapper().documentOf(fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD"));
    assertNotNull(document);
    assertEquals(accounts.kaminoIntegrationProgram(), document.proxyProgramId());
  }
}

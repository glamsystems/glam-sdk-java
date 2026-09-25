package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.spl.system.gen.SystemProgram;
import systems.glam.ix.proxy.MapResult;
import systems.glam.ix.proxy.MappingDocument;
import systems.glam.ix.proxy.MappingDocumentException;
import systems.glam.ix.proxy.MappingDocuments;
import systems.glam.ix.proxy.UnsupportedReason;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// The sdk's side of the ix-mapper: a mapper is built from one environment's documents and refused
/// another's, and a vault's context maps a native instruction to the GLAM proxy instruction the
/// generated protocol client lays out. The documents are the checked-in system-program fixtures
/// under src/test/resources/mapping (see its README for their provenance).
final class GlamAccountsMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey SYSTEM_PROGRAM = fromBase58Encoded("11111111111111111111111111111111");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;
  private static final String SYSTEM_DOCUMENT = "11111111111111111111111111111111.json";

  /// Copies the environment's system-program document into `directory`, the way the untracked
  /// download lays one environment out, and returns the copy.
  private static Path systemDocument(final String environment, final Path directory) {
    final var resource = "mapping/" + environment + '/' + SYSTEM_DOCUMENT;
    try (final var in = GlamAccountsMapperTests.class.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull(in, resource + " not found on classpath");
      final var file = directory.resolve(SYSTEM_DOCUMENT);
      Files.write(file, in.readAllBytes());
      return file;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<MappingDocument> documents(final String environment, final Path directory) {
    return List.of(MappingDocuments.read(systemDocument(environment, directory)));
  }

  @Test
  void eachDeploymentNamesItsMappingEnvironment() {
    assertEquals("production", GlamAccounts.MAIN_NET.mappingEnvironment());
    assertEquals("staging", GlamAccounts.MAIN_NET_STAGING.mappingEnvironment());
    assertEquals(GlamEnv.PRODUCTION.mappingEnvironment(), GlamAccounts.MAIN_NET.mappingEnvironment());
    assertEquals(GlamEnv.STAGING.mappingEnvironment(), GlamAccounts.MAIN_NET_STAGING.mappingEnvironment());
  }

  @Test
  void aMapperIsBuiltFromTheDocumentsOfItsOwnEnvironment(@TempDir final Path production,
                                                         @TempDir final Path staging) {
    // a file that is not a document, and a document of the other environment in a sub-directory,
    // are left alone: the directory read is flat and `.json` only
    try {
      Files.writeString(production.resolve("README.md"), "not a document");
      Files.createDirectory(production.resolve("staging"));
      systemDocument("staging", production.resolve("staging"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    systemDocument("production", production);
    systemDocument("staging", staging);

    final var productionMapper = GlamAccounts.MAIN_NET.createMapper(production);
    assertEquals("production", productionMapper.environment());
    assertEquals(1, productionMapper.documents().size());
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), productionMapper.documentOf(SYSTEM_PROGRAM).proxyProgramId());

    final var stagingMapper = GlamAccounts.MAIN_NET_STAGING.createMapper(staging);
    assertEquals("staging", stagingMapper.environment());
    assertEquals(GlamAccounts.MAIN_NET_STAGING.protocolProgram(), stagingMapper.documentOf(SYSTEM_PROGRAM).proxyProgramId());
  }

  @Test
  void theOtherEnvironmentsDocumentsAreRefused(@TempDir final Path production, @TempDir final Path staging) {
    final var stagingDocuments = documents("staging", staging);
    final var productionDocuments = documents("production", production);

    final var mainNet = assertThrows(IllegalArgumentException.class,
        () -> GlamAccounts.MAIN_NET.createMapper(stagingDocuments));
    assertTrue(mainNet.getMessage().contains("'staging'"), mainNet.getMessage());
    assertTrue(mainNet.getMessage().contains("'production'"), mainNet.getMessage());
    assertTrue(mainNet.getMessage().contains(GlamAccounts.MAIN_NET.protocolProgram().toBase58()), mainNet.getMessage());

    final var mainNetStaging = assertThrows(IllegalArgumentException.class,
        () -> GlamAccounts.MAIN_NET_STAGING.createMapper(productionDocuments));
    assertTrue(mainNetStaging.getMessage().contains("'production'"), mainNetStaging.getMessage());
    assertTrue(mainNetStaging.getMessage().contains("'staging'"), mainNetStaging.getMessage());

    // the directory overload applies the same guard
    assertThrows(IllegalArgumentException.class, () -> GlamAccounts.MAIN_NET.createMapper(staging));
    // and ix-proxy's own refusals come through untouched: nothing to map with
    assertThrows(MappingDocumentException.class, () -> GlamAccounts.MAIN_NET.createMapper(List.of()));
  }

  /// A SOL transfer out of the vault maps to the protocol's system_transfer exactly as the generated
  /// client lays it out, with the document's trailing Token program seat (the remaining account the
  /// handler takes when wrapping SOL) after the client's five accounts.
  @Test
  void aVaultTransferMapsToTheProtocolSystemTransfer(@TempDir final Path production) {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    final var mapper = GlamAccounts.MAIN_NET.createMapper(documents("production", production));
    final var to = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");
    final long lamports = 1_234_567L;

    final var transfer = SystemProgram.transferSol(SOLANA.invokedSystemProgram(), vaultAccounts.vaultPublicKey(), to, lamports);
    final var result = mapper.map(transfer, vaultAccounts.mappingContext());
    final var mapped = assertInstanceOf(MapResult.Mapped.class, result, result::toString);
    assertEquals(SYSTEM_PROGRAM, mapped.program());
    assertEquals("transfer", mapped.source());
    assertEquals("system_transfer", mapped.handler());

    final var expected = GlamProtocolProgram.systemTransfer(
        GlamAccounts.MAIN_NET.invokedProtocolProgram(),
        SOLANA,
        vaultAccounts.glamStateKey(),
        vaultAccounts.vaultPublicKey(),
        vaultAccounts.feePayer(),
        to,
        lamports
    );
    final var instruction = mapped.instruction();
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instruction.programId().publicKey());
    final var accounts = instruction.accounts();
    assertEquals(expected.accounts().size() + 1, accounts.size());
    assertEquals(expected.accounts(), accounts.subList(0, expected.accounts().size()));
    assertEquals(AccountMeta.createRead(SOLANA.tokenProgram()), accounts.getLast());
    assertArrayEquals(expected.copyData(), instruction.copyData());
  }

  @Test
  void aTransferFromAnotherSourceIsUnsupported(@TempDir final Path production) {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(FEE_PAYER, STATE_KEY);
    final var mapper = GlamAccounts.MAIN_NET.createMapper(documents("production", production));

    // the document expects the source to be the vault; the fee payer's own transfer is not proxied
    final var transfer = SystemProgram.transferSol(SOLANA.invokedSystemProgram(), FEE_PAYER, vaultAccounts.vaultPublicKey(), 1L);
    final var result = mapper.map(transfer, vaultAccounts.mappingContext());
    final var unsupported = assertInstanceOf(MapResult.Unsupported.class, result, result::toString);
    assertEquals(UnsupportedReason.ACCOUNT_EXPECTATION, unsupported.reason());
    assertEquals("transfer", unsupported.source());
  }
}

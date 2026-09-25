package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.system.gen.SystemProgram;
import systems.glam.ix.proxy.InstructionEntry;
import systems.glam.ix.proxy.MapResult;
import systems.glam.ix.proxy.UnsupportedReason;
import systems.glam.sdk.idl.programs.glam.cctp.gen.ExtCctpProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgeProgram;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// What a consumer sees with only the sdk on its module path: a mapper built from the
/// documents the jar embeds, with no directory to mount, mapping a native instruction onto
/// the GLAM proxy for both deployments with the vault's own context.
final class EmbeddedMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DESTINATION = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");
  private static final PublicKey KAMINO_LENDING = fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD");
  private static final PublicKey CCTP_TOKEN_MESSENGER = fromBase58Encoded("CCTPV2vPZJS2u2BBsUoscuikbYjnpFmbFsvVuJdgUMQe");
  private static final PublicKey PHOENIX = fromBase58Encoded("EtrnLzgbS7nMMy5fbD42kXiUzGg8XQzJ972Xtk1cjWih");
  // the token messenger's deposit_for_burn, as the documents carry it
  private static final byte[] NATIVE_DEPOSIT_FOR_BURN = {(byte) 215, 60, 61, 46, 114, 55, (byte) 128, (byte) 176};

  @Test
  void aSystemTransferFromTheVaultMapsOntoTheProtocolProxyInBothEnvironments() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE_KEY);
      final var mapper = vaultAccounts.createMapper();
      final var context = vaultAccounts.mappingContext();

      final var transfer = SystemProgram.transferSol(
          SolanaAccounts.MAIN_NET.invokedSystemProgram(),
          vaultAccounts.vaultPublicKey(),
          DESTINATION,
          1_000L
      );
      final var result = assertInstanceOf(MapResult.Mapped.class, mapper.map(transfer, context), env + ": not mapped");
      final var mapped = result.instruction();

      assertEquals("transfer", result.source());
      assertEquals("system_transfer", result.handler());
      assertEquals(glamAccounts.protocolProgram(), mapped.programId().publicKey(), env + ": not sent to the protocol program");
      assertTrue(GlamProtocolProgram.SYSTEM_TRANSFER_DISCRIMINATOR.equals(mapped.data(), 0), env + ": not the system_transfer discriminator");
      // the lamports follow the swapped discriminator, byte for byte
      final var payload = new byte[8];
      System.arraycopy(mapped.data(), GlamProtocolProgram.SYSTEM_TRANSFER_DISCRIMINATOR.length(), payload, 0, 8);
      assertArrayEquals(new byte[]{(byte) 0xE8, 3, 0, 0, 0, 0, 0, 0}, payload, env + ": lamports");
      assertEquals(
          List.of(
              AccountMeta.createRead(STATE_KEY),
              AccountMeta.createWrite(vaultAccounts.vaultPublicKey()),
              AccountMeta.createWritableSigner(FEE_PAYER),
              AccountMeta.createRead(SolanaAccounts.MAIN_NET.systemProgram()),
              AccountMeta.createWrite(DESTINATION),
              AccountMeta.createRead(SolanaAccounts.MAIN_NET.tokenProgram())
          ),
          mapped.accounts(),
          env + ": glam_state, glam_vault, glam_signer, the system program, the destination, the token program"
      );

      // the vault must be the source: another owner's transfer is refused, not redirected
      final var foreign = SystemProgram.transferSol(SolanaAccounts.MAIN_NET.invokedSystemProgram(), FEE_PAYER, DESTINATION, 1L);
      final var refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(foreign, context));
      assertEquals(UnsupportedReason.ACCOUNT_EXPECTATION, refused.reason());
      assertEquals("transfer account 0 (source) must be glam_vault", refused.message());
    }
  }

  @Test
  void theKaminoDocumentsAreServedForBothEnvironments() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var document = glamAccounts.createMapper().documentOf(KAMINO_LENDING);
      assertNotNull(document, env + ": no Kamino Lending document");
      assertEquals(KAMINO_LENDING, document.programId(), env + ": the document is keyed by the native program");
      assertEquals(glamAccounts.kaminoIntegrationProgram(), document.proxyProgramId(), env + ": proxied by ext_kamino");
    }
  }

  /// CCTP's token messenger is proxied by the program each deployment carries the bridge path
  /// in: ext_cctp in production, ext_bridge on staging, where the native deposit_for_burn maps
  /// onto cctp_deposit_for_burn and not onto the managed handler.
  @Test
  void theCctpTokenMessengerIsProxiedByEachDeploymentsBridgeProgram() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var production = env == GlamEnv.PRODUCTION;
      final var document = glamAccounts.createMapper().documentOf(CCTP_TOKEN_MESSENGER);
      assertNotNull(document, env + ": no CCTP token messenger document");
      assertEquals(
          production ? glamAccounts.cctpIntegrationProgram() : glamAccounts.bridgeIntegrationProgram(),
          document.proxyProgramId(),
          env + ": proxied by the wrong GLAM program"
      );
      final var entry = assertInstanceOf(InstructionEntry.Mapped.class,
          document.entryFor(NATIVE_DEPOSIT_FOR_BURN, 0, NATIVE_DEPOSIT_FOR_BURN.length), env + ": deposit_for_burn is not mapped");
      assertEquals(
          production ? ExtCctpProgram.DEPOSIT_FOR_BURN_DISCRIMINATOR : ExtBridgeProgram.CCTP_DEPOSIT_FOR_BURN_DISCRIMINATOR,
          entry.handler().discriminator(),
          env + ": deposit_for_burn maps onto the wrong handler"
      );
    }
  }

  /// ext_phoenix takes the native accounts as remaining accounts after its own seven and
  /// requires the trader wallet to be the vault. The vault PDA cannot sign a transaction, so
  /// the mapper seats it at that position without the signer bit and forwards the rest in
  /// place; the fee payer is the only signer left. (Review finding on glamsystems/glam#1393:
  /// an earlier index map forwarded the trader with its signer bit, an unsignable transaction.)
  @Test
  void aPhoenixDepositSeatsTheVaultAsTheTraderWithoutASignerBit() {
    final var glamAccounts = GlamEnv.STAGING.glamAccounts();
    final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE_KEY);
    final var vault = vaultAccounts.vaultPublicKey();
    final var mapper = vaultAccounts.createMapper();
    final var other = fromBase58Encoded("So11111111111111111111111111111111111111112");
    // native deposit_funds: phoenix program, log authority, market, trader (signer), base
    // account, quote account, base vault, quote vault, base token program, quote token program;
    // the client builds it with the vault as the trader
    final var deposit = Instruction.createInstruction(
        PHOENIX,
        List.of(
            AccountMeta.createRead(PHOENIX),
            AccountMeta.createRead(other),
            AccountMeta.createWrite(other),
            AccountMeta.createReadOnlySigner(vault),
            AccountMeta.createWrite(other),
            AccountMeta.createWrite(other),
            AccountMeta.createWrite(other),
            AccountMeta.createWrite(other),
            AccountMeta.createRead(SolanaAccounts.MAIN_NET.tokenProgram()),
            AccountMeta.createRead(SolanaAccounts.MAIN_NET.tokenProgram())
        ),
        new byte[]{(byte) 202, 39, 52, (byte) 211, 53, 20, (byte) 250, 88, 1, 0, 0, 0, 0, 0, 0, 0}
    );
    final var result = assertInstanceOf(MapResult.Mapped.class, mapper.map(deposit, vaultAccounts.mappingContext()));
    final var mapped = result.instruction();

    assertEquals(glamAccounts.phoenixIntegrationProgram(), mapped.programId().publicKey());
    final var seats = mapped.accounts();
    assertEquals(17, seats.size(), "seven extension accounts then the ten native ones");
    assertEquals(STATE_KEY, seats.get(0).publicKey());
    assertEquals(vault, seats.get(1).publicKey());
    assertEquals(FEE_PAYER, seats.get(2).publicKey());
    assertEquals(glamAccounts.readPhoenixIntegrationAuthority().publicKey(), seats.get(3).publicKey(), "the integration authority comes from the context");
    assertEquals(PHOENIX, seats.get(4).publicKey(), "the native program is seated for the CPI");
    assertEquals(PHOENIX, seats.get(7).publicKey(), "the native accounts forward in place");
    assertEquals(vault, seats.get(10).publicKey(), "the trader wallet position carries the vault");
    assertFalse(seats.get(10).signer(), "the vault PDA does not sign; the program signs for it");
    assertEquals(SolanaAccounts.MAIN_NET.tokenProgram(), seats.get(15).publicKey());
    final var signers = new ArrayList<Integer>();
    for (int i = 0; i < seats.size(); ++i) {
      if (seats.get(i).signer()) {
        signers.add(i);
      }
    }
    assertEquals(List.of(2), signers, "the fee payer is the only signer");
  }
}

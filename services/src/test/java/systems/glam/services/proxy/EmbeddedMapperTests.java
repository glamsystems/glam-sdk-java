package systems.glam.services.proxy;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.system.gen.SystemProgram;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.cctp.gen.ExtCctpProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgeProgram;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.programs.Discriminator.toDiscriminator;

/// What a service sees with only the sdk on its module path: a mapper built from the configs
/// the jar embeds, with no directory to mount, mapping a native instruction onto the GLAM
/// proxy for both deployments.
final class EmbeddedMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DESTINATION = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");
  private static final PublicKey KAMINO_LENDING = fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD");
  private static final PublicKey CCTP_TOKEN_MESSENGER = fromBase58Encoded("CCTPV2vPZJS2u2BBsUoscuikbYjnpFmbFsvVuJdgUMQe");
  // the token messenger's deposit_for_burn, as the configs carry it
  private static final Discriminator NATIVE_DEPOSIT_FOR_BURN = toDiscriminator(215, 60, 61, 46, 114, 55, 128, 176);

  @Test
  void aSystemTransferFromTheVaultMapsOntoTheProtocolProxyInBothEnvironments() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE_KEY);
      final var mapper = glamAccounts.createMapper(
          DynamicGlamAccountFactory.createFactory(glamAccounts.integrationAuthorities(), 8)
      );

      final var transfer = SystemProgram.transferSol(
          SolanaAccounts.MAIN_NET.invokedSystemProgram(),
          vaultAccounts.vaultPublicKey(),
          DESTINATION,
          1_000L
      );
      final var mapped = mapper.mapInstruction(AccountMeta.createWritableSigner(FEE_PAYER), vaultAccounts, transfer);

      assertEquals(glamAccounts.protocolProgram(), mapped.programId().publicKey(), env + ": not sent to the protocol program");
      assertTrue(GlamProtocolProgram.SYSTEM_TRANSFER_DISCRIMINATOR.equals(mapped.data(), 0), env + ": not the system_transfer discriminator");
      final var accounts = mapped.accounts();
      assertEquals(STATE_KEY, accounts.get(0).publicKey(), env + ": glam_state");
      assertEquals(vaultAccounts.vaultPublicKey(), accounts.get(1).publicKey(), env + ": glam_vault");
      assertEquals(FEE_PAYER, accounts.get(2).publicKey(), env + ": glam_signer");
      assertTrue(accounts.get(2).signer(), env + ": glam_signer signs");
      assertEquals(SolanaAccounts.MAIN_NET.systemProgram(), accounts.get(3).publicKey(), env + ": system program");
      assertEquals(DESTINATION, accounts.get(4).publicKey(), env + ": the native destination crosses");
      assertEquals(5, accounts.size(), env + ": account count");
    }
  }

  @Test
  void theKaminoProxiesAreServedForBothEnvironments() {
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var mapper = glamAccounts.createMapper(
          DynamicGlamAccountFactory.createFactory(glamAccounts.integrationAuthorities(), 8)
      );
      final var proxy = mapper.programProxy(KAMINO_LENDING);
      assertNotNull(proxy, env + ": no Kamino Lending proxy");
      assertEquals(KAMINO_LENDING, proxy.cpiProgram(), env + ": the proxy is keyed by the native program");
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
      final var config = glamAccounts.embeddedMappingConfigs().stream()
          .filter(candidate -> candidate.programs().stream().anyMatch(program -> program.publicKey().equals(CCTP_TOKEN_MESSENGER)))
          .findFirst()
          .orElseThrow(() -> new AssertionError(env + ": no CCTP token messenger config"));
      assertEquals(
          production ? glamAccounts.cctpIntegrationProgram() : glamAccounts.bridgeIntegrationProgram(),
          config.invokedProxyProgram().publicKey(),
          env + ": proxied by the wrong GLAM program"
      );
      final var mapper = glamAccounts.createMapper(
          DynamicGlamAccountFactory.createFactory(glamAccounts.integrationAuthorities(), 8)
      );
      final var proxy = mapper.programProxy(CCTP_TOKEN_MESSENGER);
      assertNotNull(proxy, env + ": no CCTP token messenger proxy");
      assertEquals(
          production ? ExtCctpProgram.DEPOSIT_FOR_BURN_DISCRIMINATOR : ExtBridgeProgram.CCTP_DEPOSIT_FOR_BURN_DISCRIMINATOR,
          proxy.lookupProxyOrThrow(NATIVE_DEPOSIT_FOR_BURN).proxyDiscriminator(),
          env + ": deposit_for_burn maps onto the wrong handler"
      );
    }
  }
  private static final PublicKey PHOENIX = fromBase58Encoded("EtrnLzgbS7nMMy5fbD42kXiUzGg8XQzJ972Xtk1cjWih");

  /// ext_phoenix takes the native accounts as remaining accounts after its own seven and
  /// requires the trader wallet to be the vault. The vault PDA cannot sign a transaction, so the
  /// mapper seats it at that position without the signer bit and forwards the rest in place; the
  /// fee payer is the only signer left. (Review finding on glamsystems/glam#1393: the earlier
  /// empty index map forwarded the trader with its signer bit, an unsignable transaction.)
  @Test
  void aPhoenixDepositSeatsTheVaultAsTheTraderWithoutASignerBit() {
    final var glamAccounts = GlamEnv.STAGING.glamAccounts();
    final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE_KEY);
    final var vault = vaultAccounts.vaultPublicKey();
    final var mapper = glamAccounts.createMapper(
        DynamicGlamAccountFactory.createFactory(glamAccounts.integrationAuthorities(), 8)
    );
    final var other = fromBase58Encoded("So11111111111111111111111111111111111111112");
    // native deposit_funds: phoenix program, log authority, global configuration, trader wallet
    // (signer), trader token account, trader account, global vault, token program, global trader
    // index, active trader buffer; the client builds it with the vault as the trader
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
            AccountMeta.createRead(SolanaAccounts.MAIN_NET.tokenProgram()),
            AccountMeta.createWrite(other),
            AccountMeta.createWrite(other)
        ),
        new byte[]{(byte) 202, 39, 52, (byte) 211, 53, 20, (byte) 250, 88, 1, 0, 0, 0, 0, 0, 0, 0}
    );
    final var mapped = mapper.mapInstruction(AccountMeta.createWritableSigner(FEE_PAYER), vaultAccounts, deposit);

    assertEquals(glamAccounts.phoenixIntegrationProgram(), mapped.programId().publicKey());
    final var seats = mapped.accounts();
    assertEquals(17, seats.size(), "seven extension accounts then the ten native ones");
    assertEquals(vault, seats.get(10).publicKey(), "the trader wallet position carries the vault");
    assertFalse(seats.get(10).signer(), "the vault PDA does not sign; the program signs for it");
    assertEquals(PHOENIX, seats.get(7).publicKey(), "the native accounts forward in place");
    assertEquals(SolanaAccounts.MAIN_NET.tokenProgram(), seats.get(14).publicKey());
    final var signers = new java.util.ArrayList<Integer>();
    for (int i = 0; i < seats.size(); ++i) {
      if (seats.get(i).signer()) {
        signers.add(i);
      }
    }
    assertEquals(List.of(2), signers, "the fee payer is the only signer");
  }
}

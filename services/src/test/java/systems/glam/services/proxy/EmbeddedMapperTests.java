package systems.glam.services.proxy;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.spl.system.gen.SystemProgram;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.proxy.DynamicGlamAccountFactory;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// What a service sees with only the sdk on its module path: a mapper built from the configs
/// the jar embeds, with no directory to mount, mapping a native instruction onto the GLAM
/// proxy for both deployments.
final class EmbeddedMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DESTINATION = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");
  private static final PublicKey KAMINO_LENDING = fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD");

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
}

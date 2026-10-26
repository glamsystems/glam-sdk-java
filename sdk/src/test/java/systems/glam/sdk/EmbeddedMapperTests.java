package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.system.gen.SystemProgram;
import software.sava.idl.clients.spl.token.gen.TokenProgram;
import software.sava.idl.clients.spl.token_2022.gen.Token2022Program;
import systems.glam.ix.proxy.InstructionEntry;
import systems.glam.ix.proxy.MapResult;
import systems.glam.ix.proxy.UnsupportedReason;
import systems.glam.sdk.idl.programs.glam.cctp.gen.ExtCctpProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgePDAs;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgeProgram;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types.CctpDepositForBurnArgs;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types.DepositForBurnParams;
import systems.glam.sdk.mapping.GlamSuppliedAccounts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static systems.glam.sdk.tests.MappingFixtures.globalConfig;
import static systems.glam.sdk.tests.MappingFixtures.key;

/// What a consumer sees with only the sdk on its module path: a mapper built from the
/// documents the jar embeds, with no directory to mount, mapping a native instruction onto
/// the GLAM proxy for both deployments with the vault's own context.
final class EmbeddedMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DESTINATION = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");
  private static final PublicKey USDC = fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
  private static final PublicKey PYUSD = fromBase58Encoded("2b1kV6DkPAnxd5ixfnxCpjxmKwqjjaYmCZfHsFu24GXo");
  private static final PublicKey KAMINO_LENDING = fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD");
  private static final PublicKey CCTP_TOKEN_MESSENGER = fromBase58Encoded("CCTPV2vPZJS2u2BBsUoscuikbYjnpFmbFsvVuJdgUMQe");
  private static final PublicKey CCTP_MESSAGE_TRANSMITTER = fromBase58Encoded("CCTPV2Sm4AdWt5296sk4P66VBZ7bEhcARwFaaS9YPbeC");
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

  /// The SPL Token documents take the authority of transfer_checked and the owner of
  /// close_account as a signer the caller chooses, held to the vault. Either choice maps, for
  /// both token programs in both deployments, onto the instruction the GLAM client builds for
  /// the same transfer or close, where the vault PDA sits unsigned and the fee payer is the only
  /// signer. The choice never loosens the address: another authority is refused.
  @Test
  void aVaultTokenTransferOrCloseMapsWhetherOrNotTheCallerSignsForTheVault() {
    final var solana = SolanaAccounts.MAIN_NET;
    for (final var env : GlamEnv.values()) {
      final var vaultAccounts = GlamVaultAccounts.createAccounts(env.glamAccounts(), FEE_PAYER, STATE_KEY);
      final var vault = vaultAccounts.vaultPublicKey();
      final var mapper = vaultAccounts.createMapper();
      final var context = vaultAccounts.mappingContext();
      final var client = GlamAccountClient.createClient(solana, vaultAccounts);
      for (final boolean token2022 : new boolean[]{false, true}) {
        final var tokenProgram = token2022 ? solana.invokedToken2022Program() : solana.invokedTokenProgram();
        final var mint = token2022 ? PYUSD : USDC;
        final var tokenAccount = client.vaultTokenAccount(tokenProgram.publicKey(), mint).publicKey();
        final var transfer = client.transferTokenChecked(tokenProgram, tokenAccount, DESTINATION, 1_000L, 6, mint);
        final var close = client.closeTokenAccount(tokenProgram, tokenAccount);
        for (final boolean signed : new boolean[]{true, false}) {
          final var label = env + ", " + tokenProgram.publicKey() + ", vault " + (signed ? "signed" : "unsigned") + ": ";
          assertMapsOnto(transfer, mapper.map(transferChecked(token2022, tokenProgram, tokenAccount, mint, vault, signed), context), label + "transfer_checked");
          final var nativeClose = token2022
              ? Token2022Program.closeAccount(tokenProgram, tokenAccount, FEE_PAYER, vault, signed)
              : TokenProgram.closeAccount(tokenProgram, tokenAccount, FEE_PAYER, vault, signed);
          assertMapsOnto(close, mapper.map(nativeClose, context), label + "close_account");

          final var refused = assertInstanceOf(MapResult.Unsupported.class,
              mapper.map(transferChecked(token2022, tokenProgram, tokenAccount, mint, FEE_PAYER, signed), context), label + "another authority");
          assertEquals(UnsupportedReason.ACCOUNT_EXPECTATION, refused.reason(), label);
          assertEquals("transfer_checked account 3 (authority) must be glam_vault", refused.message(), label);
        }
      }
    }
  }

  private static Instruction transferChecked(final boolean token2022,
                                             final AccountMeta tokenProgram,
                                             final PublicKey tokenAccount,
                                             final PublicKey mint,
                                             final PublicKey authority,
                                             final boolean authorityIsSigner) {
    return token2022
        ? Token2022Program.transferChecked(tokenProgram, tokenAccount, mint, DESTINATION, authority, authorityIsSigner, 1_000L, 6)
        : TokenProgram.transferChecked(tokenProgram, tokenAccount, mint, DESTINATION, authority, authorityIsSigner, 1_000L, 6);
  }

  private static void assertMapsOnto(final Instruction expected, final MapResult result, final String message) {
    final var mapped = assertInstanceOf(MapResult.Mapped.class, result, () -> message + ": " + result).instruction();
    assertEquals(expected.programId(), mapped.programId(), message);
    assertEquals(expected.accounts(), mapped.accounts(), message);
    assertArrayEquals(
        Arrays.copyOfRange(expected.data(), expected.offset(), expected.offset() + expected.len()),
        Arrays.copyOfRange(mapped.data(), mapped.offset(), mapped.offset() + mapped.len()),
        message
    );
    assertEquals(
        List.of(FEE_PAYER),
        mapped.accounts().stream().filter(AccountMeta::signer).map(AccountMeta::publicKey).toList(),
        message + ": the fee payer is the only signer"
    );
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
  /// onto cctp_deposit_for_burn and not onto the managed handler. Mapped, the burn is the
  /// instruction the deployment's generated client builds for it: the GLAM seats, then the
  /// native accounts in the handler's order, the vault seated unsigned and the caller's
  /// event-data keypair still signing. On staging the handler also reads the vault's CCTP route
  /// set, which the document derives and the supplier answers for, so the plain context is
  /// refused rather than mapped one account short.
  @Test
  void theCctpTokenMessengerIsProxiedByEachDeploymentsBridgeProgram() {
    final var solana = SolanaAccounts.MAIN_NET;
    final var params = new DepositForBurnParams(1_000_000L, 6L, key(50), key(51), 2_500L, 1_000L);
    for (final var env : GlamEnv.values()) {
      final var glamAccounts = env.glamAccounts();
      final var production = env == GlamEnv.PRODUCTION;
      final var mapper = glamAccounts.createMapper();
      final var document = mapper.documentOf(CCTP_TOKEN_MESSENGER);
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

      final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE_KEY);
      final var vault = vaultAccounts.vaultPublicKey();
      final var deposit = depositForBurn(vault, params);
      final Instruction expected;
      final MapResult result;
      if (production) {
        expected = ExtCctpProgram.depositForBurn(
            glamAccounts.invokedCctpIntegrationProgram(), solana, STATE_KEY, vault, FEE_PAYER,
            glamAccounts.readCctpIntegrationAuthority().publicKey(), CCTP_TOKEN_MESSENGER, glamAccounts.protocolProgram(),
            key(40), key(41), key(42), key(43), key(44), key(45), key(46), key(47), key(48), key(49),
            CCTP_MESSAGE_TRANSMITTER, CCTP_TOKEN_MESSENGER, solana.tokenProgram(), key(52),
            new systems.glam.sdk.idl.programs.glam.cctp.gen.types.DepositForBurnParams(
                params.amount(), params.destinationDomain(), params.mintRecipient(), params.destinationCaller(),
                params.maxFee(), params.minFinalityThreshold()
            )
        );
        result = mapper.map(deposit, vaultAccounts.mappingContext());
      } else {
        expected = ExtBridgeProgram.cctpDepositForBurn(
            glamAccounts.invokedBridgeIntegrationProgram(), solana, STATE_KEY, vault, FEE_PAYER,
            glamAccounts.readBridgeIntegrationAuthority().publicKey(), CCTP_TOKEN_MESSENGER, glamAccounts.protocolProgram(),
            ExtBridgePDAs.bridgeRoutes1PDA(glamAccounts.bridgeIntegrationProgram(), STATE_KEY).publicKey(),
            key(40), key(41), key(42), key(43), key(44), key(45), key(46), key(47), key(48), key(49),
            CCTP_MESSAGE_TRANSMITTER, CCTP_TOKEN_MESSENGER, solana.tokenProgram(), key(52),
            new CctpDepositForBurnArgs(params)
        );
        final var refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(deposit, vaultAccounts.mappingContext()),
            "staging: the route set needs a supplier");
        assertEquals(UnsupportedReason.CONTEXT, refused.reason(), "staging: refused for want of the supplier");
        result = mapper.map(deposit, vaultAccounts.mappingContext(GlamSuppliedAccounts.create(globalConfig())));
      }
      final var mapped = assertInstanceOf(MapResult.Mapped.class, result, () -> env + ": " + result).instruction();
      assertEquals(expected.programId(), mapped.programId(), env + ": the deployment's bridge program");
      assertEquals(expected.accounts(), mapped.accounts(), env + ": the accounts the generated client seats");
      assertArrayEquals(
          Arrays.copyOfRange(expected.data(), expected.offset(), expected.offset() + expected.len()),
          Arrays.copyOfRange(mapped.data(), mapped.offset(), mapped.offset() + mapped.len()),
          env + ": the handler's discriminator, then the burn's params"
      );
      assertEquals(
          List.of(FEE_PAYER, key(49)),
          mapped.accounts().stream().filter(AccountMeta::signer).map(AccountMeta::publicKey).toList(),
          env + ": the fee payer and the event-data keypair sign; the vault does not"
      );
    }
  }

  /// The token messenger's deposit_for_burn as the documents list its accounts: the vault owns the
  /// burn account and signs for it, the fee payer pays the event's rent, and the CCTP accounts of
  /// the transfer follow, each distinguishable by key, with the programs the document expects.
  private static Instruction depositForBurn(final PublicKey vault, final DepositForBurnParams params) {
    final var solana = SolanaAccounts.MAIN_NET;
    final byte[] data = new byte[NATIVE_DEPOSIT_FOR_BURN.length + DepositForBurnParams.BYTES];
    System.arraycopy(NATIVE_DEPOSIT_FOR_BURN, 0, data, 0, NATIVE_DEPOSIT_FOR_BURN.length);
    params.write(data, NATIVE_DEPOSIT_FOR_BURN.length);
    return Instruction.createInstruction(
        CCTP_TOKEN_MESSENGER,
        List.of(
            AccountMeta.createReadOnlySigner(vault),          // owner
            AccountMeta.createWritableSigner(FEE_PAYER),      // event_rent_payer
            AccountMeta.createRead(key(40)),                  // sender_authority_pda
            AccountMeta.createWrite(key(41)),                 // burn_token_account
            AccountMeta.createRead(key(42)),                  // denylist_account
            AccountMeta.createWrite(key(43)),                 // message_transmitter
            AccountMeta.createRead(key(44)),                  // token_messenger
            AccountMeta.createRead(key(45)),                  // remote_token_messenger
            AccountMeta.createRead(key(46)),                  // token_minter
            AccountMeta.createWrite(key(47)),                 // local_token
            AccountMeta.createWrite(key(48)),                 // burn_token_mint
            AccountMeta.createWritableSigner(key(49)),        // message_sent_event_data
            AccountMeta.createRead(CCTP_MESSAGE_TRANSMITTER), // message_transmitter_program
            AccountMeta.createRead(CCTP_TOKEN_MESSENGER),     // token_messenger_minter_program
            AccountMeta.createRead(solana.tokenProgram()),
            AccountMeta.createRead(solana.systemProgram()),
            AccountMeta.createRead(key(52)),                  // event_authority
            AccountMeta.createRead(CCTP_TOKEN_MESSENGER)      // program
        ),
        data
    );
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

package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GlamJupiterProgramClientImpl implements GlamJupiterProgramClient {

  private final GlamAccountClient glamAccountClient;
  private final SolanaAccounts solanaAccounts;
  private final GlamVaultAccounts glamVaultAccounts;
  private final AccountMeta invokedProgram;
  private final PublicKey glamGlobalConfigAccount;
  private final AccountMeta feePayer;
  private final JupiterAccounts jupiterAccounts;
  private final PublicKey swapProgram;
  private final KaminoAccounts kaminoAccounts;
  private final AccountMeta readTokenProgram;

  GlamJupiterProgramClientImpl(final GlamAccountClient glamAccountClient,
                               final JupiterAccounts jupiterAccounts,
                               final KaminoAccounts kaminoAccounts) {
    this.glamAccountClient = glamAccountClient;
    this.solanaAccounts = glamAccountClient.solanaAccounts();
    this.glamVaultAccounts = glamAccountClient.vaultAccounts();
    this.feePayer = glamAccountClient.feePayer();
    final var glamAccounts = glamAccountClient.glamAccounts();
    this.invokedProgram = glamAccounts.invokedProtocolProgram();
    this.glamGlobalConfigAccount = glamAccounts.globalConfigPDA().publicKey();
    this.jupiterAccounts = jupiterAccounts;
    this.swapProgram = jupiterAccounts.swapProgram();
    this.kaminoAccounts = kaminoAccounts;
    this.readTokenProgram = solanaAccounts.readTokenProgram();
  }

  @Override
  public KaminoAccounts kaminoAccounts() {
    return kaminoAccounts;
  }

  /// Funds the vault's wSOL token account through the GLAM system transfer, which requires the Token
  /// program as its one remaining account when the destination is a wSOL token account, so that the
  /// program can sync the account's balance; without it the transfer fails with MissingAccount.
  private Instruction fundWrappedSol(final PublicKey wrappedSolTokenAccount, final long lamports) {
    return glamAccountClient.transferSolLamports(wrappedSolTokenAccount, lamports).extraAccount(readTokenProgram);
  }

  /// One refresh over the context's reserves that price a role, in role order (input, output, SOL/USD;
  /// a reserve pricing two roles is listed twice here and refreshed once by the batch), or null when
  /// none does.
  private Instruction kaminoReserveRefresh(final JupiterSwapContext swapContext) {
    final var priced = new ArrayList<KaminoReserveRefresh>();
    for (final var role : new PublicKey[]{
        swapContext.inputTokenOracleKey(), swapContext.outputTokenOracleKey(), swapContext.solUsdOracleKey()}) {
      if (role != null) {
        for (final var refresh : swapContext.kaminoReserves()) {
          if (role.equals(refresh.reserve())) {
            priced.add(refresh);
          }
        }
      }
    }
    return priced.isEmpty()
        ? null
        : KaminoReserveRefresh.refreshInstruction(kaminoAccounts.invokedKLendProgram(), priced);
  }

  private static List<Instruction> withRefresh(final Instruction refresh, final List<Instruction> instructions) {
    if (refresh == null) {
      return instructions;
    }
    final var withRefresh = new ArrayList<Instruction>();
    withRefresh.add(refresh);
    withRefresh.addAll(instructions);
    return List.copyOf(withRefresh);
  }

  @Override
  public SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public GlamVaultAccounts glamVaultAccounts() {
    return glamVaultAccounts;
  }

  @Override
  public JupiterAccounts jupiterAccounts() {
    return jupiterAccounts;
  }

  /// The GLAM CPI around the route. The global configuration and the caller's oracles ride along whether
  /// or not a price-check skip is requested: the program reads the configuration to decide a limited
  /// skip, and prices the swap when it declines one, so leaving them out fails such a swap with
  /// MissingAccount rather than settling it.
  private Instruction jupiterSwapV2(final JupiterSwapContext swapContext) {
    final var swapInstruction = swapContext.swapInstruction();
    final var fixedAccounts = GlamJupiterProgramClient.fixCPICallerRights(
        swapInstruction.accounts(), glamVaultAccounts.vaultPublicKey()
    );
    return GlamProtocolProgram.jupiterSwapV2(
        invokedProgram,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        swapProgram,
        swapContext.inputProgramStateKey(), swapContext.outputProgramStateKey(),
        glamGlobalConfigAccount,
        swapContext.solUsdOracleKey(),
        swapContext.inputTokenOracleKey(),
        swapContext.outputTokenOracleKey(),
        swapContext.skipQuotePriceCheck(),
        swapInstruction.data()
    ).extraAccounts(fixedAccounts);
  }

  @Override
  public Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final JupiterSwapContext swapContext) {
    return createSwapTokenAccountsIdempotent(
        swapContext.inputTokenProgram(), swapContext.inputMintKey(),
        swapContext.outputTokenProgram(), swapContext.outputMintKey()
    );
  }

  private List<Instruction> swapChecked(final JupiterSwapContext swapContext) {
    final var inputMintKey = swapContext.inputMintKey();
    final var inputTokenProgram = swapContext.inputTokenProgram();
    final var outputMintKey = swapContext.outputMintKey();
    final var outputTokenProgram = swapContext.outputTokenProgram();
    final var inputVaultATA = glamAccountClient.findATA(inputTokenProgram, inputMintKey).publicKey();
    final var outputVaultATA = glamAccountClient.findATA(outputTokenProgram, outputMintKey).publicKey();
    final var createVaultOutputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
        true, outputVaultATA, outputMintKey, outputTokenProgram
    );
    final var glamJupiterSwap = jupiterSwapV2(swapContext);
    final var refresh = kaminoReserveRefresh(swapContext);
    if (swapContext.wrapSOL() && inputMintKey.equals(solanaAccounts.wrappedSolTokenMint())) {
      return withRefresh(refresh, List.of(
          glamAccountClient.createATAForOwnerFundedByFeePayer(
              true, inputVaultATA, inputMintKey, inputTokenProgram
          ),
          fundWrappedSol(inputVaultATA, swapContext.amount()),
          glamAccountClient.syncNative(),
          createVaultOutputATA,
          glamJupiterSwap
      ));
    } else {
      return withRefresh(refresh, List.of(createVaultOutputATA, glamJupiterSwap));
    }
  }

  private List<Instruction> swapUnchecked(final JupiterSwapContext swapContext) {
    final var glamJupiterSwap = jupiterSwapV2(swapContext);
    final var refresh = kaminoReserveRefresh(swapContext);
    if (swapContext.wrapSOL() && swapContext.inputMintKey().equals(solanaAccounts.wrappedSolTokenMint())) {
      final var wrappedSolPDA = glamAccountClient.wrappedSolPDA().publicKey();
      return withRefresh(refresh, List.of(
          fundWrappedSol(wrappedSolPDA, swapContext.amount()),
          glamAccountClient.syncNative(),
          glamJupiterSwap
      ));
    } else {
      return withRefresh(refresh, List.of(glamJupiterSwap));
    }
  }

  @Override
  public List<Instruction> swap(final JupiterSwapContext swapContext) {
    if (swapContext.createATA()) {
      return swapChecked(swapContext);
    } else {
      return swapUnchecked(swapContext);
    }
  }

  @Override
  public Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final PublicKey inputTokenProgram,
                                                                       final PublicKey inputMintKey,
                                                                       final PublicKey outputTokenProgram,
                                                                       final PublicKey outputMintKey) {
    final var outputVaultATA = glamAccountClient.findATA(outputTokenProgram, outputMintKey).publicKey();
    final var createVaultOutputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
        true, outputVaultATA, outputMintKey, outputTokenProgram
    );

    if (inputMintKey.equals(solanaAccounts.wrappedSolTokenMint())) {
      final var inputVaultATA = glamAccountClient.findATA(inputTokenProgram, inputMintKey).publicKey();
      final var createVaultInputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
          true, inputVaultATA, inputMintKey, inputTokenProgram
      );
      return Map.of(
          inputVaultATA, createVaultInputATA,
          outputVaultATA, createVaultOutputATA
      );
    } else {
      return Map.of(outputVaultATA, createVaultOutputATA);
    }
  }
}

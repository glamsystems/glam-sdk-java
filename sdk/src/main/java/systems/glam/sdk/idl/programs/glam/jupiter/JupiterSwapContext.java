package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;

import java.util.List;
import java.util.Objects;

/// What one swap through GLAM needs from the caller. The mint keys and token programs of both sides are
/// required; the oracles, program-state keys and Kamino reserves are the caller's knowledge of the global
/// configuration and may be absent.
///
/// `kaminoReserves` are the reserves that price any of the three roles (the SOL/USD, input and output
/// oracles registered as `KaminoReserve`), decoded by the caller from the reserve accounts: the program
/// reads a reserve's last price write before it prices anything, so each one that prices a role is
/// refreshed in front of the swap, whether or not a price-check skip is requested, since the program may
/// still price the swap. A caller certain of an unconditional skip may leave them out. Reserves that
/// price none of the three roles are ignored.
public record JupiterSwapContext(PublicKey inputProgramStateKey,
                                 PublicKey inputMintKey,
                                 PublicKey inputTokenProgram,
                                 PublicKey inputTokenOracleKey,
                                 PublicKey outputProgramStateKey,
                                 PublicKey outputMintKey,
                                 PublicKey outputTokenProgram,
                                 PublicKey outputTokenOracleKey,
                                 PublicKey solUsdOracleKey,
                                 boolean skipQuotePriceCheck,
                                 long amount,
                                 Instruction swapInstruction,
                                 boolean wrapSOL,
                                 boolean createATA,
                                 List<KaminoReserveRefresh> kaminoReserves) {

  public JupiterSwapContext {
    Objects.requireNonNull(inputMintKey, "inputMintKey");
    Objects.requireNonNull(inputTokenProgram, "inputTokenProgram");
    Objects.requireNonNull(outputMintKey, "outputMintKey");
    Objects.requireNonNull(outputTokenProgram, "outputTokenProgram");
    Objects.requireNonNull(swapInstruction, "swapInstruction");
    kaminoReserves = kaminoReserves == null ? List.of() : List.copyOf(kaminoReserves);
  }

  public static Builder build() {
    return new Builder();
  }

  public static final class Builder {

    private PublicKey inputProgramStateKey;
    private PublicKey inputMintKey;
    private PublicKey inputTokenProgram;
    private PublicKey inputTokenOracleKey;
    private PublicKey outputProgramStateKey;
    private PublicKey outputMintKey;
    private PublicKey outputTokenProgram;
    private PublicKey outputTokenOracleKey;
    private PublicKey solUsdOracleKey;
    private boolean skipQuotePriceCheck;
    private long amount;
    private Instruction swapInstruction;
    private boolean wrapSOL;
    private boolean createATA;
    private List<KaminoReserveRefresh> kaminoReserves = List.of();

    private Builder() {
    }

    public JupiterSwapContext create() {
      return new JupiterSwapContext(
          inputProgramStateKey,
          inputMintKey,
          inputTokenProgram,
          inputTokenOracleKey,
          outputProgramStateKey,
          outputMintKey,
          outputTokenProgram,
          outputTokenOracleKey,
          solUsdOracleKey,
          skipQuotePriceCheck,
          amount,
          swapInstruction,
          wrapSOL,
          createATA,
          kaminoReserves
      );
    }

    public Builder inputProgramStateKey(final PublicKey inputProgramStateKey) {
      this.inputProgramStateKey = inputProgramStateKey;
      return this;
    }

    public Builder inputMintKey(final PublicKey inputMintKey) {
      this.inputMintKey = inputMintKey;
      return this;
    }

    public Builder inputTokenProgram(final PublicKey inputTokenProgram) {
      this.inputTokenProgram = inputTokenProgram;
      return this;
    }

    public Builder inputTokenOracleKey(final PublicKey inputTokenOracleKey) {
      this.inputTokenOracleKey = inputTokenOracleKey;
      return this;
    }

    public Builder outputProgramStateKey(final PublicKey outputProgramStateKey) {
      this.outputProgramStateKey = outputProgramStateKey;
      return this;
    }

    public Builder outputMintKey(final PublicKey outputMintKey) {
      this.outputMintKey = outputMintKey;
      return this;
    }

    public Builder outputTokenProgram(final PublicKey outputTokenProgram) {
      this.outputTokenProgram = outputTokenProgram;
      return this;
    }

    public Builder outputTokenOracleKey(final PublicKey outputTokenOracleKey) {
      this.outputTokenOracleKey = outputTokenOracleKey;
      return this;
    }

    public Builder solUsdOracleKey(final PublicKey solUsdOracleKey) {
      this.solUsdOracleKey = solUsdOracleKey;
      return this;
    }

    public Builder skipQuotePriceCheck(final boolean skipQuotePriceCheck) {
      this.skipQuotePriceCheck = skipQuotePriceCheck;
      return this;
    }

    public Builder amount(final long amount) {
      this.amount = amount;
      return this;
    }

    public Builder swapInstruction(final Instruction swapInstruction) {
      this.swapInstruction = swapInstruction;
      return this;
    }

    public Builder wrapSOL(final boolean wrapSOL) {
      this.wrapSOL = wrapSOL;
      return this;
    }

    public Builder createATA(final boolean createATA) {
      this.createATA = createATA;
      return this;
    }

    public Builder kaminoReserves(final List<KaminoReserveRefresh> kaminoReserves) {
      this.kaminoReserves = kaminoReserves;
      return this;
    }
  }
}

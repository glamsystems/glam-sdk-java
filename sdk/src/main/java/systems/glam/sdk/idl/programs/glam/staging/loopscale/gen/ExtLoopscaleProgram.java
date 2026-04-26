package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types.*;

import java.util.List;

import static software.sava.core.accounts.meta.AccountMeta.*;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtLoopscaleProgram {

  public static final Discriminator BORROW_PRINCIPAL_DISCRIMINATOR = toDiscriminator(106, 10, 38, 204, 139, 188, 124, 50);

  public static List<AccountMeta> borrowPrincipalKeys(final SolanaAccounts solanaAccounts,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamVaultKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey integrationAuthorityKey,
                                                      final PublicKey cpiProgramKey,
                                                      final PublicKey glamProtocolProgramKey,
                                                      final PublicKey bsAuthKey,
                                                      final PublicKey loanKey,
                                                      final PublicKey strategyKey,
                                                      final PublicKey marketInformationKey,
                                                      final PublicKey principalMintKey,
                                                      final PublicKey borrowerTaKey,
                                                      final PublicKey strategyTaKey,
                                                      final PublicKey associatedTokenProgramKey,
                                                      final PublicKey tokenProgramKey,
                                                      final PublicKey eventAuthorityKey,
                                                      final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createWrite(strategyKey),
      createWrite(marketInformationKey),
      createRead(principalMintKey),
      createWrite(borrowerTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction borrowPrincipal(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final SolanaAccounts solanaAccounts,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamVaultKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey integrationAuthorityKey,
                                            final PublicKey cpiProgramKey,
                                            final PublicKey glamProtocolProgramKey,
                                            final PublicKey bsAuthKey,
                                            final PublicKey loanKey,
                                            final PublicKey strategyKey,
                                            final PublicKey marketInformationKey,
                                            final PublicKey principalMintKey,
                                            final PublicKey borrowerTaKey,
                                            final PublicKey strategyTaKey,
                                            final PublicKey associatedTokenProgramKey,
                                            final PublicKey tokenProgramKey,
                                            final PublicKey eventAuthorityKey,
                                            final PublicKey programKey,
                                            final BorrowPrincipalParams params) {
    final var keys = borrowPrincipalKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      strategyKey,
      marketInformationKey,
      principalMintKey,
      borrowerTaKey,
      strategyTaKey,
      associatedTokenProgramKey,
      tokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return borrowPrincipal(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction borrowPrincipal(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final List<AccountMeta> keys,
                                            final BorrowPrincipalParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = BORROW_PRINCIPAL_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record BorrowPrincipalIxData(Discriminator discriminator, BorrowPrincipalParams params) implements SerDe {  

    public static BorrowPrincipalIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static BorrowPrincipalIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = BorrowPrincipalParams.read(_data, i);
      return new BorrowPrincipalIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  public static final Discriminator CLOSE_LOAN_DISCRIMINATOR = toDiscriminator(96, 114, 111, 204, 149, 228, 235, 124);

  public static List<AccountMeta> closeLoanKeys(final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey,
                                                final PublicKey bsAuthKey,
                                                final PublicKey loanKey,
                                                final PublicKey eventAuthorityKey,
                                                final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction closeLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                      final SolanaAccounts solanaAccounts,
                                      final PublicKey glamStateKey,
                                      final PublicKey glamVaultKey,
                                      final PublicKey glamSignerKey,
                                      final PublicKey integrationAuthorityKey,
                                      final PublicKey cpiProgramKey,
                                      final PublicKey glamProtocolProgramKey,
                                      final PublicKey bsAuthKey,
                                      final PublicKey loanKey,
                                      final PublicKey eventAuthorityKey,
                                      final PublicKey programKey) {
    final var keys = closeLoanKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      eventAuthorityKey,
      programKey
    );
    return closeLoan(invokedExtLoopscaleProgramMeta, keys);
  }

  public static Instruction closeLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                      final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, CLOSE_LOAN_DISCRIMINATOR);
  }

  public static final Discriminator CREATE_LOAN_DISCRIMINATOR = toDiscriminator(166, 131, 118, 219, 138, 218, 206, 140);

  public static List<AccountMeta> createLoanKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey bsAuthKey,
                                                 final PublicKey loanKey,
                                                 final PublicKey eventAuthorityKey,
                                                 final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(loanKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction createLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey cpiProgramKey,
                                       final PublicKey glamProtocolProgramKey,
                                       final PublicKey bsAuthKey,
                                       final PublicKey loanKey,
                                       final PublicKey eventAuthorityKey,
                                       final PublicKey programKey,
                                       final CreateLoanParams params) {
    final var keys = createLoanKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      eventAuthorityKey,
      programKey
    );
    return createLoan(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction createLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final List<AccountMeta> keys,
                                       final CreateLoanParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = CREATE_LOAN_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record CreateLoanIxData(Discriminator discriminator, CreateLoanParams params) implements SerDe {  

    public static CreateLoanIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static final int PARAMS_OFFSET = 8;

    public static CreateLoanIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = CreateLoanParams.read(_data, i);
      return new CreateLoanIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator DEPOSIT_COLLATERAL_DISCRIMINATOR = toDiscriminator(156, 131, 142, 116, 146, 247, 162, 120);

  public static List<AccountMeta> depositCollateralKeys(final SolanaAccounts solanaAccounts,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamVaultKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey cpiProgramKey,
                                                        final PublicKey glamProtocolProgramKey,
                                                        final PublicKey bsAuthKey,
                                                        final PublicKey loanKey,
                                                        final PublicKey borrowerCollateralTaKey,
                                                        final PublicKey loanCollateralTaKey,
                                                        final PublicKey depositMintKey,
                                                        final PublicKey assetIdentifierKey,
                                                        final PublicKey tokenProgramKey,
                                                        final PublicKey associatedTokenProgramKey,
                                                        final PublicKey eventAuthorityKey,
                                                        final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createWrite(borrowerCollateralTaKey),
      createWrite(loanCollateralTaKey),
      createRead(depositMintKey),
      createRead(assetIdentifierKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction depositCollateral(final AccountMeta invokedExtLoopscaleProgramMeta,
                                              final SolanaAccounts solanaAccounts,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamVaultKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationAuthorityKey,
                                              final PublicKey cpiProgramKey,
                                              final PublicKey glamProtocolProgramKey,
                                              final PublicKey bsAuthKey,
                                              final PublicKey loanKey,
                                              final PublicKey borrowerCollateralTaKey,
                                              final PublicKey loanCollateralTaKey,
                                              final PublicKey depositMintKey,
                                              final PublicKey assetIdentifierKey,
                                              final PublicKey tokenProgramKey,
                                              final PublicKey associatedTokenProgramKey,
                                              final PublicKey eventAuthorityKey,
                                              final PublicKey programKey,
                                              final DepositCollateralParams params) {
    final var keys = depositCollateralKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      borrowerCollateralTaKey,
      loanCollateralTaKey,
      depositMintKey,
      assetIdentifierKey,
      tokenProgramKey,
      associatedTokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return depositCollateral(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction depositCollateral(final AccountMeta invokedExtLoopscaleProgramMeta,
                                              final List<AccountMeta> keys,
                                              final DepositCollateralParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = DEPOSIT_COLLATERAL_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record DepositCollateralIxData(Discriminator discriminator, DepositCollateralParams params) implements SerDe {  

    public static DepositCollateralIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static DepositCollateralIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = DepositCollateralParams.read(_data, i);
      return new DepositCollateralIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  public static final Discriminator LOCK_LOAN_DISCRIMINATOR = toDiscriminator(28, 101, 52, 240, 146, 230, 95, 22);

  public static List<AccountMeta> lockLoanKeys(final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey bsAuthKey,
                                               final PublicKey loanKey,
                                               final PublicKey instructionsSysvarKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createRead(instructionsSysvarKey)
    );
  }

  public static Instruction lockLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                     final SolanaAccounts solanaAccounts,
                                     final PublicKey glamStateKey,
                                     final PublicKey glamVaultKey,
                                     final PublicKey glamSignerKey,
                                     final PublicKey integrationAuthorityKey,
                                     final PublicKey cpiProgramKey,
                                     final PublicKey glamProtocolProgramKey,
                                     final PublicKey bsAuthKey,
                                     final PublicKey loanKey,
                                     final PublicKey instructionsSysvarKey,
                                     final LockLoanParams params) {
    final var keys = lockLoanKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      instructionsSysvarKey
    );
    return lockLoan(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction lockLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                     final List<AccountMeta> keys,
                                     final LockLoanParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = LOCK_LOAN_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record LockLoanIxData(Discriminator discriminator, LockLoanParams params) implements SerDe {  

    public static LockLoanIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 9;

    public static final int PARAMS_OFFSET = 8;

    public static LockLoanIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = LockLoanParams.read(_data, i);
      return new LockLoanIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator REFINANCE_LEDGER_DISCRIMINATOR = toDiscriminator(103, 41, 134, 43, 140, 152, 253, 74);

  public static List<AccountMeta> refinanceLedgerKeys(final SolanaAccounts solanaAccounts,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamVaultKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey integrationAuthorityKey,
                                                      final PublicKey cpiProgramKey,
                                                      final PublicKey glamProtocolProgramKey,
                                                      final PublicKey bsAuthKey,
                                                      final PublicKey loanKey,
                                                      final PublicKey oldStrategyKey,
                                                      final PublicKey newStrategyKey,
                                                      final PublicKey oldStrategyTaKey,
                                                      final PublicKey newStrategyTaKey,
                                                      final PublicKey oldStrategyMarketInformationKey,
                                                      final PublicKey newStrategyMarketInformationKey,
                                                      final PublicKey principalMintKey,
                                                      final PublicKey tokenProgramKey,
                                                      final PublicKey associatedTokenProgramKey,
                                                      final PublicKey eventAuthorityKey,
                                                      final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createWrite(oldStrategyKey),
      createWrite(newStrategyKey),
      createWrite(oldStrategyTaKey),
      createWrite(newStrategyTaKey),
      createWrite(oldStrategyMarketInformationKey),
      createWrite(newStrategyMarketInformationKey),
      createRead(principalMintKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction refinanceLedger(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final SolanaAccounts solanaAccounts,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamVaultKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey integrationAuthorityKey,
                                            final PublicKey cpiProgramKey,
                                            final PublicKey glamProtocolProgramKey,
                                            final PublicKey bsAuthKey,
                                            final PublicKey loanKey,
                                            final PublicKey oldStrategyKey,
                                            final PublicKey newStrategyKey,
                                            final PublicKey oldStrategyTaKey,
                                            final PublicKey newStrategyTaKey,
                                            final PublicKey oldStrategyMarketInformationKey,
                                            final PublicKey newStrategyMarketInformationKey,
                                            final PublicKey principalMintKey,
                                            final PublicKey tokenProgramKey,
                                            final PublicKey associatedTokenProgramKey,
                                            final PublicKey eventAuthorityKey,
                                            final PublicKey programKey,
                                            final RefinanceLedgerParams params) {
    final var keys = refinanceLedgerKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      oldStrategyKey,
      newStrategyKey,
      oldStrategyTaKey,
      newStrategyTaKey,
      oldStrategyMarketInformationKey,
      newStrategyMarketInformationKey,
      principalMintKey,
      tokenProgramKey,
      associatedTokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return refinanceLedger(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction refinanceLedger(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final List<AccountMeta> keys,
                                            final RefinanceLedgerParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = REFINANCE_LEDGER_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record RefinanceLedgerIxData(Discriminator discriminator, RefinanceLedgerParams params) implements SerDe {  

    public static RefinanceLedgerIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static RefinanceLedgerIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = RefinanceLedgerParams.read(_data, i);
      return new RefinanceLedgerIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  public static final Discriminator REPAY_PRINCIPAL_DISCRIMINATOR = toDiscriminator(229, 67, 83, 65, 77, 84, 80, 141);

  public static List<AccountMeta> repayPrincipalKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey integrationAuthorityKey,
                                                     final PublicKey cpiProgramKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey bsAuthKey,
                                                     final PublicKey loanKey,
                                                     final PublicKey strategyKey,
                                                     final PublicKey marketInformationKey,
                                                     final PublicKey principalMintKey,
                                                     final PublicKey borrowerTaKey,
                                                     final PublicKey strategyTaKey,
                                                     final PublicKey associatedTokenProgramKey,
                                                     final PublicKey tokenProgramKey,
                                                     final PublicKey eventAuthorityKey,
                                                     final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createWrite(strategyKey),
      createWrite(marketInformationKey),
      createRead(principalMintKey),
      createWrite(borrowerTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction repayPrincipal(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey bsAuthKey,
                                           final PublicKey loanKey,
                                           final PublicKey strategyKey,
                                           final PublicKey marketInformationKey,
                                           final PublicKey principalMintKey,
                                           final PublicKey borrowerTaKey,
                                           final PublicKey strategyTaKey,
                                           final PublicKey associatedTokenProgramKey,
                                           final PublicKey tokenProgramKey,
                                           final PublicKey eventAuthorityKey,
                                           final PublicKey programKey,
                                           final RepayPrincipalParams params) {
    final var keys = repayPrincipalKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      strategyKey,
      marketInformationKey,
      principalMintKey,
      borrowerTaKey,
      strategyTaKey,
      associatedTokenProgramKey,
      tokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return repayPrincipal(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction repayPrincipal(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final List<AccountMeta> keys,
                                           final RepayPrincipalParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = REPAY_PRINCIPAL_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record RepayPrincipalIxData(Discriminator discriminator, RepayPrincipalParams params) implements SerDe {  

    public static RepayPrincipalIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 18;

    public static final int PARAMS_OFFSET = 8;

    public static RepayPrincipalIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = RepayPrincipalParams.read(_data, i);
      return new RepayPrincipalIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator SET_LOOPSCALE_POLICY_DISCRIMINATOR = toDiscriminator(216, 84, 180, 148, 164, 253, 148, 173);

  public static List<AccountMeta> setLoopscalePolicyKeys(final PublicKey glamStateKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  public static Instruction setLoopscalePolicy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final LoopscalePolicy policy) {
    final var keys = setLoopscalePolicyKeys(
      glamStateKey,
      glamSignerKey,
      glamProtocolProgramKey
    );
    return setLoopscalePolicy(invokedExtLoopscaleProgramMeta, keys, policy);
  }

  public static Instruction setLoopscalePolicy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final List<AccountMeta> keys,
                                               final LoopscalePolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_LOOPSCALE_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record SetLoopscalePolicyIxData(Discriminator discriminator, LoopscalePolicy policy) implements SerDe {  

    public static SetLoopscalePolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POLICY_OFFSET = 8;

    public static SetLoopscalePolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = LoopscalePolicy.read(_data, i);
      return new SetLoopscalePolicyIxData(discriminator, policy);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += policy.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + policy.l();
    }
  }

  public static final Discriminator UNLOCK_LOAN_DISCRIMINATOR = toDiscriminator(121, 226, 178, 98, 215, 209, 240, 38);

  public static List<AccountMeta> unlockLoanKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey bsAuthKey,
                                                 final PublicKey loanKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey)
    );
  }

  public static Instruction unlockLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey cpiProgramKey,
                                       final PublicKey glamProtocolProgramKey,
                                       final PublicKey bsAuthKey,
                                       final PublicKey loanKey,
                                       final LoanUnlockParams params) {
    final var keys = unlockLoanKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey
    );
    return unlockLoan(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction unlockLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final List<AccountMeta> keys,
                                       final LoanUnlockParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = UNLOCK_LOAN_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record UnlockLoanIxData(Discriminator discriminator, LoanUnlockParams params) implements SerDe {  

    public static UnlockLoanIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static UnlockLoanIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = LoanUnlockParams.read(_data, i);
      return new UnlockLoanIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  public static final Discriminator UPDATE_WEIGHT_MATRIX_DISCRIMINATOR = toDiscriminator(252, 166, 37, 207, 154, 83, 187, 128);

  public static List<AccountMeta> updateWeightMatrixKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey,
                                                         final PublicKey bsAuthKey,
                                                         final PublicKey loanKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(loanKey)
    );
  }

  public static Instruction updateWeightMatrix(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey bsAuthKey,
                                               final PublicKey loanKey,
                                               final UpdateWeightMatrixParams params) {
    final var keys = updateWeightMatrixKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey
    );
    return updateWeightMatrix(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction updateWeightMatrix(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final List<AccountMeta> keys,
                                               final UpdateWeightMatrixParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = UPDATE_WEIGHT_MATRIX_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record UpdateWeightMatrixIxData(Discriminator discriminator, UpdateWeightMatrixParams params) implements SerDe {  

    public static UpdateWeightMatrixIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static UpdateWeightMatrixIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = UpdateWeightMatrixParams.read(_data, i);
      return new UpdateWeightMatrixIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  public static final Discriminator WITHDRAW_COLLATERAL_DISCRIMINATOR = toDiscriminator(115, 135, 168, 106, 139, 214, 138, 150);

  public static List<AccountMeta> withdrawCollateralKeys(final SolanaAccounts solanaAccounts,
                                                         final PublicKey glamStateKey,
                                                         final PublicKey glamVaultKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey integrationAuthorityKey,
                                                         final PublicKey cpiProgramKey,
                                                         final PublicKey glamProtocolProgramKey,
                                                         final PublicKey bsAuthKey,
                                                         final PublicKey loanKey,
                                                         final PublicKey borrowerTaKey,
                                                         final PublicKey loanTaKey,
                                                         final PublicKey assetMintKey,
                                                         final PublicKey tokenProgramKey,
                                                         final PublicKey associatedTokenProgramKey,
                                                         final PublicKey eventAuthorityKey,
                                                         final PublicKey programKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(bsAuthKey),
      createWrite(loanKey),
      createWrite(borrowerTaKey),
      createWrite(loanTaKey),
      createRead(assetMintKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey),
      createRead(programKey)
    );
  }

  public static Instruction withdrawCollateral(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey integrationAuthorityKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey glamProtocolProgramKey,
                                               final PublicKey bsAuthKey,
                                               final PublicKey loanKey,
                                               final PublicKey borrowerTaKey,
                                               final PublicKey loanTaKey,
                                               final PublicKey assetMintKey,
                                               final PublicKey tokenProgramKey,
                                               final PublicKey associatedTokenProgramKey,
                                               final PublicKey eventAuthorityKey,
                                               final PublicKey programKey,
                                               final WithdrawCollateralParams params) {
    final var keys = withdrawCollateralKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      loanKey,
      borrowerTaKey,
      loanTaKey,
      assetMintKey,
      tokenProgramKey,
      associatedTokenProgramKey,
      eventAuthorityKey,
      programKey
    );
    return withdrawCollateral(invokedExtLoopscaleProgramMeta, keys, params);
  }

  public static Instruction withdrawCollateral(final AccountMeta invokedExtLoopscaleProgramMeta,
                                               final List<AccountMeta> keys,
                                               final WithdrawCollateralParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = WITHDRAW_COLLATERAL_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record WithdrawCollateralIxData(Discriminator discriminator, WithdrawCollateralParams params) implements SerDe {  

    public static WithdrawCollateralIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static WithdrawCollateralIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = WithdrawCollateralParams.read(_data, i);
      return new WithdrawCollateralIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += params.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + params.l();
    }
  }

  private ExtLoopscaleProgram() {
  }
}

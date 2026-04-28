package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types.*;

import java.util.List;

import static software.sava.core.accounts.meta.AccountMeta.*;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtLoopscaleProgram {

  public static final Discriminator BORROW_PRINCIPAL_DISCRIMINATOR = toDiscriminator(106, 10, 38, 204, 139, 188, 124, 50);

  /// Borrow principal against a locked loan.
  /// 
  /// - Permission: `BorrowPrincipal`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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
                                                      final PublicKey eventAuthorityKey) {
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
      createWrite(strategyKey),
      createWrite(marketInformationKey),
      createRead(principalMintKey),
      createWrite(borrowerTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Borrow principal against a locked loan.
  /// 
  /// - Permission: `BorrowPrincipal`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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
      eventAuthorityKey
    );
    return borrowPrincipal(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Borrow principal against a locked loan.
  /// 
  /// - Permission: `BorrowPrincipal`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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

  /// Close an existing Loopscale loan PDA.
  /// 
  /// - Permission: `ManageLoan`.
  ///
  public static List<AccountMeta> closeLoanKeys(final SolanaAccounts solanaAccounts,
                                                final PublicKey glamStateKey,
                                                final PublicKey glamVaultKey,
                                                final PublicKey glamSignerKey,
                                                final PublicKey integrationAuthorityKey,
                                                final PublicKey cpiProgramKey,
                                                final PublicKey glamProtocolProgramKey,
                                                final PublicKey bsAuthKey,
                                                final PublicKey loanKey,
                                                final PublicKey eventAuthorityKey) {
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
      createRead(eventAuthorityKey)
    );
  }

  /// Close an existing Loopscale loan PDA.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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
                                      final PublicKey eventAuthorityKey) {
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
      eventAuthorityKey
    );
    return closeLoan(invokedExtLoopscaleProgramMeta, keys);
  }

  /// Close an existing Loopscale loan PDA.
  /// 
  /// - Permission: `ManageLoan`.
  ///
  public static Instruction closeLoan(final AccountMeta invokedExtLoopscaleProgramMeta,
                                      final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, CLOSE_LOAN_DISCRIMINATOR);
  }

  public static final Discriminator CLOSE_STRATEGY_DISCRIMINATOR = toDiscriminator(56, 247, 170, 246, 89, 221, 134, 200);

  /// Close a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CloseStrategy`.
  /// - Policy: `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static List<AccountMeta> closeStrategyKeys(final SolanaAccounts solanaAccounts,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamVaultKey,
                                                    final PublicKey glamSignerKey,
                                                    final PublicKey integrationAuthorityKey,
                                                    final PublicKey cpiProgramKey,
                                                    final PublicKey glamProtocolProgramKey,
                                                    final PublicKey bsAuthKey,
                                                    final PublicKey strategyKey,
                                                    final PublicKey principalMintKey,
                                                    final PublicKey tokenProgramKey,
                                                    final PublicKey associatedTokenProgramKey,
                                                    final PublicKey eventAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(strategyKey),
      createRead(principalMintKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Close a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CloseStrategy`.
  /// - Policy: `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static Instruction closeStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                          final SolanaAccounts solanaAccounts,
                                          final PublicKey glamStateKey,
                                          final PublicKey glamVaultKey,
                                          final PublicKey glamSignerKey,
                                          final PublicKey integrationAuthorityKey,
                                          final PublicKey cpiProgramKey,
                                          final PublicKey glamProtocolProgramKey,
                                          final PublicKey bsAuthKey,
                                          final PublicKey strategyKey,
                                          final PublicKey principalMintKey,
                                          final PublicKey tokenProgramKey,
                                          final PublicKey associatedTokenProgramKey,
                                          final PublicKey eventAuthorityKey) {
    final var keys = closeStrategyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      strategyKey,
      principalMintKey,
      tokenProgramKey,
      associatedTokenProgramKey,
      eventAuthorityKey
    );
    return closeStrategy(invokedExtLoopscaleProgramMeta, keys);
  }

  /// Close a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CloseStrategy`.
  /// - Policy: `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static Instruction closeStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                          final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, CLOSE_STRATEGY_DISCRIMINATOR);
  }

  public static final Discriminator CREATE_LOAN_DISCRIMINATOR = toDiscriminator(166, 131, 118, 219, 138, 218, 206, 140);

  /// Create a new Loopscale loan PDA owned by the GLAM vault.
  /// 
  /// - Permission: `ManageLoan`.
  ///
  public static List<AccountMeta> createLoanKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey cpiProgramKey,
                                                 final PublicKey glamProtocolProgramKey,
                                                 final PublicKey bsAuthKey,
                                                 final PublicKey loanKey,
                                                 final PublicKey eventAuthorityKey) {
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
      createRead(eventAuthorityKey)
    );
  }

  /// Create a new Loopscale loan PDA owned by the GLAM vault.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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
      eventAuthorityKey
    );
    return createLoan(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Create a new Loopscale loan PDA owned by the GLAM vault.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  public static final Discriminator CREATE_STRATEGY_DISCRIMINATOR = toDiscriminator(152, 160, 107, 148, 245, 190, 127, 224);

  /// Create a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CreateStrategy`.
  /// - Policy
  /// - `params.lender` must be the GLAM vault.
  /// - `params.external_yield_source_args` must be unset.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static List<AccountMeta> createStrategyKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey integrationAuthorityKey,
                                                     final PublicKey cpiProgramKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey bsAuthKey,
                                                     final PublicKey nonceKey,
                                                     final PublicKey strategyKey,
                                                     final PublicKey marketInformationKey,
                                                     final PublicKey principalMintKey,
                                                     final PublicKey eventAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createReadOnlySigner(nonceKey),
      createWrite(strategyKey),
      createRead(marketInformationKey),
      createRead(principalMintKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Create a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CreateStrategy`.
  /// - Policy
  /// - `params.lender` must be the GLAM vault.
  /// - `params.external_yield_source_args` must be unset.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction createStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey bsAuthKey,
                                           final PublicKey nonceKey,
                                           final PublicKey strategyKey,
                                           final PublicKey marketInformationKey,
                                           final PublicKey principalMintKey,
                                           final PublicKey eventAuthorityKey,
                                           final CreateStrategyParams params) {
    final var keys = createStrategyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      nonceKey,
      strategyKey,
      marketInformationKey,
      principalMintKey,
      eventAuthorityKey
    );
    return createStrategy(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Create a direct lender strategy owned by the GLAM vault.
  /// 
  /// - Permission: `CreateStrategy`.
  /// - Policy
  /// - `params.lender` must be the GLAM vault.
  /// - `params.external_yield_source_args` must be unset.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction createStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final List<AccountMeta> keys,
                                           final CreateStrategyParams params) {
    final byte[] _data = new byte[8 + params.l()];
    int i = CREATE_STRATEGY_DISCRIMINATOR.write(_data, 0);
    params.write(_data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record CreateStrategyIxData(Discriminator discriminator, CreateStrategyParams params) implements SerDe {  

    public static CreateStrategyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int PARAMS_OFFSET = 8;

    public static CreateStrategyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = CreateStrategyParams.read(_data, i);
      return new CreateStrategyIxData(discriminator, params);
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

  public static final Discriminator DEPOSIT_COLLATERAL_DISCRIMINATOR = toDiscriminator(156, 131, 142, 116, 146, 247, 162, 120);

  /// Deposit collateral into a Loopscale loan.
  /// 
  /// - Permission: `DepositCollateral`.
  /// - Policy: `deposit_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  ///
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
                                                        final PublicKey eventAuthorityKey) {
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
      createWrite(borrowerCollateralTaKey),
      createWrite(loanCollateralTaKey),
      createRead(depositMintKey),
      createRead(assetIdentifierKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Deposit collateral into a Loopscale loan.
  /// 
  /// - Permission: `DepositCollateral`.
  /// - Policy: `deposit_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  ///
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
      eventAuthorityKey
    );
    return depositCollateral(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Deposit collateral into a Loopscale loan.
  /// 
  /// - Permission: `DepositCollateral`.
  /// - Policy: `deposit_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  ///
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

  public static final Discriminator DEPOSIT_STRATEGY_DISCRIMINATOR = toDiscriminator(246, 82, 57, 226, 131, 222, 253, 249);

  /// Deposit principal into a direct lender strategy.
  /// 
  /// - Permission: `DepositStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static List<AccountMeta> depositStrategyKeys(final SolanaAccounts solanaAccounts,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamVaultKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey integrationAuthorityKey,
                                                      final PublicKey cpiProgramKey,
                                                      final PublicKey glamProtocolProgramKey,
                                                      final PublicKey bsAuthKey,
                                                      final PublicKey strategyKey,
                                                      final PublicKey marketInformationKey,
                                                      final PublicKey principalMintKey,
                                                      final PublicKey lenderTaKey,
                                                      final PublicKey strategyTaKey,
                                                      final PublicKey associatedTokenProgramKey,
                                                      final PublicKey tokenProgramKey,
                                                      final PublicKey eventAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(strategyKey),
      createRead(marketInformationKey),
      createRead(principalMintKey),
      createWrite(lenderTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Deposit principal into a direct lender strategy.
  /// 
  /// - Permission: `DepositStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction depositStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final SolanaAccounts solanaAccounts,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamVaultKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey integrationAuthorityKey,
                                            final PublicKey cpiProgramKey,
                                            final PublicKey glamProtocolProgramKey,
                                            final PublicKey bsAuthKey,
                                            final PublicKey strategyKey,
                                            final PublicKey marketInformationKey,
                                            final PublicKey principalMintKey,
                                            final PublicKey lenderTaKey,
                                            final PublicKey strategyTaKey,
                                            final PublicKey associatedTokenProgramKey,
                                            final PublicKey tokenProgramKey,
                                            final PublicKey eventAuthorityKey,
                                            final long amount) {
    final var keys = depositStrategyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      strategyKey,
      marketInformationKey,
      principalMintKey,
      lenderTaKey,
      strategyTaKey,
      associatedTokenProgramKey,
      tokenProgramKey,
      eventAuthorityKey
    );
    return depositStrategy(invokedExtLoopscaleProgramMeta, keys, amount);
  }

  /// Deposit principal into a direct lender strategy.
  /// 
  /// - Permission: `DepositStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - `market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction depositStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final List<AccountMeta> keys,
                                            final long amount) {
    final byte[] _data = new byte[16];
    int i = DEPOSIT_STRATEGY_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record DepositStrategyIxData(Discriminator discriminator, long amount) implements SerDe {  

    public static DepositStrategyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static final int AMOUNT_OFFSET = 8;

    public static DepositStrategyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      return new DepositStrategyIxData(discriminator, amount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, amount);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator LOCK_LOAN_DISCRIMINATOR = toDiscriminator(28, 101, 52, 240, 146, 230, 95, 22);

  /// Lock a loan to commit borrowed principal and collateral.
  /// 
  /// - Permission: `ManageLoan`.
  ///
  public static List<AccountMeta> lockLoanKeys(final SolanaAccounts solanaAccounts,
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
      createWrite(loanKey),
      createRead(solanaAccounts.instructionsSysVar())
    );
  }

  /// Lock a loan to commit borrowed principal and collateral.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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
      loanKey
    );
    return lockLoan(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Lock a loan to commit borrowed principal and collateral.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  public static final Discriminator PRICE_LOANS_DISCRIMINATOR = toDiscriminator(171, 186, 128, 215, 24, 72, 231, 229);

  /// Price loans.
  /// 
  /// Extra accounts for pricing N loans:
  /// - N loan accounts
  /// - M oracle accounts (one per unique mint used by the loans)
  ///
  public static List<AccountMeta> priceLoansKeys(final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey signerKey,
                                                 final PublicKey solUsdOracleKey,
                                                 final PublicKey baseAssetOracleKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PublicKey glamConfigKey,
                                                 final PublicKey glamProtocolKey) {
    return List.of(
      createWrite(glamStateKey),
      createRead(glamVaultKey),
      createWritableSigner(signerKey),
      createRead(solUsdOracleKey),
      createRead(baseAssetOracleKey),
      createRead(integrationAuthorityKey),
      createRead(glamConfigKey),
      createRead(glamProtocolKey)
    );
  }

  /// Price loans.
  /// 
  /// Extra accounts for pricing N loans:
  /// - N loan accounts
  /// - M oracle accounts (one per unique mint used by the loans)
  ///
  public static Instruction priceLoans(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey signerKey,
                                       final PublicKey solUsdOracleKey,
                                       final PublicKey baseAssetOracleKey,
                                       final PublicKey integrationAuthorityKey,
                                       final PublicKey glamConfigKey,
                                       final PublicKey glamProtocolKey) {
    final var keys = priceLoansKeys(
      glamStateKey,
      glamVaultKey,
      signerKey,
      solUsdOracleKey,
      baseAssetOracleKey,
      integrationAuthorityKey,
      glamConfigKey,
      glamProtocolKey
    );
    return priceLoans(invokedExtLoopscaleProgramMeta, keys);
  }

  /// Price loans.
  /// 
  /// Extra accounts for pricing N loans:
  /// - N loan accounts
  /// - M oracle accounts (one per unique mint used by the loans)
  ///
  public static Instruction priceLoans(final AccountMeta invokedExtLoopscaleProgramMeta,
                                       final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, PRICE_LOANS_DISCRIMINATOR);
  }

  public static final Discriminator PRICE_STRATEGIES_DISCRIMINATOR = toDiscriminator(184, 134, 183, 172, 67, 183, 164, 102);

  /// Price direct lender strategies.
  /// 
  /// Extra accounts for pricing N strategies:
  /// - N strategy accounts
  /// - M oracle accounts (one per unique principal mint used by the strategies)
  ///
  public static List<AccountMeta> priceStrategiesKeys(final PublicKey glamStateKey,
                                                      final PublicKey glamVaultKey,
                                                      final PublicKey signerKey,
                                                      final PublicKey solUsdOracleKey,
                                                      final PublicKey baseAssetOracleKey,
                                                      final PublicKey integrationAuthorityKey,
                                                      final PublicKey glamConfigKey,
                                                      final PublicKey glamProtocolKey) {
    return List.of(
      createWrite(glamStateKey),
      createRead(glamVaultKey),
      createWritableSigner(signerKey),
      createRead(solUsdOracleKey),
      createRead(baseAssetOracleKey),
      createRead(integrationAuthorityKey),
      createRead(glamConfigKey),
      createRead(glamProtocolKey)
    );
  }

  /// Price direct lender strategies.
  /// 
  /// Extra accounts for pricing N strategies:
  /// - N strategy accounts
  /// - M oracle accounts (one per unique principal mint used by the strategies)
  ///
  public static Instruction priceStrategies(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamVaultKey,
                                            final PublicKey signerKey,
                                            final PublicKey solUsdOracleKey,
                                            final PublicKey baseAssetOracleKey,
                                            final PublicKey integrationAuthorityKey,
                                            final PublicKey glamConfigKey,
                                            final PublicKey glamProtocolKey) {
    final var keys = priceStrategiesKeys(
      glamStateKey,
      glamVaultKey,
      signerKey,
      solUsdOracleKey,
      baseAssetOracleKey,
      integrationAuthorityKey,
      glamConfigKey,
      glamProtocolKey
    );
    return priceStrategies(invokedExtLoopscaleProgramMeta, keys);
  }

  /// Price direct lender strategies.
  /// 
  /// Extra accounts for pricing N strategies:
  /// - N strategy accounts
  /// - M oracle accounts (one per unique principal mint used by the strategies)
  ///
  public static Instruction priceStrategies(final AccountMeta invokedExtLoopscaleProgramMeta,
                                            final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, PRICE_STRATEGIES_DISCRIMINATOR);
  }

  public static final Discriminator REFINANCE_LEDGER_DISCRIMINATOR = toDiscriminator(103, 41, 134, 43, 140, 152, 253, 74);

  /// Refinance a loan ledger from one strategy to another.
  /// 
  /// - Permission: `RefinanceLedger`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `new_strategy_market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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

  /// Refinance a loan ledger from one strategy to another.
  /// 
  /// - Permission: `RefinanceLedger`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `new_strategy_market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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

  /// Refinance a loan ledger from one strategy to another.
  /// 
  /// - Permission: `RefinanceLedger`.
  /// - Policy
  /// - `principal_mint` must be present in `LoopscalePolicy::borrow_allowlist`.
  /// - `new_strategy_market_information` must be present in `LoopscalePolicy::markets_allowlist`.
  ///
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

  /// Repay principal on a Loopscale loan.
  /// 
  /// - Permission: `RepayPrincipal`.
  ///
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
                                                     final PublicKey eventAuthorityKey) {
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
      createWrite(strategyKey),
      createWrite(marketInformationKey),
      createRead(principalMintKey),
      createWrite(borrowerTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Repay principal on a Loopscale loan.
  /// 
  /// - Permission: `RepayPrincipal`.
  ///
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
      eventAuthorityKey
    );
    return repayPrincipal(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Repay principal on a Loopscale loan.
  /// 
  /// - Permission: `RepayPrincipal`.
  ///
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

  /// Set the `LoopscalePolicy` (deposit, borrow, and markets allowlists) on the GLAM state.
  ///
  public static List<AccountMeta> setLoopscalePolicyKeys(final PublicKey glamStateKey,
                                                         final PublicKey glamSignerKey,
                                                         final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(glamProtocolProgramKey)
    );
  }

  /// Set the `LoopscalePolicy` (deposit, borrow, and markets allowlists) on the GLAM state.
  ///
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

  /// Set the `LoopscalePolicy` (deposit, borrow, and markets allowlists) on the GLAM state.
  ///
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

  /// Unlock a previously locked loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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
      createReadOnlySigner(bsAuthKey),
      createWrite(loanKey)
    );
  }

  /// Unlock a previously locked loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  /// Unlock a previously locked loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  public static final Discriminator UPDATE_STRATEGY_DISCRIMINATOR = toDiscriminator(16, 76, 138, 179, 171, 112, 196, 21);

  /// Update direct lender strategy terms.
  /// 
  /// - Permission: `UpdateStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `params.external_yield_source_args` must be unset when `params` is provided.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - When provided, `params.market_information` must be present in
  /// `LoopscalePolicy::markets_allowlist`.
  ///
  public static List<AccountMeta> updateStrategyKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey integrationAuthorityKey,
                                                     final PublicKey cpiProgramKey,
                                                     final PublicKey glamProtocolProgramKey,
                                                     final PublicKey bsAuthKey,
                                                     final PublicKey strategyKey,
                                                     final PublicKey principalMintKey,
                                                     final PublicKey strategyTaKey,
                                                     final PublicKey associatedTokenProgramKey,
                                                     final PublicKey tokenProgramKey,
                                                     final PublicKey eventAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(strategyKey),
      createRead(principalMintKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Update direct lender strategy terms.
  /// 
  /// - Permission: `UpdateStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `params.external_yield_source_args` must be unset when `params` is provided.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - When provided, `params.market_information` must be present in
  /// `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction updateStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey integrationAuthorityKey,
                                           final PublicKey cpiProgramKey,
                                           final PublicKey glamProtocolProgramKey,
                                           final PublicKey bsAuthKey,
                                           final PublicKey strategyKey,
                                           final PublicKey principalMintKey,
                                           final PublicKey strategyTaKey,
                                           final PublicKey associatedTokenProgramKey,
                                           final PublicKey tokenProgramKey,
                                           final PublicKey eventAuthorityKey,
                                           final MultiCollateralTermsUpdateParams[] collateralTerms,
                                           final UpdateStrategyParams params) {
    final var keys = updateStrategyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      strategyKey,
      principalMintKey,
      strategyTaKey,
      associatedTokenProgramKey,
      tokenProgramKey,
      eventAuthorityKey
    );
    return updateStrategy(invokedExtLoopscaleProgramMeta, keys, collateralTerms, params);
  }

  /// Update direct lender strategy terms.
  /// 
  /// - Permission: `UpdateStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  /// - `params.external_yield_source_args` must be unset when `params` is provided.
  /// - `principal_mint` must be present in `LoopscalePolicy::deposit_allowlist`.
  /// - When provided, `params.market_information` must be present in
  /// `LoopscalePolicy::markets_allowlist`.
  ///
  public static Instruction updateStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                           final List<AccountMeta> keys,
                                           final MultiCollateralTermsUpdateParams[] collateralTerms,
                                           final UpdateStrategyParams params) {
    final byte[] _data = new byte[
    8 + SerDeUtil.lenVector(4, collateralTerms)
    + (params == null ? 1 : (1 + params.l()))
    ];
    int i = UPDATE_STRATEGY_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeVector(4, collateralTerms, _data, i);
    SerDeUtil.writeOptional(1, params, _data, i);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record UpdateStrategyIxData(Discriminator discriminator, MultiCollateralTermsUpdateParams[] collateralTerms, UpdateStrategyParams params) implements SerDe {  

    public static UpdateStrategyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int COLLATERAL_TERMS_OFFSET = 8;

    public static UpdateStrategyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var collateralTerms = SerDeUtil.readVector(4, MultiCollateralTermsUpdateParams.class, MultiCollateralTermsUpdateParams::read, _data, i);
      i += SerDeUtil.lenVector(4, collateralTerms);
      final UpdateStrategyParams params;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        params = null;
      } else {
        ++i;
        params = UpdateStrategyParams.read(_data, i);
      }
      return new UpdateStrategyIxData(discriminator, collateralTerms, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, collateralTerms, _data, i);
      i += SerDeUtil.writeOptional(1, params, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, collateralTerms) + (params == null ? 1 : (1 + params.l()));
    }
  }

  public static final Discriminator UPDATE_WEIGHT_MATRIX_DISCRIMINATOR = toDiscriminator(252, 166, 37, 207, 154, 83, 187, 128);

  /// Update the collateral weight matrix on a loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  /// Update the collateral weight matrix on a loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  /// Update the collateral weight matrix on a loan.
  /// 
  /// - Permission: `ManageLoan`.
  ///
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

  /// Withdraw collateral from a Loopscale loan.
  /// 
  /// - Permission: `WithdrawCollateral`.
  ///
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
                                                         final PublicKey eventAuthorityKey) {
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
      createWrite(borrowerTaKey),
      createWrite(loanTaKey),
      createRead(assetMintKey),
      createRead(tokenProgramKey),
      createRead(associatedTokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Withdraw collateral from a Loopscale loan.
  /// 
  /// - Permission: `WithdrawCollateral`.
  ///
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
      eventAuthorityKey
    );
    return withdrawCollateral(invokedExtLoopscaleProgramMeta, keys, params);
  }

  /// Withdraw collateral from a Loopscale loan.
  /// 
  /// - Permission: `WithdrawCollateral`.
  ///
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

  public static final Discriminator WITHDRAW_STRATEGY_DISCRIMINATOR = toDiscriminator(31, 45, 162, 5, 193, 217, 134, 188);

  /// Withdraw principal from a direct lender strategy.
  /// 
  /// - Permission: `WithdrawStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static List<AccountMeta> withdrawStrategyKeys(final SolanaAccounts solanaAccounts,
                                                       final PublicKey glamStateKey,
                                                       final PublicKey glamVaultKey,
                                                       final PublicKey glamSignerKey,
                                                       final PublicKey integrationAuthorityKey,
                                                       final PublicKey cpiProgramKey,
                                                       final PublicKey glamProtocolProgramKey,
                                                       final PublicKey bsAuthKey,
                                                       final PublicKey strategyKey,
                                                       final PublicKey marketInformationKey,
                                                       final PublicKey principalMintKey,
                                                       final PublicKey lenderTaKey,
                                                       final PublicKey strategyTaKey,
                                                       final PublicKey associatedTokenProgramKey,
                                                       final PublicKey tokenProgramKey,
                                                       final PublicKey eventAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(integrationAuthorityKey),
      createRead(cpiProgramKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram()),
      createReadOnlySigner(bsAuthKey),
      createWrite(strategyKey),
      createRead(marketInformationKey),
      createRead(principalMintKey),
      createWrite(lenderTaKey),
      createWrite(strategyTaKey),
      createRead(associatedTokenProgramKey),
      createRead(tokenProgramKey),
      createRead(eventAuthorityKey)
    );
  }

  /// Withdraw principal from a direct lender strategy.
  /// 
  /// - Permission: `WithdrawStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static Instruction withdrawStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                             final SolanaAccounts solanaAccounts,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamVaultKey,
                                             final PublicKey glamSignerKey,
                                             final PublicKey integrationAuthorityKey,
                                             final PublicKey cpiProgramKey,
                                             final PublicKey glamProtocolProgramKey,
                                             final PublicKey bsAuthKey,
                                             final PublicKey strategyKey,
                                             final PublicKey marketInformationKey,
                                             final PublicKey principalMintKey,
                                             final PublicKey lenderTaKey,
                                             final PublicKey strategyTaKey,
                                             final PublicKey associatedTokenProgramKey,
                                             final PublicKey tokenProgramKey,
                                             final PublicKey eventAuthorityKey,
                                             final long amount,
                                             final boolean withdrawAll) {
    final var keys = withdrawStrategyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      integrationAuthorityKey,
      cpiProgramKey,
      glamProtocolProgramKey,
      bsAuthKey,
      strategyKey,
      marketInformationKey,
      principalMintKey,
      lenderTaKey,
      strategyTaKey,
      associatedTokenProgramKey,
      tokenProgramKey,
      eventAuthorityKey
    );
    return withdrawStrategy(invokedExtLoopscaleProgramMeta, keys, amount, withdrawAll);
  }

  /// Withdraw principal from a direct lender strategy.
  /// 
  /// - Permission: `WithdrawStrategy`.
  /// - Policy
  /// - `strategy` must be tracked in `StateAccount::external_positions`.
  ///
  public static Instruction withdrawStrategy(final AccountMeta invokedExtLoopscaleProgramMeta,
                                             final List<AccountMeta> keys,
                                             final long amount,
                                             final boolean withdrawAll) {
    final byte[] _data = new byte[17];
    int i = WITHDRAW_STRATEGY_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) (withdrawAll ? 1 : 0);

    return Instruction.createInstruction(invokedExtLoopscaleProgramMeta, keys, _data);
  }

  public record WithdrawStrategyIxData(Discriminator discriminator, long amount, boolean withdrawAll) implements SerDe {  

    public static WithdrawStrategyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 17;

    public static final int AMOUNT_OFFSET = 8;
    public static final int WITHDRAW_ALL_OFFSET = 16;

    public static WithdrawStrategyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      i += 8;
      final var withdrawAll = _data[i] == 1;
      return new WithdrawStrategyIxData(discriminator, amount, withdrawAll);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, amount);
      i += 8;
      _data[i] = (byte) (withdrawAll ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  private ExtLoopscaleProgram() {
  }
}

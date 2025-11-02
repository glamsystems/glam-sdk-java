package systems.glam.sdk.idl.programs.glam.policy.gen;

import java.util.List;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.borsh.Borsh;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;

import systems.glam.sdk.idl.programs.glam.policy.gen.types.AnchorExtraAccountMeta;

import static software.sava.core.accounts.meta.AccountMeta.createRead;
import static software.sava.core.accounts.meta.AccountMeta.createWritableSigner;
import static software.sava.core.accounts.meta.AccountMeta.createWrite;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class GlamPoliciesProgram {

  public static final Discriminator CLOSE_EXTRA_METAS_ACCOUNT_DISCRIMINATOR = toDiscriminator(67, 72, 24, 239, 222, 207, 240, 177);

  public static List<AccountMeta> closeExtraMetasAccountKeys(final AccountMeta invokedGlamPoliciesProgramMeta                                                             ,
                                                             final SolanaAccounts solanaAccounts,
                                                             final PublicKey extraMetasAccountKey,
                                                             final PublicKey mintKey,
                                                             final PublicKey authorityKey,
                                                             final PublicKey destinationKey) {
    return List.of(
      createWrite(extraMetasAccountKey),
      createRead(mintKey),
      createWritableSigner(authorityKey),
      createWrite(destinationKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction closeExtraMetasAccount(final AccountMeta invokedGlamPoliciesProgramMeta,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey extraMetasAccountKey,
                                                   final PublicKey mintKey,
                                                   final PublicKey authorityKey,
                                                   final PublicKey destinationKey) {
    final var keys = closeExtraMetasAccountKeys(
      invokedGlamPoliciesProgramMeta,
      solanaAccounts,
      extraMetasAccountKey,
      mintKey,
      authorityKey,
      destinationKey
    );
    return closeExtraMetasAccount(invokedGlamPoliciesProgramMeta, keys);
  }

  public static Instruction closeExtraMetasAccount(final AccountMeta invokedGlamPoliciesProgramMeta                                                   ,
                                                   final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamPoliciesProgramMeta, keys, CLOSE_EXTRA_METAS_ACCOUNT_DISCRIMINATOR);
  }

  public static final Discriminator CLOSE_POLICY_DISCRIMINATOR = toDiscriminator(55, 42, 248, 229, 222, 138, 26, 252);

  /// @param policyAccountKey lamports will be refunded to the owner
  public static List<AccountMeta> closePolicyKeys(final AccountMeta invokedGlamPoliciesProgramMeta                                                  ,
                                                  final SolanaAccounts solanaAccounts,
                                                  final PublicKey policyAccountKey,
                                                  final PublicKey signerKey,
                                                  final PublicKey subjectKey) {
    return List.of(
      createWrite(policyAccountKey),
      createWritableSigner(signerKey),
      createWrite(subjectKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  /// @param policyAccountKey lamports will be refunded to the owner
  public static Instruction closePolicy(final AccountMeta invokedGlamPoliciesProgramMeta,
                                        final SolanaAccounts solanaAccounts,
                                        final PublicKey policyAccountKey,
                                        final PublicKey signerKey,
                                        final PublicKey subjectKey) {
    final var keys = closePolicyKeys(
      invokedGlamPoliciesProgramMeta,
      solanaAccounts,
      policyAccountKey,
      signerKey,
      subjectKey
    );
    return closePolicy(invokedGlamPoliciesProgramMeta, keys);
  }

  public static Instruction closePolicy(final AccountMeta invokedGlamPoliciesProgramMeta                                        ,
                                        final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamPoliciesProgramMeta, keys, CLOSE_POLICY_DISCRIMINATOR);
  }

  public static final Discriminator CREATE_POLICY_DISCRIMINATOR = toDiscriminator(27, 81, 33, 27, 196, 103, 246, 53);

  /// @param authorityKey Must be the mint authority or permanent delegate
  public static List<AccountMeta> createPolicyKeys(final AccountMeta invokedGlamPoliciesProgramMeta                                                   ,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey policyAccountKey,
                                                   final PublicKey authorityKey,
                                                   final PublicKey subjectKey,
                                                   final PublicKey payerKey,
                                                   final PublicKey mintKey,
                                                   final PublicKey subjectTokenAccountKey) {
    return List.of(
      createWrite(policyAccountKey),
      createWritableSigner(authorityKey),
      createRead(subjectKey),
      createWritableSigner(payerKey),
      createRead(mintKey),
      createRead(subjectTokenAccountKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  /// @param authorityKey Must be the mint authority or permanent delegate
  public static Instruction createPolicy(final AccountMeta invokedGlamPoliciesProgramMeta,
                                         final SolanaAccounts solanaAccounts,
                                         final PublicKey policyAccountKey,
                                         final PublicKey authorityKey,
                                         final PublicKey subjectKey,
                                         final PublicKey payerKey,
                                         final PublicKey mintKey,
                                         final PublicKey subjectTokenAccountKey,
                                         final long lockedUntilTs) {
    final var keys = createPolicyKeys(
      invokedGlamPoliciesProgramMeta,
      solanaAccounts,
      policyAccountKey,
      authorityKey,
      subjectKey,
      payerKey,
      mintKey,
      subjectTokenAccountKey
    );
    return createPolicy(invokedGlamPoliciesProgramMeta, keys, lockedUntilTs);
  }

  public static Instruction createPolicy(final AccountMeta invokedGlamPoliciesProgramMeta                                         ,
                                         final List<AccountMeta> keys,
                                         final long lockedUntilTs) {
    final byte[] _data = new byte[16];
    int i = CREATE_POLICY_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, lockedUntilTs);

    return Instruction.createInstruction(invokedGlamPoliciesProgramMeta, keys, _data);
  }

  public record CreatePolicyIxData(Discriminator discriminator, long lockedUntilTs) implements Borsh {  

    public static CreatePolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static CreatePolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var lockedUntilTs = getInt64LE(_data, i);
      return new CreatePolicyIxData(discriminator, lockedUntilTs);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, lockedUntilTs);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator EXECUTE_DISCRIMINATOR = toDiscriminator(105, 37, 101, 197, 75, 251, 102, 26);

  public static List<AccountMeta> executeKeys(final AccountMeta invokedGlamPoliciesProgramMeta                                              ,
                                              final PublicKey srcAccountKey,
                                              final PublicKey mintKey,
                                              final PublicKey dstAccountKey,
                                              final PublicKey srcAccountAuthorityKey,
                                              final PublicKey extraMetasAccountKey,
                                              final PublicKey srcPolicyAccountKey,
                                              final PublicKey dstPolicyAccountKey) {
    return List.of(
      createRead(srcAccountKey),
      createRead(mintKey),
      createRead(dstAccountKey),
      createRead(srcAccountAuthorityKey),
      createRead(extraMetasAccountKey),
      createRead(srcPolicyAccountKey),
      createRead(dstPolicyAccountKey)
    );
  }

  public static Instruction execute(final AccountMeta invokedGlamPoliciesProgramMeta,
                                    final PublicKey srcAccountKey,
                                    final PublicKey mintKey,
                                    final PublicKey dstAccountKey,
                                    final PublicKey srcAccountAuthorityKey,
                                    final PublicKey extraMetasAccountKey,
                                    final PublicKey srcPolicyAccountKey,
                                    final PublicKey dstPolicyAccountKey,
                                    final long amount) {
    final var keys = executeKeys(
      invokedGlamPoliciesProgramMeta,
      srcAccountKey,
      mintKey,
      dstAccountKey,
      srcAccountAuthorityKey,
      extraMetasAccountKey,
      srcPolicyAccountKey,
      dstPolicyAccountKey
    );
    return execute(invokedGlamPoliciesProgramMeta, keys, amount);
  }

  public static Instruction execute(final AccountMeta invokedGlamPoliciesProgramMeta                                    ,
                                    final List<AccountMeta> keys,
                                    final long amount) {
    final byte[] _data = new byte[16];
    int i = EXECUTE_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);

    return Instruction.createInstruction(invokedGlamPoliciesProgramMeta, keys, _data);
  }

  public record ExecuteIxData(Discriminator discriminator, long amount) implements Borsh {  

    public static ExecuteIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static ExecuteIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      return new ExecuteIxData(discriminator, amount);
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

  public static final Discriminator INITIALIZE_EXTRA_METAS_ACCOUNT_DISCRIMINATOR = toDiscriminator(43, 34, 13, 49, 167, 88, 235, 235);

  public static List<AccountMeta> initializeExtraMetasAccountKeys(final AccountMeta invokedGlamPoliciesProgramMeta                                                                  ,
                                                                  final SolanaAccounts solanaAccounts,
                                                                  final PublicKey extraMetasAccountKey,
                                                                  final PublicKey mintKey,
                                                                  final PublicKey authorityKey,
                                                                  final PublicKey payerKey) {
    return List.of(
      createWrite(extraMetasAccountKey),
      createRead(mintKey),
      createWritableSigner(authorityKey),
      createWritableSigner(payerKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction initializeExtraMetasAccount(final AccountMeta invokedGlamPoliciesProgramMeta,
                                                        final SolanaAccounts solanaAccounts,
                                                        final PublicKey extraMetasAccountKey,
                                                        final PublicKey mintKey,
                                                        final PublicKey authorityKey,
                                                        final PublicKey payerKey,
                                                        final AnchorExtraAccountMeta[] metas) {
    final var keys = initializeExtraMetasAccountKeys(
      invokedGlamPoliciesProgramMeta,
      solanaAccounts,
      extraMetasAccountKey,
      mintKey,
      authorityKey,
      payerKey
    );
    return initializeExtraMetasAccount(invokedGlamPoliciesProgramMeta, keys, metas);
  }

  public static Instruction initializeExtraMetasAccount(final AccountMeta invokedGlamPoliciesProgramMeta                                                        ,
                                                        final List<AccountMeta> keys,
                                                        final AnchorExtraAccountMeta[] metas) {
    final byte[] _data = new byte[8 + Borsh.lenVector(metas)];
    int i = INITIALIZE_EXTRA_METAS_ACCOUNT_DISCRIMINATOR.write(_data, 0);
    Borsh.writeVector(metas, _data, i);

    return Instruction.createInstruction(invokedGlamPoliciesProgramMeta, keys, _data);
  }

  public record InitializeExtraMetasAccountIxData(Discriminator discriminator, AnchorExtraAccountMeta[] metas) implements Borsh {  

    public static InitializeExtraMetasAccountIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static InitializeExtraMetasAccountIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var metas = Borsh.readVector(AnchorExtraAccountMeta.class, AnchorExtraAccountMeta::read, _data, i);
      return new InitializeExtraMetasAccountIxData(discriminator, metas);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += Borsh.writeVector(metas, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + Borsh.lenVector(metas);
    }
  }

  private GlamPoliciesProgram() {
  }
}

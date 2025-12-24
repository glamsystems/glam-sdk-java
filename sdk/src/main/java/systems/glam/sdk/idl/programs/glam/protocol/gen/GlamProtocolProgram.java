package systems.glam.sdk.idl.programs.glam.protocol.gen;

import java.util.List;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import systems.glam.sdk.idl.programs.glam.protocol.gen.types.EmergencyAccessUpdateArgs;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.EngineField;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.ExtraParams;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.JupiterSwapPolicy;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.PricedProtocol;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.TransferPolicy;

import static java.util.Objects.requireNonNullElse;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.accounts.meta.AccountMeta.createRead;
import static software.sava.core.accounts.meta.AccountMeta.createReadOnlySigner;
import static software.sava.core.accounts.meta.AccountMeta.createWritableSigner;
import static software.sava.core.accounts.meta.AccountMeta.createWrite;
import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt32LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class GlamProtocolProgram {

  public static final Discriminator CANCEL_TIMELOCK_DISCRIMINATOR = toDiscriminator(158, 180, 47, 81, 133, 231, 168, 238);

  public static List<AccountMeta> cancelTimelockKeys(final PublicKey glamStateKey,
                                                     final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction cancelTimelock(final AccountMeta invokedGlamProtocolProgramMeta,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamSignerKey) {
    final var keys = cancelTimelockKeys(
      glamStateKey,
      glamSignerKey
    );
    return cancelTimelock(invokedGlamProtocolProgramMeta, keys);
  }

  public static Instruction cancelTimelock(final AccountMeta invokedGlamProtocolProgramMeta,
                                           final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, CANCEL_TIMELOCK_DISCRIMINATOR);
  }

  public static final Discriminator CLOSE_STATE_DISCRIMINATOR = toDiscriminator(25, 1, 184, 101, 200, 245, 210, 246);

  public static List<AccountMeta> closeStateKeys(final SolanaAccounts solanaAccounts,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamVaultKey,
                                                 final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction closeState(final AccountMeta invokedGlamProtocolProgramMeta,
                                       final SolanaAccounts solanaAccounts,
                                       final PublicKey glamStateKey,
                                       final PublicKey glamVaultKey,
                                       final PublicKey glamSignerKey) {
    final var keys = closeStateKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey
    );
    return closeState(invokedGlamProtocolProgramMeta, keys);
  }

  public static Instruction closeState(final AccountMeta invokedGlamProtocolProgramMeta,
                                       final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, CLOSE_STATE_DISCRIMINATOR);
  }

  public static final Discriminator CPI_PROXY_DISCRIMINATOR = toDiscriminator(65, 134, 48, 2, 7, 232, 199, 46);

  /// Only accessible by integration programs
  ///
  public static List<AccountMeta> cpiProxyKeys(final SolanaAccounts solanaAccounts,
                                               final PublicKey glamStateKey,
                                               final PublicKey glamVaultKey,
                                               final PublicKey glamSignerKey,
                                               final PublicKey cpiProgramKey,
                                               final PublicKey integrationAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(cpiProgramKey),
      createReadOnlySigner(integrationAuthorityKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  /// Only accessible by integration programs
  ///
  public static Instruction cpiProxy(final AccountMeta invokedGlamProtocolProgramMeta,
                                     final SolanaAccounts solanaAccounts,
                                     final PublicKey glamStateKey,
                                     final PublicKey glamVaultKey,
                                     final PublicKey glamSignerKey,
                                     final PublicKey cpiProgramKey,
                                     final PublicKey integrationAuthorityKey,
                                     final byte[] data,
                                     final ExtraParams[] extraParams) {
    final var keys = cpiProxyKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      cpiProgramKey,
      integrationAuthorityKey
    );
    return cpiProxy(invokedGlamProtocolProgramMeta, keys, data, extraParams);
  }

  /// Only accessible by integration programs
  ///
  public static Instruction cpiProxy(final AccountMeta invokedGlamProtocolProgramMeta,
                                     final List<AccountMeta> keys,
                                     final byte[] data,
                                     final ExtraParams[] extraParams) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, data) + SerDeUtil.lenVector(4, extraParams)];
    int i = CPI_PROXY_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeVector(4, data, _data, i);
    SerDeUtil.writeVector(4, extraParams, _data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record CpiProxyIxData(Discriminator discriminator, byte[] data, ExtraParams[] extraParams) implements SerDe {  

    public static CpiProxyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static CpiProxyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var data = SerDeUtil.readbyteVector(4, _data, i);
      i += SerDeUtil.lenVector(4, data);
      final var extraParams = SerDeUtil.readVector(4, ExtraParams.class, ExtraParams::read, _data, i);
      return new CpiProxyIxData(discriminator, data, extraParams);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, data, _data, i);
      i += SerDeUtil.writeVector(4, extraParams, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, data) + SerDeUtil.lenVector(4, extraParams);
    }
  }

  public static final Discriminator EMERGENCY_ACCESS_UPDATE_DISCRIMINATOR = toDiscriminator(207, 247, 157, 14, 87, 132, 230, 0);

  /// Bypasses the timelock for emergency updates on access control rules. Allowed operations:
  /// - removing an integration program
  /// - removing a delegate
  /// - enabling/disabling glam state
  ///
  public static List<AccountMeta> emergencyAccessUpdateKeys(final PublicKey glamStateKey,
                                                            final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  /// Bypasses the timelock for emergency updates on access control rules. Allowed operations:
  /// - removing an integration program
  /// - removing a delegate
  /// - enabling/disabling glam state
  ///
  public static Instruction emergencyAccessUpdate(final AccountMeta invokedGlamProtocolProgramMeta,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamSignerKey,
                                                  final EmergencyAccessUpdateArgs args) {
    final var keys = emergencyAccessUpdateKeys(
      glamStateKey,
      glamSignerKey
    );
    return emergencyAccessUpdate(invokedGlamProtocolProgramMeta, keys, args);
  }

  /// Bypasses the timelock for emergency updates on access control rules. Allowed operations:
  /// - removing an integration program
  /// - removing a delegate
  /// - enabling/disabling glam state
  ///
  public static Instruction emergencyAccessUpdate(final AccountMeta invokedGlamProtocolProgramMeta,
                                                  final List<AccountMeta> keys,
                                                  final EmergencyAccessUpdateArgs args) {
    final byte[] _data = new byte[8 + args.l()];
    int i = EMERGENCY_ACCESS_UPDATE_DISCRIMINATOR.write(_data, 0);
    args.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record EmergencyAccessUpdateIxData(Discriminator discriminator, EmergencyAccessUpdateArgs args) implements SerDe {  

    public static EmergencyAccessUpdateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static EmergencyAccessUpdateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var args = EmergencyAccessUpdateArgs.read(_data, i);
      return new EmergencyAccessUpdateIxData(discriminator, args);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += args.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + args.l();
    }
  }

  public static final Discriminator ENABLE_DISABLE_PROTOCOLS_DISCRIMINATOR = toDiscriminator(222, 198, 164, 163, 194, 161, 11, 171);

  public static List<AccountMeta> enableDisableProtocolsKeys(final PublicKey glamStateKey,
                                                             final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction enableDisableProtocols(final AccountMeta invokedGlamProtocolProgramMeta,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey integrationProgram,
                                                   final int protocolsBitmask,
                                                   final boolean setEnabled) {
    final var keys = enableDisableProtocolsKeys(
      glamStateKey,
      glamSignerKey
    );
    return enableDisableProtocols(
      invokedGlamProtocolProgramMeta,
      keys,
      integrationProgram,
      protocolsBitmask,
      setEnabled
    );
  }

  public static Instruction enableDisableProtocols(final AccountMeta invokedGlamProtocolProgramMeta,
                                                   final List<AccountMeta> keys,
                                                   final PublicKey integrationProgram,
                                                   final int protocolsBitmask,
                                                   final boolean setEnabled) {
    final byte[] _data = new byte[43];
    int i = ENABLE_DISABLE_PROTOCOLS_DISCRIMINATOR.write(_data, 0);
    integrationProgram.write(_data, i);
    i += 32;
    putInt16LE(_data, i, protocolsBitmask);
    i += 2;
    _data[i] = (byte) (setEnabled ? 1 : 0);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record EnableDisableProtocolsIxData(Discriminator discriminator,
                                             PublicKey integrationProgram,
                                             int protocolsBitmask,
                                             boolean setEnabled) implements SerDe {  

    public static EnableDisableProtocolsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 43;

    public static EnableDisableProtocolsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var integrationProgram = readPubKey(_data, i);
      i += 32;
      final var protocolsBitmask = getInt16LE(_data, i);
      i += 2;
      final var setEnabled = _data[i] == 1;
      return new EnableDisableProtocolsIxData(discriminator, integrationProgram, protocolsBitmask, setEnabled);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      integrationProgram.write(_data, i);
      i += 32;
      putInt16LE(_data, i, protocolsBitmask);
      i += 2;
      _data[i] = (byte) (setEnabled ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator EXTEND_STATE_DISCRIMINATOR = toDiscriminator(34, 147, 151, 206, 134, 128, 82, 228);

  public static List<AccountMeta> extendStateKeys(final SolanaAccounts solanaAccounts,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  public static Instruction extendState(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final SolanaAccounts solanaAccounts,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamSignerKey,
                                        final int bytes) {
    final var keys = extendStateKeys(
      solanaAccounts,
      glamStateKey,
      glamSignerKey
    );
    return extendState(invokedGlamProtocolProgramMeta, keys, bytes);
  }

  public static Instruction extendState(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final List<AccountMeta> keys,
                                        final int bytes) {
    final byte[] _data = new byte[12];
    int i = EXTEND_STATE_DISCRIMINATOR.write(_data, 0);
    putInt32LE(_data, i, bytes);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record ExtendStateIxData(Discriminator discriminator, int bytes) implements SerDe {  

    public static ExtendStateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 12;

    public static ExtendStateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var bytes = getInt32LE(_data, i);
      return new ExtendStateIxData(discriminator, bytes);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt32LE(_data, i, bytes);
      i += 4;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator GRANT_REVOKE_DELEGATE_PERMISSIONS_DISCRIMINATOR = toDiscriminator(162, 21, 218, 157, 218, 86, 114, 171);

  public static List<AccountMeta> grantRevokeDelegatePermissionsKeys(final PublicKey glamStateKey,
                                                                     final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction grantRevokeDelegatePermissions(final AccountMeta invokedGlamProtocolProgramMeta,
                                                           final PublicKey glamStateKey,
                                                           final PublicKey glamSignerKey,
                                                           final PublicKey delegate,
                                                           final PublicKey integrationProgram,
                                                           final int protocolBitflag,
                                                           final long permissionsBitmask,
                                                           final boolean setGranted) {
    final var keys = grantRevokeDelegatePermissionsKeys(
      glamStateKey,
      glamSignerKey
    );
    return grantRevokeDelegatePermissions(
      invokedGlamProtocolProgramMeta,
      keys,
      delegate,
      integrationProgram,
      protocolBitflag,
      permissionsBitmask,
      setGranted
    );
  }

  public static Instruction grantRevokeDelegatePermissions(final AccountMeta invokedGlamProtocolProgramMeta,
                                                           final List<AccountMeta> keys,
                                                           final PublicKey delegate,
                                                           final PublicKey integrationProgram,
                                                           final int protocolBitflag,
                                                           final long permissionsBitmask,
                                                           final boolean setGranted) {
    final byte[] _data = new byte[83];
    int i = GRANT_REVOKE_DELEGATE_PERMISSIONS_DISCRIMINATOR.write(_data, 0);
    delegate.write(_data, i);
    i += 32;
    integrationProgram.write(_data, i);
    i += 32;
    putInt16LE(_data, i, protocolBitflag);
    i += 2;
    putInt64LE(_data, i, permissionsBitmask);
    i += 8;
    _data[i] = (byte) (setGranted ? 1 : 0);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record GrantRevokeDelegatePermissionsIxData(Discriminator discriminator,
                                                     PublicKey delegate,
                                                     PublicKey integrationProgram,
                                                     int protocolBitflag,
                                                     long permissionsBitmask,
                                                     boolean setGranted) implements SerDe {  

    public static GrantRevokeDelegatePermissionsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 83;

    public static GrantRevokeDelegatePermissionsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var delegate = readPubKey(_data, i);
      i += 32;
      final var integrationProgram = readPubKey(_data, i);
      i += 32;
      final var protocolBitflag = getInt16LE(_data, i);
      i += 2;
      final var permissionsBitmask = getInt64LE(_data, i);
      i += 8;
      final var setGranted = _data[i] == 1;
      return new GrantRevokeDelegatePermissionsIxData(discriminator,
                                                      delegate,
                                                      integrationProgram,
                                                      protocolBitflag,
                                                      permissionsBitmask,
                                                      setGranted);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      delegate.write(_data, i);
      i += 32;
      integrationProgram.write(_data, i);
      i += 32;
      putInt16LE(_data, i, protocolBitflag);
      i += 2;
      putInt64LE(_data, i, permissionsBitmask);
      i += 8;
      _data[i] = (byte) (setGranted ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator INITIALIZE_STATE_DISCRIMINATOR = toDiscriminator(190, 171, 224, 219, 217, 72, 199, 176);

  public static List<AccountMeta> initializeStateKeys(final SolanaAccounts solanaAccounts,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey baseAssetMintKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createRead(solanaAccounts.systemProgram()),
      createRead(baseAssetMintKey)
    );
  }

  public static Instruction initializeState(final AccountMeta invokedGlamProtocolProgramMeta,
                                            final SolanaAccounts solanaAccounts,
                                            final PublicKey glamStateKey,
                                            final PublicKey glamSignerKey,
                                            final PublicKey baseAssetMintKey,
                                            final StateModel state) {
    final var keys = initializeStateKeys(
      solanaAccounts,
      glamStateKey,
      glamSignerKey,
      baseAssetMintKey
    );
    return initializeState(invokedGlamProtocolProgramMeta, keys, state);
  }

  public static Instruction initializeState(final AccountMeta invokedGlamProtocolProgramMeta,
                                            final List<AccountMeta> keys,
                                            final StateModel state) {
    final byte[] _data = new byte[8 + state.l()];
    int i = INITIALIZE_STATE_DISCRIMINATOR.write(_data, 0);
    state.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record InitializeStateIxData(Discriminator discriminator, StateModel state) implements SerDe {  

    public static InitializeStateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static InitializeStateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var state = StateModel.read(_data, i);
      return new InitializeStateIxData(discriminator, state);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += state.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + state.l();
    }
  }

  public static final Discriminator JUPITER_SWAP_DISCRIMINATOR = toDiscriminator(116, 207, 0, 196, 252, 120, 243, 18);

  public static List<AccountMeta> jupiterSwapKeys(final AccountMeta invokedGlamProtocolProgramMeta,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamVaultKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey cpiProgramKey,
                                                  final PublicKey inputStakePoolKey,
                                                  final PublicKey outputStakePoolKey) {
    return List.of(
      createWrite(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(cpiProgramKey),
      createRead(requireNonNullElse(inputStakePoolKey, invokedGlamProtocolProgramMeta.publicKey())),
      createRead(requireNonNullElse(outputStakePoolKey, invokedGlamProtocolProgramMeta.publicKey()))
    );
  }

  public static Instruction jupiterSwap(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamVaultKey,
                                        final PublicKey glamSignerKey,
                                        final PublicKey cpiProgramKey,
                                        final PublicKey inputStakePoolKey,
                                        final PublicKey outputStakePoolKey,
                                        final byte[] data) {
    final var keys = jupiterSwapKeys(
      invokedGlamProtocolProgramMeta,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      cpiProgramKey,
      inputStakePoolKey,
      outputStakePoolKey
    );
    return jupiterSwap(invokedGlamProtocolProgramMeta, keys, data);
  }

  public static Instruction jupiterSwap(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final List<AccountMeta> keys,
                                        final byte[] data) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, data)];
    int i = JUPITER_SWAP_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeVector(4, data, _data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record JupiterSwapIxData(Discriminator discriminator, byte[] data) implements SerDe {  

    public static JupiterSwapIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static JupiterSwapIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var data = SerDeUtil.readbyteVector(4, _data, i);
      return new JupiterSwapIxData(discriminator, data);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, data, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, data);
    }
  }

  public static final Discriminator LINK_UNLINK_MINT_BY_MINT_AUTHORITY_DISCRIMINATOR = toDiscriminator(85, 67, 58, 245, 175, 14, 122, 6);

  /// For glam mint program's use only
  ///
  public static List<AccountMeta> linkUnlinkMintByMintAuthorityKeys(final PublicKey glamStateKey,
                                                                    final PublicKey glamMintKey,
                                                                    final PublicKey glamMintAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createRead(glamMintKey),
      createReadOnlySigner(glamMintAuthorityKey)
    );
  }

  /// For glam mint program's use only
  ///
  public static Instruction linkUnlinkMintByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                          final PublicKey glamStateKey,
                                                          final PublicKey glamMintKey,
                                                          final PublicKey glamMintAuthorityKey,
                                                          final boolean link) {
    final var keys = linkUnlinkMintByMintAuthorityKeys(
      glamStateKey,
      glamMintKey,
      glamMintAuthorityKey
    );
    return linkUnlinkMintByMintAuthority(invokedGlamProtocolProgramMeta, keys, link);
  }

  /// For glam mint program's use only
  ///
  public static Instruction linkUnlinkMintByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                          final List<AccountMeta> keys,
                                                          final boolean link) {
    final byte[] _data = new byte[9];
    int i = LINK_UNLINK_MINT_BY_MINT_AUTHORITY_DISCRIMINATOR.write(_data, 0);
    _data[i] = (byte) (link ? 1 : 0);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record LinkUnlinkMintByMintAuthorityIxData(Discriminator discriminator, boolean link) implements SerDe {  

    public static LinkUnlinkMintByMintAuthorityIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 9;

    public static LinkUnlinkMintByMintAuthorityIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var link = _data[i] == 1;
      return new LinkUnlinkMintByMintAuthorityIxData(discriminator, link);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      _data[i] = (byte) (link ? 1 : 0);
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator RESET_PRICED_PROTOCOLS_BY_MINT_AUTHORITY_DISCRIMINATOR = toDiscriminator(86, 95, 153, 145, 179, 181, 107, 235);

  /// For glam mint program's use only
  ///
  public static List<AccountMeta> resetPricedProtocolsByMintAuthorityKeys(final PublicKey glamStateKey,
                                                                          final PublicKey glamMintKey,
                                                                          final PublicKey glamMintAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createRead(glamMintKey),
      createReadOnlySigner(glamMintAuthorityKey)
    );
  }

  /// For glam mint program's use only
  ///
  public static Instruction resetPricedProtocolsByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                                final PublicKey glamStateKey,
                                                                final PublicKey glamMintKey,
                                                                final PublicKey glamMintAuthorityKey) {
    final var keys = resetPricedProtocolsByMintAuthorityKeys(
      glamStateKey,
      glamMintKey,
      glamMintAuthorityKey
    );
    return resetPricedProtocolsByMintAuthority(invokedGlamProtocolProgramMeta, keys);
  }

  /// For glam mint program's use only
  ///
  public static Instruction resetPricedProtocolsByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                                final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, RESET_PRICED_PROTOCOLS_BY_MINT_AUTHORITY_DISCRIMINATOR);
  }

  public static final Discriminator SET_JUPITER_SWAP_POLICY_DISCRIMINATOR = toDiscriminator(189, 182, 227, 165, 127, 148, 246, 189);

  public static List<AccountMeta> setJupiterSwapPolicyKeys(final PublicKey glamStateKey,
                                                           final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction setJupiterSwapPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey glamSignerKey,
                                                 final JupiterSwapPolicy policy) {
    final var keys = setJupiterSwapPolicyKeys(
      glamStateKey,
      glamSignerKey
    );
    return setJupiterSwapPolicy(invokedGlamProtocolProgramMeta, keys, policy);
  }

  public static Instruction setJupiterSwapPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                                 final List<AccountMeta> keys,
                                                 final JupiterSwapPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_JUPITER_SWAP_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record SetJupiterSwapPolicyIxData(Discriminator discriminator, JupiterSwapPolicy policy) implements SerDe {  

    public static SetJupiterSwapPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static SetJupiterSwapPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = JupiterSwapPolicy.read(_data, i);
      return new SetJupiterSwapPolicyIxData(discriminator, policy);
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

  public static final Discriminator SET_PROTOCOL_POLICY_DISCRIMINATOR = toDiscriminator(37, 99, 61, 122, 227, 102, 182, 180);

  public static List<AccountMeta> setProtocolPolicyKeys(final PublicKey glamStateKey,
                                                        final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction setProtocolPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                              final PublicKey glamStateKey,
                                              final PublicKey glamSignerKey,
                                              final PublicKey integrationProgram,
                                              final int protocolBitflag,
                                              final byte[] data) {
    final var keys = setProtocolPolicyKeys(
      glamStateKey,
      glamSignerKey
    );
    return setProtocolPolicy(
      invokedGlamProtocolProgramMeta,
      keys,
      integrationProgram,
      protocolBitflag,
      data
    );
  }

  public static Instruction setProtocolPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                              final List<AccountMeta> keys,
                                              final PublicKey integrationProgram,
                                              final int protocolBitflag,
                                              final byte[] data) {
    final byte[] _data = new byte[42 + SerDeUtil.lenVector(4, data)];
    int i = SET_PROTOCOL_POLICY_DISCRIMINATOR.write(_data, 0);
    integrationProgram.write(_data, i);
    i += 32;
    putInt16LE(_data, i, protocolBitflag);
    i += 2;
    SerDeUtil.writeVector(4, data, _data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record SetProtocolPolicyIxData(Discriminator discriminator,
                                        PublicKey integrationProgram,
                                        int protocolBitflag,
                                        byte[] data) implements SerDe {  

    public static SetProtocolPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static SetProtocolPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var integrationProgram = readPubKey(_data, i);
      i += 32;
      final var protocolBitflag = getInt16LE(_data, i);
      i += 2;
      final var data = SerDeUtil.readbyteVector(4, _data, i);
      return new SetProtocolPolicyIxData(discriminator, integrationProgram, protocolBitflag, data);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      integrationProgram.write(_data, i);
      i += 32;
      putInt16LE(_data, i, protocolBitflag);
      i += 2;
      i += SerDeUtil.writeVector(4, data, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + 32 + 2 + SerDeUtil.lenVector(4, data);
    }
  }

  public static final Discriminator SET_SYSTEM_TRANSFER_POLICY_DISCRIMINATOR = toDiscriminator(102, 21, 157, 101, 19, 4, 100, 213);

  public static List<AccountMeta> setSystemTransferPolicyKeys(final PublicKey glamStateKey,
                                                              final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction setSystemTransferPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                                    final PublicKey glamStateKey,
                                                    final PublicKey glamSignerKey,
                                                    final TransferPolicy policy) {
    final var keys = setSystemTransferPolicyKeys(
      glamStateKey,
      glamSignerKey
    );
    return setSystemTransferPolicy(invokedGlamProtocolProgramMeta, keys, policy);
  }

  public static Instruction setSystemTransferPolicy(final AccountMeta invokedGlamProtocolProgramMeta,
                                                    final List<AccountMeta> keys,
                                                    final TransferPolicy policy) {
    final byte[] _data = new byte[8 + policy.l()];
    int i = SET_SYSTEM_TRANSFER_POLICY_DISCRIMINATOR.write(_data, 0);
    policy.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record SetSystemTransferPolicyIxData(Discriminator discriminator, TransferPolicy policy) implements SerDe {  

    public static SetSystemTransferPolicyIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static SetSystemTransferPolicyIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var policy = TransferPolicy.read(_data, i);
      return new SetSystemTransferPolicyIxData(discriminator, policy);
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

  public static final Discriminator SYSTEM_TRANSFER_DISCRIMINATOR = toDiscriminator(167, 164, 195, 155, 219, 152, 191, 230);

  /// Transfers SOL from the vault to another account.
  /// 
  /// Token program ID is required as a remaining account when wrapping SOL (i.e., transfer to wSOL token account).
  ///
  public static List<AccountMeta> systemTransferKeys(final SolanaAccounts solanaAccounts,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamVaultKey,
                                                     final PublicKey glamSignerKey,
                                                     final PublicKey toKey) {
    return List.of(
      createRead(glamStateKey),
      createWrite(glamVaultKey),
      createWritableSigner(glamSignerKey),
      createRead(solanaAccounts.systemProgram()),
      createWrite(toKey)
    );
  }

  /// Transfers SOL from the vault to another account.
  /// 
  /// Token program ID is required as a remaining account when wrapping SOL (i.e., transfer to wSOL token account).
  ///
  public static Instruction systemTransfer(final AccountMeta invokedGlamProtocolProgramMeta,
                                           final SolanaAccounts solanaAccounts,
                                           final PublicKey glamStateKey,
                                           final PublicKey glamVaultKey,
                                           final PublicKey glamSignerKey,
                                           final PublicKey toKey,
                                           final long lamports) {
    final var keys = systemTransferKeys(
      solanaAccounts,
      glamStateKey,
      glamVaultKey,
      glamSignerKey,
      toKey
    );
    return systemTransfer(invokedGlamProtocolProgramMeta, keys, lamports);
  }

  /// Transfers SOL from the vault to another account.
  /// 
  /// Token program ID is required as a remaining account when wrapping SOL (i.e., transfer to wSOL token account).
  ///
  public static Instruction systemTransfer(final AccountMeta invokedGlamProtocolProgramMeta,
                                           final List<AccountMeta> keys,
                                           final long lamports) {
    final byte[] _data = new byte[16];
    int i = SYSTEM_TRANSFER_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, lamports);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record SystemTransferIxData(Discriminator discriminator, long lamports) implements SerDe {  

    public static SystemTransferIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 16;

    public static SystemTransferIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var lamports = getInt64LE(_data, i);
      return new SystemTransferIxData(discriminator, lamports);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, lamports);
      i += 8;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator TOKEN_TRANSFER_CHECKED_BY_MINT_AUTHORITY_DISCRIMINATOR = toDiscriminator(37, 131, 188, 85, 45, 183, 8, 81);

  /// For glam mint program's use only
  ///
  public static List<AccountMeta> tokenTransferCheckedByMintAuthorityKeys(final PublicKey glamStateKey,
                                                                          final PublicKey glamVaultKey,
                                                                          final PublicKey glamMintKey,
                                                                          final PublicKey glamMintAuthorityKey,
                                                                          final PublicKey fromKey,
                                                                          final PublicKey toKey,
                                                                          final PublicKey mintKey,
                                                                          final PublicKey tokenProgramKey) {
    return List.of(
      createRead(glamStateKey),
      createWrite(glamVaultKey),
      createWrite(glamMintKey),
      createWritableSigner(glamMintAuthorityKey),
      createWrite(fromKey),
      createWrite(toKey),
      createRead(mintKey),
      createRead(tokenProgramKey)
    );
  }

  /// For glam mint program's use only
  ///
  public static Instruction tokenTransferCheckedByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                                final PublicKey glamStateKey,
                                                                final PublicKey glamVaultKey,
                                                                final PublicKey glamMintKey,
                                                                final PublicKey glamMintAuthorityKey,
                                                                final PublicKey fromKey,
                                                                final PublicKey toKey,
                                                                final PublicKey mintKey,
                                                                final PublicKey tokenProgramKey,
                                                                final long amount,
                                                                final int decimals) {
    final var keys = tokenTransferCheckedByMintAuthorityKeys(
      glamStateKey,
      glamVaultKey,
      glamMintKey,
      glamMintAuthorityKey,
      fromKey,
      toKey,
      mintKey,
      tokenProgramKey
    );
    return tokenTransferCheckedByMintAuthority(invokedGlamProtocolProgramMeta, keys, amount, decimals);
  }

  /// For glam mint program's use only
  ///
  public static Instruction tokenTransferCheckedByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                                final List<AccountMeta> keys,
                                                                final long amount,
                                                                final int decimals) {
    final byte[] _data = new byte[17];
    int i = TOKEN_TRANSFER_CHECKED_BY_MINT_AUTHORITY_DISCRIMINATOR.write(_data, 0);
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) decimals;

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record TokenTransferCheckedByMintAuthorityIxData(Discriminator discriminator, long amount, int decimals) implements SerDe {  

    public static TokenTransferCheckedByMintAuthorityIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 17;

    public static TokenTransferCheckedByMintAuthorityIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var amount = getInt64LE(_data, i);
      i += 8;
      final var decimals = _data[i] & 0xFF;
      return new TokenTransferCheckedByMintAuthorityIxData(discriminator, amount, decimals);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      putInt64LE(_data, i, amount);
      i += 8;
      _data[i] = (byte) decimals;
      ++i;
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPDATE_MINT_PARAMS_DISCRIMINATOR = toDiscriminator(45, 42, 115, 25, 179, 27, 57, 191);

  public static List<AccountMeta> updateMintParamsKeys(final PublicKey glamStateKey,
                                                       final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction updateMintParams(final AccountMeta invokedGlamProtocolProgramMeta,
                                             final PublicKey glamStateKey,
                                             final PublicKey glamSignerKey,
                                             final EngineField[] params) {
    final var keys = updateMintParamsKeys(
      glamStateKey,
      glamSignerKey
    );
    return updateMintParams(invokedGlamProtocolProgramMeta, keys, params);
  }

  public static Instruction updateMintParams(final AccountMeta invokedGlamProtocolProgramMeta,
                                             final List<AccountMeta> keys,
                                             final EngineField[] params) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, params)];
    int i = UPDATE_MINT_PARAMS_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeVector(4, params, _data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record UpdateMintParamsIxData(Discriminator discriminator, EngineField[] params) implements SerDe {  

    public static UpdateMintParamsIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static UpdateMintParamsIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = SerDeUtil.readVector(4, EngineField.class, EngineField::read, _data, i);
      return new UpdateMintParamsIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, params, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, params);
    }
  }

  public static final Discriminator UPDATE_MINT_PARAMS_BY_MINT_AUTHORITY_DISCRIMINATOR = toDiscriminator(94, 160, 55, 53, 175, 225, 62, 118);

  /// For glam mint program's use only, timelock is not enforced
  ///
  public static List<AccountMeta> updateMintParamsByMintAuthorityKeys(final PublicKey glamStateKey,
                                                                      final PublicKey glamMintKey,
                                                                      final PublicKey glamMintAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createRead(glamMintKey),
      createReadOnlySigner(glamMintAuthorityKey)
    );
  }

  /// For glam mint program's use only, timelock is not enforced
  ///
  public static Instruction updateMintParamsByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                            final PublicKey glamStateKey,
                                                            final PublicKey glamMintKey,
                                                            final PublicKey glamMintAuthorityKey,
                                                            final EngineField[] params) {
    final var keys = updateMintParamsByMintAuthorityKeys(
      glamStateKey,
      glamMintKey,
      glamMintAuthorityKey
    );
    return updateMintParamsByMintAuthority(invokedGlamProtocolProgramMeta, keys, params);
  }

  /// For glam mint program's use only, timelock is not enforced
  ///
  public static Instruction updateMintParamsByMintAuthority(final AccountMeta invokedGlamProtocolProgramMeta,
                                                            final List<AccountMeta> keys,
                                                            final EngineField[] params) {
    final byte[] _data = new byte[8 + SerDeUtil.lenVector(4, params)];
    int i = UPDATE_MINT_PARAMS_BY_MINT_AUTHORITY_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeVector(4, params, _data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record UpdateMintParamsByMintAuthorityIxData(Discriminator discriminator, EngineField[] params) implements SerDe {  

    public static UpdateMintParamsByMintAuthorityIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static UpdateMintParamsByMintAuthorityIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var params = SerDeUtil.readVector(4, EngineField.class, EngineField::read, _data, i);
      return new UpdateMintParamsByMintAuthorityIxData(discriminator, params);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeVector(4, params, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenVector(4, params);
    }
  }

  public static final Discriminator UPDATE_PRICED_PROTOCOL_DISCRIMINATOR = toDiscriminator(10, 106, 94, 171, 118, 217, 62, 98);

  /// Only accessible by integration programs
  ///
  public static List<AccountMeta> updatePricedProtocolKeys(final PublicKey glamStateKey,
                                                           final PublicKey integrationAuthorityKey) {
    return List.of(
      createWrite(glamStateKey),
      createReadOnlySigner(integrationAuthorityKey)
    );
  }

  /// Only accessible by integration programs
  ///
  public static Instruction updatePricedProtocol(final AccountMeta invokedGlamProtocolProgramMeta,
                                                 final PublicKey glamStateKey,
                                                 final PublicKey integrationAuthorityKey,
                                                 final PricedProtocol pricedProtocol) {
    final var keys = updatePricedProtocolKeys(
      glamStateKey,
      integrationAuthorityKey
    );
    return updatePricedProtocol(invokedGlamProtocolProgramMeta, keys, pricedProtocol);
  }

  /// Only accessible by integration programs
  ///
  public static Instruction updatePricedProtocol(final AccountMeta invokedGlamProtocolProgramMeta,
                                                 final List<AccountMeta> keys,
                                                 final PricedProtocol pricedProtocol) {
    final byte[] _data = new byte[8 + pricedProtocol.l()];
    int i = UPDATE_PRICED_PROTOCOL_DISCRIMINATOR.write(_data, 0);
    pricedProtocol.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record UpdatePricedProtocolIxData(Discriminator discriminator, PricedProtocol pricedProtocol) implements SerDe {  

    public static UpdatePricedProtocolIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static UpdatePricedProtocolIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var pricedProtocol = PricedProtocol.read(_data, i);
      return new UpdatePricedProtocolIxData(discriminator, pricedProtocol);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += pricedProtocol.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + pricedProtocol.l();
    }
  }

  public static final Discriminator UPDATE_STATE_DISCRIMINATOR = toDiscriminator(135, 112, 215, 75, 247, 185, 53, 176);

  public static List<AccountMeta> updateStateKeys(final PublicKey glamStateKey,
                                                  final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction updateState(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final PublicKey glamStateKey,
                                        final PublicKey glamSignerKey,
                                        final StateModel state) {
    final var keys = updateStateKeys(
      glamStateKey,
      glamSignerKey
    );
    return updateState(invokedGlamProtocolProgramMeta, keys, state);
  }

  public static Instruction updateState(final AccountMeta invokedGlamProtocolProgramMeta,
                                        final List<AccountMeta> keys,
                                        final StateModel state) {
    final byte[] _data = new byte[8 + state.l()];
    int i = UPDATE_STATE_DISCRIMINATOR.write(_data, 0);
    state.write(_data, i);

    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, _data);
  }

  public record UpdateStateIxData(Discriminator discriminator, StateModel state) implements SerDe {  

    public static UpdateStateIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static UpdateStateIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var state = StateModel.read(_data, i);
      return new UpdateStateIxData(discriminator, state);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += state.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + state.l();
    }
  }

  public static final Discriminator UPDATE_STATE_APPLY_TIMELOCK_DISCRIMINATOR = toDiscriminator(66, 12, 138, 80, 133, 85, 46, 220);

  public static List<AccountMeta> updateStateApplyTimelockKeys(final PublicKey glamStateKey,
                                                               final PublicKey glamSignerKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey)
    );
  }

  public static Instruction updateStateApplyTimelock(final AccountMeta invokedGlamProtocolProgramMeta,
                                                     final PublicKey glamStateKey,
                                                     final PublicKey glamSignerKey) {
    final var keys = updateStateApplyTimelockKeys(
      glamStateKey,
      glamSignerKey
    );
    return updateStateApplyTimelock(invokedGlamProtocolProgramMeta, keys);
  }

  public static Instruction updateStateApplyTimelock(final AccountMeta invokedGlamProtocolProgramMeta,
                                                     final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedGlamProtocolProgramMeta, keys, UPDATE_STATE_APPLY_TIMELOCK_DISCRIMINATOR);
  }

  private GlamProtocolProgram() {
  }
}

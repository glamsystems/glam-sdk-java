package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types.ExternalPositionConfig;
import systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types.PositionObservationInput;

import java.math.BigInteger;
import java.util.List;

import static software.sava.core.accounts.meta.AccountMeta.*;
import static software.sava.core.encoding.ByteUtil.getInt128LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public final class ExtEpiProgram {

  public static final Discriminator REFRESH_PRICED_PROTOCOL_DISCRIMINATOR = toDiscriminator(229, 89, 39, 46, 5, 217, 10, 192);

  /// Republish the aggregate EPI priced protocol from already validated
  /// observations at the current slot.
  ///
  public static List<AccountMeta> refreshPricedProtocolKeys(final PublicKey glamStateKey,
                                                            final PublicKey glamSignerKey,
                                                            final PublicKey observationStateKey,
                                                            final PublicKey integrationAuthorityKey,
                                                            final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createReadOnlySigner(glamSignerKey),
      createRead(observationStateKey),
      createRead(integrationAuthorityKey),
      createRead(glamProtocolProgramKey)
    );
  }

  /// Republish the aggregate EPI priced protocol from already validated
  /// observations at the current slot.
  ///
  public static Instruction refreshPricedProtocol(final AccountMeta invokedExtEpiProgramMeta,
                                                  final PublicKey glamStateKey,
                                                  final PublicKey glamSignerKey,
                                                  final PublicKey observationStateKey,
                                                  final PublicKey integrationAuthorityKey,
                                                  final PublicKey glamProtocolProgramKey) {
    final var keys = refreshPricedProtocolKeys(
      glamStateKey,
      glamSignerKey,
      observationStateKey,
      integrationAuthorityKey,
      glamProtocolProgramKey
    );
    return refreshPricedProtocol(invokedExtEpiProgramMeta, keys);
  }

  /// Republish the aggregate EPI priced protocol from already validated
  /// observations at the current slot.
  ///
  public static Instruction refreshPricedProtocol(final AccountMeta invokedExtEpiProgramMeta,
                                                  final List<AccountMeta> keys) {
    return Instruction.createInstruction(invokedExtEpiProgramMeta, keys, REFRESH_PRICED_PROTOCOL_DISCRIMINATOR);
  }

  public static final Discriminator REMOVE_EXTERNAL_POSITION_DISCRIMINATOR = toDiscriminator(97, 146, 246, 241, 130, 26, 108, 97);

  /// Remove an external position from the registry.
  /// Closes the observation state PDA and refunds rent to the signer.
  ///
  public static List<AccountMeta> removeExternalPositionKeys(final SolanaAccounts solanaAccounts,
                                                             final PublicKey glamStateKey,
                                                             final PublicKey glamSignerKey,
                                                             final PublicKey glamVaultKey,
                                                             final PublicKey observationStateKey,
                                                             final PublicKey integrationAuthorityKey,
                                                             final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createWrite(glamVaultKey),
      createWrite(observationStateKey),
      createRead(integrationAuthorityKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  /// Remove an external position from the registry.
  /// Closes the observation state PDA and refunds rent to the signer.
  ///
  public static Instruction removeExternalPosition(final AccountMeta invokedExtEpiProgramMeta,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey observationStateKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final byte[] positionId) {
    final var keys = removeExternalPositionKeys(
      solanaAccounts,
      glamStateKey,
      glamSignerKey,
      glamVaultKey,
      observationStateKey,
      integrationAuthorityKey,
      glamProtocolProgramKey
    );
    return removeExternalPosition(invokedExtEpiProgramMeta, keys, positionId);
  }

  /// Remove an external position from the registry.
  /// Closes the observation state PDA and refunds rent to the signer.
  ///
  public static Instruction removeExternalPosition(final AccountMeta invokedExtEpiProgramMeta,
                                                   final List<AccountMeta> keys,
                                                   final byte[] positionId) {
    final byte[] _data = new byte[8 + SerDeUtil.lenArray(positionId)];
    int i = REMOVE_EXTERNAL_POSITION_DISCRIMINATOR.write(_data, 0);
    SerDeUtil.writeArrayChecked(positionId, 32, _data, i);

    return Instruction.createInstruction(invokedExtEpiProgramMeta, keys, _data);
  }

  public record RemoveExternalPositionIxData(Discriminator discriminator, byte[] positionId) implements SerDe {  

    public static RemoveExternalPositionIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 40;
    public static final int POSITION_ID_LEN = 32;

    public static final int POSITION_ID_OFFSET = 8;

    public static RemoveExternalPositionIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var positionId = new byte[32];
      SerDeUtil.readArray(positionId, _data, i);
      return new RemoveExternalPositionIxData(discriminator, positionId);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator SUBMIT_EXTERNAL_OBSERVATION_DISCRIMINATOR = toDiscriminator(100, 228, 147, 149, 22, 28, 158, 163);

  /// Submit an observation for a configured external position.
  /// Writes to pending slot; replaces any existing pending observation.
  ///
  public static List<AccountMeta> submitExternalObservationKeys(final PublicKey glamStateKey,
                                                                final PublicKey glamSignerKey,
                                                                final PublicKey observationStateKey) {
    return List.of(
      createRead(glamStateKey),
      createReadOnlySigner(glamSignerKey),
      createWrite(observationStateKey)
    );
  }

  /// Submit an observation for a configured external position.
  /// Writes to pending slot; replaces any existing pending observation.
  ///
  public static Instruction submitExternalObservation(final AccountMeta invokedExtEpiProgramMeta,
                                                      final PublicKey glamStateKey,
                                                      final PublicKey glamSignerKey,
                                                      final PublicKey observationStateKey,
                                                      final PositionObservationInput input) {
    final var keys = submitExternalObservationKeys(
      glamStateKey,
      glamSignerKey,
      observationStateKey
    );
    return submitExternalObservation(invokedExtEpiProgramMeta, keys, input);
  }

  /// Submit an observation for a configured external position.
  /// Writes to pending slot; replaces any existing pending observation.
  ///
  public static Instruction submitExternalObservation(final AccountMeta invokedExtEpiProgramMeta,
                                                      final List<AccountMeta> keys,
                                                      final PositionObservationInput input) {
    final byte[] _data = new byte[8 + input.l()];
    int i = SUBMIT_EXTERNAL_OBSERVATION_DISCRIMINATOR.write(_data, 0);
    input.write(_data, i);

    return Instruction.createInstruction(invokedExtEpiProgramMeta, keys, _data);
  }

  public record SubmitExternalObservationIxData(Discriminator discriminator, PositionObservationInput input) implements SerDe {  

    public static SubmitExternalObservationIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int BYTES = 233;

    public static final int INPUT_OFFSET = 8;

    public static SubmitExternalObservationIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var input = PositionObservationInput.read(_data, i);
      return new SubmitExternalObservationIxData(discriminator, input);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += input.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return BYTES;
    }
  }

  public static final Discriminator UPSERT_EXTERNAL_POSITION_DISCRIMINATOR = toDiscriminator(199, 33, 239, 150, 200, 123, 43, 70);

  /// Create or update an external position configuration.
  /// Creates the observation state PDA on first call for a position.
  ///
  public static List<AccountMeta> upsertExternalPositionKeys(final SolanaAccounts solanaAccounts,
                                                             final PublicKey glamStateKey,
                                                             final PublicKey glamSignerKey,
                                                             final PublicKey glamVaultKey,
                                                             final PublicKey observationStateKey,
                                                             final PublicKey integrationAuthorityKey,
                                                             final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createWritableSigner(glamSignerKey),
      createWrite(glamVaultKey),
      createWrite(observationStateKey),
      createRead(integrationAuthorityKey),
      createRead(glamProtocolProgramKey),
      createRead(solanaAccounts.systemProgram())
    );
  }

  /// Create or update an external position configuration.
  /// Creates the observation state PDA on first call for a position.
  ///
  public static Instruction upsertExternalPosition(final AccountMeta invokedExtEpiProgramMeta,
                                                   final SolanaAccounts solanaAccounts,
                                                   final PublicKey glamStateKey,
                                                   final PublicKey glamSignerKey,
                                                   final PublicKey glamVaultKey,
                                                   final PublicKey observationStateKey,
                                                   final PublicKey integrationAuthorityKey,
                                                   final PublicKey glamProtocolProgramKey,
                                                   final ExternalPositionConfig config) {
    final var keys = upsertExternalPositionKeys(
      solanaAccounts,
      glamStateKey,
      glamSignerKey,
      glamVaultKey,
      observationStateKey,
      integrationAuthorityKey,
      glamProtocolProgramKey
    );
    return upsertExternalPosition(invokedExtEpiProgramMeta, keys, config);
  }

  /// Create or update an external position configuration.
  /// Creates the observation state PDA on first call for a position.
  ///
  public static Instruction upsertExternalPosition(final AccountMeta invokedExtEpiProgramMeta,
                                                   final List<AccountMeta> keys,
                                                   final ExternalPositionConfig config) {
    final byte[] _data = new byte[8 + config.l()];
    int i = UPSERT_EXTERNAL_POSITION_DISCRIMINATOR.write(_data, 0);
    config.write(_data, i);

    return Instruction.createInstruction(invokedExtEpiProgramMeta, keys, _data);
  }

  public record UpsertExternalPositionIxData(Discriminator discriminator, ExternalPositionConfig config) implements SerDe {  

    public static UpsertExternalPositionIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int CONFIG_OFFSET = 8;

    public static UpsertExternalPositionIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var config = ExternalPositionConfig.read(_data, i);
      return new UpsertExternalPositionIxData(discriminator, config);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += config.write(_data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + config.l();
    }
  }

  public static final Discriminator VALIDATE_EXTERNAL_OBSERVATION_DISCRIMINATOR = toDiscriminator(88, 144, 219, 126, 79, 29, 43, 188);

  /// Validate a pending observation, promote to active, and publish
  /// the full aggregate priced protocol for ext_epi.
  /// 
  /// All position observations are stored in the single observation state PDA,
  /// so no remaining_accounts are needed.
  ///
  public static List<AccountMeta> validateExternalObservationKeys(final PublicKey glamStateKey,
                                                                  final PublicKey glamSignerKey,
                                                                  final PublicKey observationStateKey,
                                                                  final PublicKey integrationAuthorityKey,
                                                                  final PublicKey glamProtocolProgramKey) {
    return List.of(
      createWrite(glamStateKey),
      createReadOnlySigner(glamSignerKey),
      createWrite(observationStateKey),
      createRead(integrationAuthorityKey),
      createRead(glamProtocolProgramKey)
    );
  }

  /// Validate a pending observation, promote to active, and publish
  /// the full aggregate priced protocol for ext_epi.
  /// 
  /// All position observations are stored in the single observation state PDA,
  /// so no remaining_accounts are needed.
  ///
  public static Instruction validateExternalObservation(final AccountMeta invokedExtEpiProgramMeta,
                                                        final PublicKey glamStateKey,
                                                        final PublicKey glamSignerKey,
                                                        final PublicKey observationStateKey,
                                                        final PublicKey integrationAuthorityKey,
                                                        final PublicKey glamProtocolProgramKey,
                                                        final byte[] positionId,
                                                        final BigInteger normalizedBaseAssetAmount) {
    final var keys = validateExternalObservationKeys(
      glamStateKey,
      glamSignerKey,
      observationStateKey,
      integrationAuthorityKey,
      glamProtocolProgramKey
    );
    return validateExternalObservation(invokedExtEpiProgramMeta, keys, positionId, normalizedBaseAssetAmount);
  }

  /// Validate a pending observation, promote to active, and publish
  /// the full aggregate priced protocol for ext_epi.
  /// 
  /// All position observations are stored in the single observation state PDA,
  /// so no remaining_accounts are needed.
  ///
  public static Instruction validateExternalObservation(final AccountMeta invokedExtEpiProgramMeta,
                                                        final List<AccountMeta> keys,
                                                        final byte[] positionId,
                                                        final BigInteger normalizedBaseAssetAmount) {
    final byte[] _data = new byte[
    8 + SerDeUtil.lenArray(positionId)
    + (normalizedBaseAssetAmount == null ? 1 : 17)
    ];
    int i = VALIDATE_EXTERNAL_OBSERVATION_DISCRIMINATOR.write(_data, 0);
    i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
    SerDeUtil.write128Optional(1, normalizedBaseAssetAmount, _data, i);

    return Instruction.createInstruction(invokedExtEpiProgramMeta, keys, _data);
  }

  public record ValidateExternalObservationIxData(Discriminator discriminator, byte[] positionId, BigInteger normalizedBaseAssetAmount) implements SerDe {  

    public static ValidateExternalObservationIxData read(final Instruction instruction) {
      return read(instruction.data(), instruction.offset());
    }

    public static final int POSITION_ID_LEN = 32;
    public static final int POSITION_ID_OFFSET = 8;
    public static final int NORMALIZED_BASE_ASSET_AMOUNT_OFFSET = 41;

    public static ValidateExternalObservationIxData read(final byte[] _data, final int _offset) {
      if (_data == null || _data.length == 0) {
        return null;
      }
      final var discriminator = createAnchorDiscriminator(_data, _offset);
      int i = _offset + discriminator.length();
      final var positionId = new byte[32];
      i += SerDeUtil.readArray(positionId, _data, i);
      final BigInteger normalizedBaseAssetAmount;
      if (SerDeUtil.isAbsent(1, _data, i)) {
        normalizedBaseAssetAmount = null;
      } else {
        ++i;
        normalizedBaseAssetAmount = getInt128LE(_data, i);
      }
      return new ValidateExternalObservationIxData(discriminator, positionId, normalizedBaseAssetAmount);
    }

    @Override
    public int write(final byte[] _data, final int _offset) {
      int i = _offset + discriminator.write(_data, _offset);
      i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
      i += SerDeUtil.write128Optional(1, normalizedBaseAssetAmount, _data, i);
      return i - _offset;
    }

    @Override
    public int l() {
      return 8 + SerDeUtil.lenArray(positionId) + (normalizedBaseAssetAmount == null ? 1 : (1 + 16));
    }
  }

  private ExtEpiProgram() {
  }
}

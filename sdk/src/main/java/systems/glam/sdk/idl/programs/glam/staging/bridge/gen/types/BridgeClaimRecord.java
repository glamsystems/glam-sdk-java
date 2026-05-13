package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.function.BiFunction;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.*;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public record BridgeClaimRecord(PublicKey _address,
                                Discriminator discriminator,
                                PublicKey glamState,
                                int protocol,
                                PublicKey providerProgram,
                                PublicKey providerConfig,
                                PublicKey providerReceipt,
                                PublicKey destinationMint,
                                PublicKey destinationTokenAccount,
                                PublicKey destinationRecipient,
                                long amount,
                                int sourceChain,
                                byte[] providerTransferId,
                                PublicKey providerEmitter,
                                long providerSequence,
                                long claimedSlot,
                                int bump) implements SerDe {

  public static final int BYTES = 325;
  public static final int PROVIDER_TRANSFER_ID_LEN = 32;
  public static final Filter SIZE_FILTER = Filter.createDataSizeFilter(BYTES);

  public static final Discriminator DISCRIMINATOR = toDiscriminator(124, 229, 186, 198, 167, 174, 108, 89);
  public static final Filter DISCRIMINATOR_FILTER = Filter.createMemCompFilter(0, DISCRIMINATOR.data());

  public static final int GLAM_STATE_OFFSET = 8;
  public static final int PROTOCOL_OFFSET = 40;
  public static final int PROVIDER_PROGRAM_OFFSET = 42;
  public static final int PROVIDER_CONFIG_OFFSET = 74;
  public static final int PROVIDER_RECEIPT_OFFSET = 106;
  public static final int DESTINATION_MINT_OFFSET = 138;
  public static final int DESTINATION_TOKEN_ACCOUNT_OFFSET = 170;
  public static final int DESTINATION_RECIPIENT_OFFSET = 202;
  public static final int AMOUNT_OFFSET = 234;
  public static final int SOURCE_CHAIN_OFFSET = 242;
  public static final int PROVIDER_TRANSFER_ID_OFFSET = 244;
  public static final int PROVIDER_EMITTER_OFFSET = 276;
  public static final int PROVIDER_SEQUENCE_OFFSET = 308;
  public static final int CLAIMED_SLOT_OFFSET = 316;
  public static final int BUMP_OFFSET = 324;

  public static Filter createGlamStateFilter(final PublicKey glamState) {
    return Filter.createMemCompFilter(GLAM_STATE_OFFSET, glamState);
  }

  public static Filter createProtocolFilter(final int protocol) {
    final byte[] _data = new byte[2];
    putInt16LE(_data, 0, protocol);
    return Filter.createMemCompFilter(PROTOCOL_OFFSET, _data);
  }

  public static Filter createProviderProgramFilter(final PublicKey providerProgram) {
    return Filter.createMemCompFilter(PROVIDER_PROGRAM_OFFSET, providerProgram);
  }

  public static Filter createProviderConfigFilter(final PublicKey providerConfig) {
    return Filter.createMemCompFilter(PROVIDER_CONFIG_OFFSET, providerConfig);
  }

  public static Filter createProviderReceiptFilter(final PublicKey providerReceipt) {
    return Filter.createMemCompFilter(PROVIDER_RECEIPT_OFFSET, providerReceipt);
  }

  public static Filter createDestinationMintFilter(final PublicKey destinationMint) {
    return Filter.createMemCompFilter(DESTINATION_MINT_OFFSET, destinationMint);
  }

  public static Filter createDestinationTokenAccountFilter(final PublicKey destinationTokenAccount) {
    return Filter.createMemCompFilter(DESTINATION_TOKEN_ACCOUNT_OFFSET, destinationTokenAccount);
  }

  public static Filter createDestinationRecipientFilter(final PublicKey destinationRecipient) {
    return Filter.createMemCompFilter(DESTINATION_RECIPIENT_OFFSET, destinationRecipient);
  }

  public static Filter createAmountFilter(final long amount) {
    final byte[] _data = new byte[8];
    putInt64LE(_data, 0, amount);
    return Filter.createMemCompFilter(AMOUNT_OFFSET, _data);
  }

  public static Filter createSourceChainFilter(final int sourceChain) {
    final byte[] _data = new byte[2];
    putInt16LE(_data, 0, sourceChain);
    return Filter.createMemCompFilter(SOURCE_CHAIN_OFFSET, _data);
  }

  public static Filter createProviderEmitterFilter(final PublicKey providerEmitter) {
    return Filter.createMemCompFilter(PROVIDER_EMITTER_OFFSET, providerEmitter);
  }

  public static Filter createProviderSequenceFilter(final long providerSequence) {
    final byte[] _data = new byte[8];
    putInt64LE(_data, 0, providerSequence);
    return Filter.createMemCompFilter(PROVIDER_SEQUENCE_OFFSET, _data);
  }

  public static Filter createClaimedSlotFilter(final long claimedSlot) {
    final byte[] _data = new byte[8];
    putInt64LE(_data, 0, claimedSlot);
    return Filter.createMemCompFilter(CLAIMED_SLOT_OFFSET, _data);
  }

  public static Filter createBumpFilter(final int bump) {
    return Filter.createMemCompFilter(BUMP_OFFSET, new byte[]{(byte) bump});
  }

  public static BridgeClaimRecord read(final byte[] _data, final int _offset) {
    return read(null, _data, _offset);
  }

  public static BridgeClaimRecord read(final AccountInfo<byte[]> accountInfo) {
    return read(accountInfo.pubKey(), accountInfo.data(), 0);
  }

  public static BridgeClaimRecord read(final PublicKey _address, final byte[] _data) {
    return read(_address, _data, 0);
  }

  public static final BiFunction<PublicKey, byte[], BridgeClaimRecord> FACTORY = BridgeClaimRecord::read;

  public static BridgeClaimRecord read(final PublicKey _address, final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var discriminator = createAnchorDiscriminator(_data, _offset);
    int i = _offset + discriminator.length();
    final var glamState = readPubKey(_data, i);
    i += 32;
    final var protocol = getInt16LE(_data, i);
    i += 2;
    final var providerProgram = readPubKey(_data, i);
    i += 32;
    final var providerConfig = readPubKey(_data, i);
    i += 32;
    final var providerReceipt = readPubKey(_data, i);
    i += 32;
    final var destinationMint = readPubKey(_data, i);
    i += 32;
    final var destinationTokenAccount = readPubKey(_data, i);
    i += 32;
    final var destinationRecipient = readPubKey(_data, i);
    i += 32;
    final var amount = getInt64LE(_data, i);
    i += 8;
    final var sourceChain = getInt16LE(_data, i);
    i += 2;
    final var providerTransferId = new byte[32];
    i += SerDeUtil.readArray(providerTransferId, _data, i);
    final var providerEmitter = readPubKey(_data, i);
    i += 32;
    final var providerSequence = getInt64LE(_data, i);
    i += 8;
    final var claimedSlot = getInt64LE(_data, i);
    i += 8;
    final var bump = _data[i] & 0xFF;
    return new BridgeClaimRecord(_address,
                                 discriminator,
                                 glamState,
                                 protocol,
                                 providerProgram,
                                 providerConfig,
                                 providerReceipt,
                                 destinationMint,
                                 destinationTokenAccount,
                                 destinationRecipient,
                                 amount,
                                 sourceChain,
                                 providerTransferId,
                                 providerEmitter,
                                 providerSequence,
                                 claimedSlot,
                                 bump);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset + discriminator.write(_data, _offset);
    glamState.write(_data, i);
    i += 32;
    putInt16LE(_data, i, protocol);
    i += 2;
    providerProgram.write(_data, i);
    i += 32;
    providerConfig.write(_data, i);
    i += 32;
    providerReceipt.write(_data, i);
    i += 32;
    destinationMint.write(_data, i);
    i += 32;
    destinationTokenAccount.write(_data, i);
    i += 32;
    destinationRecipient.write(_data, i);
    i += 32;
    putInt64LE(_data, i, amount);
    i += 8;
    putInt16LE(_data, i, sourceChain);
    i += 2;
    i += SerDeUtil.writeArrayChecked(providerTransferId, 32, _data, i);
    providerEmitter.write(_data, i);
    i += 32;
    putInt64LE(_data, i, providerSequence);
    i += 8;
    putInt64LE(_data, i, claimedSlot);
    i += 8;
    _data[i] = (byte) bump;
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}

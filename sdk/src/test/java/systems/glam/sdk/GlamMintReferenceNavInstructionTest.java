package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.sava.core.encoding.ByteUtil.getInt128LE;

final class GlamMintReferenceNavInstructionTest {

  private static final PublicKey FEE_PAYER = PublicKey.fromBase58Encoded(
      "HuRA6CuTcLaWB9adGJ9aLG67sC4fqp4SMXYvDWmEhd83"
  );
  private static final PublicKey STATE = PublicKey.fromBase58Encoded(
      "9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT"
  );
  private static final PublicKey DEPOSIT_ASSET = PublicKey.fromBase58Encoded(
      "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
  );
  private static final PublicKey SIGNER_POLICY = PublicKey.fromBase58Encoded(
      "HeL5m28kD42iRcmxFWCdYqkMbAzFRWyukidwNNZGBEt"
  );
  private static final BigInteger I128_MIN = BigInteger.ONE.shiftLeft(127).negate();
  private static final BigInteger I128_MAX = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

  @Test
  void highLevelProdAndStagingFulfillPreserveTheGeneratedAccountShape() {
    final var solanaAccounts = SolanaAccounts.MAIN_NET;
    for (final var glamAccounts : List.of(GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING)) {
      final var client = client(glamAccounts);
      final var ordinary = client.fulfill(
          0,
          DEPOSIT_ASSET,
          solanaAccounts.tokenProgram(),
          OptionalLong.of(4_294_967_295L)
      );
      final var guarded = client.fulfillWithRefNav(
          0,
          DEPOSIT_ASSET,
          solanaAccounts.tokenProgram(),
          OptionalLong.of(4_294_967_295L),
          BigInteger.valueOf(-42)
      );

      assertEquals(ordinary.programId(), guarded.programId());
      assertEquals(ordinary.accounts(), guarded.accounts());
      assertArrayEquals(
          new byte[]{(byte) 214, 80, 104, 122, 65, (byte) 144, 19, (byte) 209},
          Arrays.copyOf(guarded.copyData(), 8)
      );
      assertEquals(1, guarded.data()[8]);
      assertArrayEquals(
          new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255},
          Arrays.copyOfRange(guarded.data(), 9, 13)
      );
      assertEquals(1, guarded.data()[13]);
      assertEquals(BigInteger.valueOf(-42), getInt128LE(guarded.data(), 14));
    }
  }

  @Test
  void highLevelProdAndStagingSubscribeDeriveEveryCanonicalAccount() {
    for (final var glamAccounts : List.of(GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING)) {
      final var client = client(glamAccounts);
      final var guarded = client.subscribeWithRefNav(
          0,
          DEPOSIT_ASSET,
          SolanaAccounts.MAIN_NET.tokenProgram(),
          SIGNER_POLICY,
          -1L,
          BigInteger.TEN
      );

      assertEquals(expectedSubscribeAccounts(glamAccounts, client, SIGNER_POLICY), guarded.accounts());
      assertArrayEquals(
          new byte[]{39, (byte) 136, 23, 75, 82, (byte) 217, (byte) 248, (byte) 141},
          Arrays.copyOf(guarded.copyData(), 8)
      );
      assertArrayEquals(
          new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255,
              (byte) 255, (byte) 255, (byte) 255, (byte) 255},
          Arrays.copyOfRange(guarded.data(), 8, 16)
      );
      assertEquals(1, guarded.data()[16]);
      assertEquals(BigInteger.TEN, getInt128LE(guarded.data(), 17));
    }
  }

  @Test
  void absentSubscribePolicyUsesTheEnvironmentMintProgramSentinel() {
    for (final var glamAccounts : List.of(GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING)) {
      final var client = client(glamAccounts);
      final var guarded = client.subscribeWithRefNav(
          0,
          DEPOSIT_ASSET,
          SolanaAccounts.MAIN_NET.tokenProgram(),
          null,
          1,
          BigInteger.ONE
      );

      assertEquals(expectedSubscribeAccounts(glamAccounts, client, null), guarded.accounts());
      assertEquals(glamAccounts.mintProgram(), guarded.accounts().get(11).publicKey());
    }
  }

  @Test
  void guardedWrappersRequirePrimaryMintAndAnI128Reference() {
    final var client = client(GlamAccounts.MAIN_NET);

    assertThrows(
        IllegalArgumentException.class,
        () -> client.fulfill(
            0,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            OptionalLong.of(-1)
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> client.fulfillWithRefNav(
            0,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            OptionalLong.of(4_294_967_296L),
            BigInteger.ONE
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> client.fulfillWithRefNav(
            1,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            OptionalLong.empty(),
            BigInteger.ONE
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> client.subscribeWithRefNav(
            1,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            null,
            1,
            BigInteger.ONE
        )
    );
    assertThrows(
        NullPointerException.class,
        () -> client.fulfillWithRefNav(
            0,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            OptionalLong.empty(),
            null
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> client.fulfillWithRefNav(
            0,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            OptionalLong.empty(),
            I128_MIN.subtract(BigInteger.ONE)
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> client.subscribeWithRefNav(
            0,
            DEPOSIT_ASSET,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            null,
            1,
            I128_MAX.add(BigInteger.ONE)
        )
    );

    client.fulfillWithRefNav(
        0,
        DEPOSIT_ASSET,
        SolanaAccounts.MAIN_NET.tokenProgram(),
        OptionalLong.empty(),
        I128_MIN
    );
    client.subscribeWithRefNav(
        0,
        DEPOSIT_ASSET,
        SolanaAccounts.MAIN_NET.tokenProgram(),
        null,
        1,
        I128_MAX
    );
  }

  @Test
  void packagePrivateCodecRejectsWrongProgramsFlagsAndMalformedSlices() {
    final var client = client(GlamAccounts.MAIN_NET);
    final var source = client.fulfill(
        0,
        DEPOSIT_ASSET,
        SolanaAccounts.MAIN_NET.tokenProgram(),
        OptionalLong.empty()
    );
    final var wrongProgram = Instruction.createInstruction(
        DEPOSIT_ASSET,
        source.accounts(),
        source.copyData()
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintReferenceNavCodec.fulfillWithRefNav(
            GlamAccounts.MAIN_NET.mintProgram(), wrongProgram, BigInteger.ONE)
    );

    final var wrongFlags = new ArrayList<>(source.accounts());
    wrongFlags.set(0, AccountMeta.createRead(wrongFlags.get(0).publicKey()));
    final var malformedAccounts = Instruction.createInstruction(
        source.programId(),
        wrongFlags,
        source.copyData()
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintReferenceNavCodec.fulfillWithRefNav(
            GlamAccounts.MAIN_NET.mintProgram(), malformedAccounts, BigInteger.ONE)
    );

    final var truncated = Instruction.createInstruction(
        source.programId(),
        source.accounts(),
        source.data(),
        source.offset(),
        7
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintReferenceNavCodec.fulfillWithRefNav(
            GlamAccounts.MAIN_NET.mintProgram(), truncated, BigInteger.ONE)
    );

    final var invalidTagData = source.copyData();
    invalidTagData[8] = 2;
    final var invalidTag = Instruction.createInstruction(
        source.programId(),
        source.accounts(),
        invalidTagData
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintReferenceNavCodec.fulfillWithRefNav(
            GlamAccounts.MAIN_NET.mintProgram(), invalidTag, BigInteger.ONE)
    );
  }

  @Test
  void packagePrivateCodecHonorsTheInstructionSlice() {
    final var client = client(GlamAccounts.MAIN_NET);
    final var source = client.fulfill(
        0,
        DEPOSIT_ASSET,
        SolanaAccounts.MAIN_NET.tokenProgram(),
        OptionalLong.of(9)
    );
    final var backing = new byte[source.len() + 6];
    System.arraycopy(source.data(), source.offset(), backing, 3, source.len());
    final var sliced = Instruction.createInstruction(
        source.programId(),
        source.accounts(),
        backing,
        3,
        source.len()
    );

    final var guarded = GlamMintReferenceNavCodec.fulfillWithRefNav(
        GlamAccounts.MAIN_NET.mintProgram(), sliced, BigInteger.valueOf(77)
    );

    assertEquals(1, guarded.data()[8]);
    assertEquals(9, Integer.toUnsignedLong(software.sava.core.encoding.ByteUtil.getInt32LE(
        guarded.data(), 9)));
    assertEquals(BigInteger.valueOf(77), getInt128LE(guarded.data(), 14));
  }

  private static List<AccountMeta> expectedSubscribeAccounts(final GlamAccounts glamAccounts,
                                                             final GlamAccountClient client,
                                                             final PublicKey signerPolicy) {
    final var solanaAccounts = SolanaAccounts.MAIN_NET;
    final var mint = client.vaultAccounts().mintPDA(0).publicKey();
    final var escrow = glamAccounts.escrowPDA(mint).publicKey();
    final var signerMintAta = client.splClient().findATA(
        FEE_PAYER,
        solanaAccounts.token2022Program(),
        mint
    ).publicKey();
    final var escrowMintAta = client.escrowMintTokenAccount(mint, escrow).publicKey();
    final var vaultDepositAta = client.vaultTokenAccount(
        solanaAccounts.tokenProgram(),
        DEPOSIT_ASSET
    ).publicKey();
    final var signerDepositAta = client.splClient().findATA(
        FEE_PAYER,
        solanaAccounts.tokenProgram(),
        DEPOSIT_ASSET
    ).publicKey();
    return List.of(
        AccountMeta.createWrite(STATE),
        AccountMeta.createRead(client.vaultAccounts().vaultPublicKey()),
        AccountMeta.createWrite(mint),
        AccountMeta.createRead(escrow),
        AccountMeta.createRead(glamAccounts.requestQueuePDA(mint).publicKey()),
        AccountMeta.createWritableSigner(FEE_PAYER),
        AccountMeta.createWrite(signerMintAta),
        AccountMeta.createWrite(escrowMintAta),
        AccountMeta.createRead(DEPOSIT_ASSET),
        AccountMeta.createWrite(vaultDepositAta),
        AccountMeta.createWrite(signerDepositAta),
        AccountMeta.createWrite(signerPolicy == null ? glamAccounts.mintProgram() : signerPolicy),
        AccountMeta.createRead(solanaAccounts.systemProgram()),
        AccountMeta.createRead(solanaAccounts.tokenProgram()),
        AccountMeta.createRead(solanaAccounts.token2022Program()),
        AccountMeta.createRead(solanaAccounts.associatedTokenAccountProgram()),
        AccountMeta.createRead(glamAccounts.policyProgram()),
        AccountMeta.createRead(glamAccounts.protocolProgram())
    );
  }

  private static GlamAccountClient client(final GlamAccounts glamAccounts) {
    final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, FEE_PAYER, STATE);
    return GlamAccountClient.createClient(SolanaAccounts.MAIN_NET, vaultAccounts);
  }
}

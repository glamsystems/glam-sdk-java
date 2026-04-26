package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;

import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;

public final class ExtLoopscalePDAs {

  public static ProgramDerivedAddress glamVaultPDA(final PublicKey glamProtocolProgram,
                                                   final PublicKey glamStateAccount) {
    return PublicKey.findProgramAddress(List.of(
      "vault".getBytes(US_ASCII),
      glamStateAccount.toByteArray()
    ), glamProtocolProgram);
  }

  public static ProgramDerivedAddress integrationAuthorityPDA(final PublicKey program) {
    return PublicKey.findProgramAddress(List.of(
      "integration-authority".getBytes(US_ASCII)
    ), program);
  }

  private ExtLoopscalePDAs() {
  }
}

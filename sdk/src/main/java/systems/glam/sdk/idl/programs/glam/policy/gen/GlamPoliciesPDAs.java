package systems.glam.sdk.idl.programs.glam.policy.gen;

import java.util.List;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;

import static java.nio.charset.StandardCharsets.US_ASCII;

public final class GlamPoliciesPDAs {

  public static ProgramDerivedAddress extraMetasAccountPDA(final PublicKey program,
                                                           final PublicKey mintAccount) {
    return PublicKey.findProgramAddress(List.of(
      "extra-account-metas".getBytes(US_ASCII),
      mintAccount.toByteArray()
    ), program);
  }

  public static ProgramDerivedAddress policyAccountPDA(final PublicKey program,
                                                       final PublicKey subjectTokenAccountAccount) {
    return PublicKey.findProgramAddress(List.of(
      "account-policy".getBytes(US_ASCII),
      subjectTokenAccountAccount.toByteArray()
    ), program);
  }

  private GlamPoliciesPDAs() {
  }
}

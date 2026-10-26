package systems.glam.sdk.mapping;

import software.sava.core.accounts.PublicKey;
import systems.glam.ix.proxy.SuppliedAccountsRequest;

import java.util.ArrayList;
import java.util.List;

/// Derives the accounts a handler seats at an account index and the document derives completely: the mapper
/// hands the supplier the program and the seeds the handler's IDL states, each account seed already replaced
/// by the address the mapper placed at that account index, and the account is the program-derived address
/// those seeds find. Nothing is guessed: a role the document states no derivation for, or one with an
/// argument seed that only a reader of the instruction data could resolve, is not served. The staging bridge
/// handlers' `bridge_routes` is the first such seat: the vault's route set for the provider (`"bridge-routes"`,
/// the state, the provider bit as a u16), under the bridge program.
///
/// Roles: any, by their derivations alone; a role's label is carried into a refusal and not interpreted.
public final class DerivedAccountResolver {

  private DerivedAccountResolver() {
  }

  /// True when `roles` is at least one, and every one carries a derivation of constant and account seeds.
  public static boolean serves(final List<SuppliedAccountsRequest.Role> roles) {
    if (roles.isEmpty()) {
      return false;
    }
    for (final var role : roles) {
      if (!derivable(role.derivation())) {
        return false;
      }
    }
    return true;
  }

  /// True when the derivation is stated and every seed is resolved: none is an argument of the data.
  static boolean derivable(final SuppliedAccountsRequest.Derivation derivation) {
    if (derivation == null) {
      return false;
    }
    for (final var seed : derivation.seeds()) {
      if (seed instanceof SuppliedAccountsRequest.Arg) {
        return false;
      }
    }
    return true;
  }

  /// The account each role's derivation finds, in the roles' order.
  ///
  /// @throws IllegalArgumentException when a role states no derivation, or derives from an argument of the
  ///                                  instruction data
  public static List<PublicKey> resolve(final List<SuppliedAccountsRequest.Role> roles) {
    final var accounts = new ArrayList<PublicKey>(roles.size());
    for (final var role : roles) {
      accounts.add(derive(role));
    }
    return List.copyOf(accounts);
  }

  /// The program-derived address of the role's seeds, in the order stated, under the role's program.
  ///
  /// @throws IllegalArgumentException when the role states no derivation, or derives from an argument of the
  ///                                  instruction data
  public static PublicKey derive(final SuppliedAccountsRequest.Role role) {
    final var derivation = role.derivation();
    if (derivation == null) {
      throw new IllegalArgumentException("The document states no derivation for " + role.role() + '.');
    }
    final var seeds = new ArrayList<byte[]>(derivation.seeds().size());
    for (final var seed : derivation.seeds()) {
      seeds.add(switch (seed) {
        case SuppliedAccountsRequest.Const constant -> constant.value();
        case SuppliedAccountsRequest.Account account -> account.address().toByteArray();
        case SuppliedAccountsRequest.Arg argument -> throw new IllegalArgumentException(
            role.role() + " derives from argument " + argument.path() + " of the instruction data, which this supplier does not read.");
      });
    }
    return PublicKey.findProgramAddress(seeds, derivation.program()).publicKey();
  }
}

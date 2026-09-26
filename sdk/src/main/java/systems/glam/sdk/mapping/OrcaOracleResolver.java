package systems.glam.sdk.mapping;

import software.sava.core.accounts.PublicKey;
import systems.glam.ix.proxy.SuppliedAccountsRequest;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/// Resolves the price-oracle prefix an Orca liquidity handler reads from its remaining accounts after the
/// global configuration seat: the registration selected for each pool mint ([RegisteredOracles]), then the
/// wrapped SOL oracle only when the two price in different units, since the handler converts the
/// SOL-denominated price through it and reads no further account otherwise. Every choice follows the
/// configuration and nothing is guessed: a mint with no selectable registration (none, all deprecated, a
/// tie), an oracle source the protocol does not price or the handler refuses, a missing or SOL-priced
/// wrapped SOL oracle when a conversion needs it, a required `sol_usd_oracle` the handler would not read,
/// and a role shape the handlers do not read all refuse the instruction, through the exception's message.
///
/// Roles: `asset_oracle`, with the one mint it prices at its `of` position, and `sol_usd_oracle`, at most
/// once and after the asset roles it depends on.
public final class OrcaOracleResolver {

  public static final String ASSET_ORACLE = "asset_oracle";
  public static final String SOL_USD_ORACLE = "sol_usd_oracle";

  /// A Kamino reserve chosen as an oracle, and the mint it prices (the wrapped SOL mint for the SOL/USD
  /// oracle): the transaction refreshes the reserve before the handler prices through it, and the handler
  /// requires the reserve's liquidity mint to be that mint.
  public record ReserveOracle(PublicKey reserve, PublicKey mint) {
  }

  /// The oracles for a request's roles, in the roles' order, a trailing optional SOL oracle left out when
  /// the handler would not read it, and the Kamino reserves among them.
  public record Resolved(List<PublicKey> accounts, List<ReserveOracle> kaminoReserves) {

    public Resolved {
      accounts = List.copyOf(accounts);
      kaminoReserves = List.copyOf(kaminoReserves);
    }
  }

  private final RegisteredOracles oracles;
  private final PublicKey wrappedSolMint;

  /// @param oracles        the configuration's registrations
  /// @param wrappedSolMint the mint the SOL/USD oracle is registered under
  public OrcaOracleResolver(final RegisteredOracles oracles, final PublicKey wrappedSolMint) {
    this.oracles = oracles;
    this.wrappedSolMint = wrappedSolMint;
  }

  /// True when `roles` are oracle roles only, and at least one.
  public static boolean serves(final List<SuppliedAccountsRequest.Role> roles) {
    if (roles.isEmpty()) {
      return false;
    }
    for (final var role : roles) {
      final var name = role.role();
      if (!ASSET_ORACLE.equals(name) && !SOL_USD_ORACLE.equals(name)) {
        return false;
      }
    }
    return true;
  }

  /// The oracles for `roles`: each `asset_oracle`'s selected registration, then for `sol_usd_oracle` the
  /// wrapped SOL oracle when the asset denominations differ, nothing when they agree and the role is
  /// optional.
  ///
  /// @throws IllegalArgumentException when an oracle cannot be placed; the message says which and why
  public Resolved resolve(final List<SuppliedAccountsRequest.Role> roles) {
    checkShape(roles);
    final var accounts = new ArrayList<PublicKey>(roles.size());
    final var reserves = new ArrayList<ReserveOracle>(2);
    final var denominations = new ArrayList<OracleDenomination>(2);
    for (final var role : roles) {
      switch (role.role()) {
        case ASSET_ORACLE -> {
          final var of = role.of();
          if (of.size() != 1) {
            throw new IllegalArgumentException(
                "asset_oracle names " + of.size() + " source accounts; the one mint it prices is expected.");
          }
          final var mint = of.getFirst();
          final var meta = priced(mint, "mint " + mint, false);
          denominations.add(OracleDenomination.of(meta.oracleSource()));
          accounts.add(choose(meta, mint, reserves));
        }
        case SOL_USD_ORACLE -> {
          if (denominations.isEmpty()) {
            throw new IllegalArgumentException("sol_usd_oracle depends on the asset_oracle roles before it, and none precedes it.");
          }
          final var first = denominations.getFirst();
          if (denominations.stream().allMatch(first::equals)) {
            if (role.optional()) {
              // the handler reads no SOL oracle: the trailing optional is left out
              return new Resolved(accounts, reserves);
            }
            throw new IllegalArgumentException(
                "sol_usd_oracle is required, but the asset oracles all price in " + first
                    + "; the handler reads no SOL oracle then, and one supplied would reach it as a native remaining account.");
          }
          final var meta = priced(wrappedSolMint, "the wrapped SOL mint " + wrappedSolMint + ", which the SOL/USD oracle is registered under", true);
          if (OracleDenomination.of(meta.oracleSource()) != OracleDenomination.USD) {
            throw new IllegalArgumentException(
                "The wrapped SOL oracle " + meta.oracle() + " (source " + meta.oracleSource()
                    + ") prices in SOL, not the USD the handler converts through.");
          }
          accounts.add(choose(meta, wrappedSolMint, reserves));
        }
        default -> throw new IllegalArgumentException("Role " + role.role() + " is not one the Orca oracle resolver serves.");
      }
    }
    return new Resolved(accounts, reserves);
  }

  /// The handlers read the SOL oracle last and at most once, so a role list that seats it otherwise is
  /// refused before anything is resolved.
  private static void checkShape(final List<SuppliedAccountsRequest.Role> roles) {
    boolean solRoleSeen = false;
    for (final var role : roles) {
      if (SOL_USD_ORACLE.equals(role.role())) {
        if (solRoleSeen) {
          throw new IllegalArgumentException("A second sol_usd_oracle; the handler reads at most one.");
        }
        solRoleSeen = true;
      } else if (solRoleSeen) {
        throw new IllegalArgumentException("asset_oracle after sol_usd_oracle; the handler reads the SOL oracle last.");
      }
    }
  }

  private AssetMeta priced(final PublicKey mint, final String what, final boolean solRole) {
    final var meta = switch (oracles.select(mint)) {
      case RegisteredOracles.Selected selected -> selected.meta();
      case RegisteredOracles.None none -> throw new IllegalArgumentException(switch (none.failure()) {
        case UNREGISTERED -> "The global configuration registers no oracle for " + what + '.';
        case ALL_DEPRECATED -> "Every registration of " + what + " is deprecated (" + none.registrations() + " of them).";
        case TIED -> {
          final var tied = none.tied();
          final var named = new StringJoiner(", ");
          for (final var candidate : tied) {
            named.add(candidate.oracle().toString());
          }
          yield "The registrations of " + what + " tie at priority " + tied.getFirst().priority() + " under "
              + tied.getFirst().oracleSource() + " (" + named + "); nothing selects between them.";
        }
      });
    };
    final var source = meta.oracleSource();
    if (source == OracleSource.ChainlinkRWA || source == OracleSource.ChainlinkX) {
      throw new IllegalArgumentException(
          "The oracle of " + what + ", " + meta.oracle() + " (source " + source + "), " + (solRole
              ? "is a Scope-read feed the handler does not price as the SOL/USD oracle (InvalidPricingOracle: no Scope index)."
              : "is one the Orca handler refuses (UnsupportedOracleSource)."));
    }
    if (OracleDenomination.of(source) == null) {
      throw new IllegalArgumentException(
          "The oracle of " + what + ", " + meta.oracle() + " (source " + source + "), has no denomination the protocol prices.");
    }
    return meta;
  }

  private static PublicKey choose(final AssetMeta meta, final PublicKey mint, final List<ReserveOracle> reserves) {
    final var oracle = meta.oracle();
    if (meta.oracleSource() == OracleSource.KaminoReserve) {
      reserves.add(new ReserveOracle(oracle, mint));
    }
    return oracle;
  }
}

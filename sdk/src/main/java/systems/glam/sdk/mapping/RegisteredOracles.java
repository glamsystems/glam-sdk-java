package systems.glam.sdk.mapping;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/// Which registration prices a mint the global configuration registers, under GLAM's one oracle-selection
/// rule (the monorepo's `oracleSelection.ts`, which every composer follows so that two consumers map one
/// instruction alike): a deprecated registration is never selected; the lowest priority number wins; among
/// equal priorities the source preference decides, the Kamino reserve first and then the sources in the
/// order the program declares them; equal priority and equal source is a tie nobody can break from here,
/// so nothing is selected and the tied registrations are named. The position of a registration in the
/// stored vector never decides anything.
///
/// A registration is deprecated when its priority is below zero. Whether a stored byte can be is the
/// generated reader's doing, which follows the type the deployed configuration document gives it: an
/// unsigned reader never yields a negative priority, so nothing decoded through it is deprecated, and a
/// signed one marks a registration deprecated below zero; a row built by hand with a negative priority
/// is deprecated either way. The rule's caller-named oracle has no counterpart here, since the mapper's
/// roles name none.
public final class RegisteredOracles {

  /// The outcome of selecting a mint's registration: [Selected] with why, or [None] with why not.
  public sealed interface Selection permits Selected, None {
  }

  /// Why a registration was selected.
  public enum Reason {
    SOLE_ACTIVE,
    LOWEST_PRIORITY,
    SOURCE_PREFERENCE
  }

  /// Why none was.
  public enum Failure {
    UNREGISTERED,
    ALL_DEPRECATED,
    TIED
  }

  public record Selected(AssetMeta meta, Reason reason) implements Selection {
  }

  /// @param tied          the registrations a tie is between, in stored order; empty for every other failure
  /// @param registrations how many rows the vector holds for the mint, deprecated ones included
  public record None(Failure failure, List<AssetMeta> tied, int registrations) implements Selection {

    public None {
      tied = List.copyOf(tied);
    }
  }

  private final Map<PublicKey, List<AssetMeta>> byMint;

  private RegisteredOracles(final Map<PublicKey, List<AssetMeta>> byMint) {
    this.byMint = byMint;
  }

  /// Over a decoded configuration's asset metas, see [#of(AssetMeta[])].
  public static RegisteredOracles of(final GlobalConfig globalConfig) {
    return of(globalConfig.assetMetas());
  }

  /// Over asset metas in their stored order, which names a tie's registrations and decides nothing else.
  public static RegisteredOracles of(final AssetMeta[] assetMetas) {
    final var byMint = new HashMap<PublicKey, List<AssetMeta>>();
    for (final var meta : assetMetas) {
      byMint.computeIfAbsent(meta.asset(), mint -> new ArrayList<>(1)).add(meta);
    }
    return new RegisteredOracles(byMint);
  }

  /// Whether a priority marks its registration deprecated: below zero, which an unsigned reader never
  /// yields.
  public static boolean deprecated(final int priority) {
    return priority < 0;
  }

  /// The rank of a source among equal priorities: the Kamino reserve first, then the program's declaration
  /// order.
  public static int sourceRank(final OracleSource source) {
    return source == OracleSource.KaminoReserve ? -1 : source.ordinal();
  }

  /// The registration that prices `mint`, under the rule above.
  public Selection select(final PublicKey mint) {
    final var rows = byMint.getOrDefault(mint, List.of());
    final var active = new ArrayList<AssetMeta>(rows.size());
    for (final var meta : rows) {
      if (!deprecated(meta.priority())) {
        active.add(meta);
      }
    }
    if (active.isEmpty()) {
      return new None(rows.isEmpty() ? Failure.UNREGISTERED : Failure.ALL_DEPRECATED, List.of(), rows.size());
    }
    if (active.size() == 1) {
      return new Selected(active.getFirst(), Reason.SOLE_ACTIVE);
    }
    final var preferred = lowest(active, AssetMeta::priority);
    if (preferred.size() == 1) {
      return new Selected(preferred.getFirst(), Reason.LOWEST_PRIORITY);
    }
    final var bySource = lowest(preferred, meta -> sourceRank(meta.oracleSource()));
    return bySource.size() == 1
        ? new Selected(bySource.getFirst(), Reason.SOURCE_PREFERENCE)
        : new None(Failure.TIED, bySource, rows.size());
  }

  /// The selected registration of `mint`, or null when none is; [#select] says why.
  public AssetMeta forMint(final PublicKey mint) {
    return select(mint) instanceof Selected selected ? selected.meta() : null;
  }

  /// How many mints hold a registration, deprecated ones included.
  public int size() {
    return byMint.size();
  }

  private static List<AssetMeta> lowest(final List<AssetMeta> entries, final ToIntFunction<AssetMeta> key) {
    int least = Integer.MAX_VALUE;
    for (final var entry : entries) {
      least = Math.min(least, key.applyAsInt(entry));
    }
    final var lowest = new ArrayList<AssetMeta>(entries.size());
    for (final var entry : entries) {
      if (key.applyAsInt(entry) == least) {
        lowest.add(entry);
      }
    }
    return lowest;
  }
}

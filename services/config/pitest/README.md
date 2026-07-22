# Mutation-testing baseline & triage policy — `services`

Each `pitest<Suite>` run is finalized by `pitest<Suite>Verify`, which diffs the
run's unkilled mutants (`SURVIVED` and `NO_COVERAGE`) against the accepted
baseline in `<suite>-accepted.csv` and **fails on anything new**. Baseline row
format: `class,method,line,mutator,status`. The canonical policy is sava-build's
`HARDENING.md`; this file records what is accepted *here* and why.

A new unkilled mutant has exactly three legal outcomes:

1. **Kill it** — add or strengthen a test. Prefer asserting the property the
   mutant breaks over restating the implementation.
2. **Refactor** — restructure so the mutant cannot exist.
3. **Accept it knowingly** — re-run with `-PupdateMutationBaseline` and record
   the reason under "Triaged equivalent mutants" below. Acceptance is for
   mutants *equivalent with respect to observable behavior*, not for "hard to
   test".

Line numbers are part of the baseline key, so unrelated edits to a mutated file
shift entries: the verify task then reports both stale and "new" rows. Confirm
the new rows are the shifted old ones, then refresh with
`-PupdateMutationBaseline`.

## Suite

One catch-all suite, `pitestServices`, targeting `systems.glam.services.*` by
wildcard with exclusions rather than an allowlist, so a new class is mutated
by default rather than silently skipped. Excluded: test sources sharing the
recompiled root (including the shared helpers in `services.tests`, which no
`*Test*` pattern matches) and the git-ignored `Integ` scratch classes —
present on a dev machine and absent in CI, so mutating them would make the
baseline machine-dependent. `build.gradle.kts` is the authoritative
definition.

## Baseline composition

| Date | Rows | `NO_COVERAGE` | `SURVIVED` | Killed |
|---|---|---|---|---|
| seeded 2026-07-21 | 1647 | 1493 | 154 | 358/2149 (16%) |
| 2026-07-21 | 1605 | 1454 | 151 | 406/2151 (18%) |
| 2026-07-21 (3rd pass) | 1458 | 1245 | 213 | 563/2151 (26%) |
| 2026-07-21 (4th pass) | 1408 | 1153 | 255 | 616/2151 (28%) |
| 2026-07-21 (5th pass) | 1370 | 1095 | 275 | 665/2151 (30%) |
| 2026-07-21 (6th pass) | 1299 | 917 | 382 | 754/2151 (35%) |

The 6th pass covered `integrations/kamino/KaminoCacheImpl` using checked-in
mainnet snapshots (`src/test/resources/accounts/kamino/`, provenance in its
README): the accept dispatch for all four account shapes, feed→mappings→reserve
dependency ordering, staleness/idempotence, listeners, persistence, and vault
state handling. One behavior gap pinned by the fixture: the SOL reserve's
price chain heads with a `MostRecentOf` composite, and
`ScopeFeedContext.indexes()` matches only direct `OracleEntry`s (the code's
own comment marks nested types as unhandled) — so the agg-index query serves
nothing for composite-only chains. If SOL pricing via scope agg indexes
matters in production, that gap needs closing.

The 5th pass extended `GlobalConfigCacheTests` into the streaming paths the
disk-init tests never reached: `accept` transitions (unchanged/older/foreign
data ignored; a valid newer config replaces state, persists to disk, and
releases `awaitNewGlobalConfig` waiters; an invalid one nulls the cache and
notifies listeners), `topPriorityForMintChecked` decimals validation against
a mint cache (both directions), `checkAccount`, and the query helpers.

The 4th pass covered `db/sql/BatchSqlExecutorImpl`: the statement-parsing and
batch-count statics directly, and the `run()` loop against proxied JDBC
interfaces (full batches, remainder flush, and the SQLException requeue path,
which restores failed items in their original order before retrying). A
`RUN_ERROR` appeared once under two-suite load and resolved to detected on a
quiet re-run — the expected transient shape, not a result.

The 3rd pass covered `rpc/AccountFetcherImpl` (driven deterministically: a
Proxy-backed `SolanaRpcClient` serves canned batches, zero fetch delay, and
the fake interrupts the thread on its final batch so `run()` exits) and
`oracles/scope/ScopeFeedContext` (surfacing two real bugs: the `indexes()`
loop double-incremented and skipped every other matching reserve, and
`resortReserves`' replacement path returned before re-indexing by chain
index, leaving `reservesByIndex` serving stale contexts). The `SURVIVED`
count rose because newly covered code carries untriaged survivors — that is
the next phase's work. This suite now reports ~26 load-dependent `TIMED_OUT`
mutants (AccountFetcher's loops); per HARDENING.md, verify solo-vs-gate
before trusting any flip, and union only observed flips.

Triage note for `ScopeFeedContext.indexReserveByIndex`: the loop over
`priceChainIndexes()` returns after handling the *first* index in two of its
three branches but continues in the third — multi-hop chains (more than one
real index before the u16-max padding) index inconsistently depending on map
state. Current tests use single-index chains; decide the intended behavior
before covering multi-hop chains.

The 2026-07-21 pass covered `io/KeyedFlatFile` (surfacing two real bugs:
`deleteEntry` skipped a swapped-in duplicate, and `writeEntries` never wrote
to disk), `fulfillment/accounting` (redemption windows, unsigned share math),
and `execution/FormatUtil`. Remaining `KeyedFlatFileImpl` survivors are
durability calls (`force`, lock guards) — unobservable in-process; triage as
a family when killing mutants here.

## Untriaged debt

The baseline was seeded with the full pre-existing survivor population when
the ratchet was adopted, per HARDENING.md's adoption path — **triage debt made
explicit, not acceptance**. Largest untested blocks, in priority order:
`integrations/kamino/KaminoCacheImpl` (~214), `rpc/AccountFetcherImpl`
(~152), `state/GlobalConfigCacheImpl` (~119), `oracles/scope/ScopeFeedContext`
(~107), `db/sql/BatchSqlExecutorImpl` (~87).

Shrinking the baseline is always an improvement; growing it requires a reason
written here.

## Triaged equivalent mutants (accepted with reasons)

None yet.

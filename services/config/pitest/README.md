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
| 2026-07-22 | 1286 | 909 | 377 | 768/2151 (35%) |
| 2026-07-22 (2nd) | 1265 | 909 | 356 | 789/2151 (36%) |
| 2026-07-22 (naked receiver + recording pass) | 1311 | 1069 | 340 | 851/2260 (37%) |

The 2026-07-22 (2nd) pass killed 21 `BaseDelegateServiceConfig.parseProperties`
survivors by pinning both directions of every optional-section presence guard:
each section parsed with real values when present (serviceBackoff single
strategy, formatter formats, tableCache capacity, rpcCallWeights, a separate
sendRPC balancer, the websocket endpoint value), and the absent-case defaults
characterized exactly — serviceBackoff falls back to fibonacci, tableCache to
its documented defaults, sendRPC to the primary rpc balancer, and
notificationHooks to a no-op client, while callWeights stays null.

The 2026-07-22 pass added the `RequestQueue` serde round trip through
`RedemptionSummary.createSummary(accountInfo, …)` (the mutation suites exclude
generated code, so that layout boundary is pinned by test instead) and
`AssetMetaContext.compareTo` ordering (negative priorities sort after every
non-negative one, then by magnitude). A sweep confirmed the only main class
outside every suite's targeting is the git-ignored `systems.glam.Integ`
scratch file — no silent mutation blind spots.

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

## EXPERIMENTAL_NAKED_RECEIVER trial (2026-07-22)

Fluent calls returning their receiver are expressions, so `VoidMethodCallMutator`
never fires on them. Trialled per sava-build's HARDENING.md and **kept**, since
it fires here:

| Suite | Mutants | Detected | New unkilled |
|---|---|---|---|
| `services` | 2162 -> 2260 (+98) | 800 -> 832 (+32) | 65 |

Of the 65 new baseline rows, 62 are `NO_COVERAGE` in classes that already carry
untriaged debt, and three are survivors triaged below. It immediately exposed a
real gap: `KeyedFlatFileImpl.appendEntry` seeks to the end of the channel before
writing, and nothing covered a *reopened* file — where the channel starts at
position 0 and a dropped seek overwrites the first entry instead of appending.
That is the restart path for every on-disk cache here; it now has a test.

### Naked-receiver survivors (accepted with reasons)

**`ScopeFeedContext.indexes` — dropped `.sorted()`** (line 277). The stream
sorts `FilteredReserve` by collateral descending, but its source
`reservesByMint` is *already* maintained in that order: `resortReserves` sorts
every mutation with `RESERVE_CONTEXT_BY_LIQUIDITY`, which is the same
descending-unsigned-collateral order, and `Stream.sorted` is stable, so
reserves contributing several matching entries keep their encounter order
either way. Re-sorting an already-sorted source cannot change the result.
Killing it would mean breaking the invariant the rest of the class maintains.

**`KaminoCacheImpl.indexes` — dropped `.sorted()`** (line 176). This one picks
the highest-liquidity feed across *scope feeds*, so distinguishing it needs two
feeds whose reserves cover the same mint at different depths. The fixtures hold
a single feed (the klend one), so sorting one element is a no-op — **unreachable
in-harness**, not equivalent. The escape is a second `Configuration` +
`OracleMappings` snapshot (the hubble feed, `ScopeFeedAccounts.SCOPE_MAINNET_HUBBLE_FEED`)
plus reserves pointing at it; add those and this becomes killable.

**`KeyedFlatFileImpl.deleteEntry` — dropped `mappedBuffer.force()`** (line 73).
Durability only: the swap is already visible through the same mapping and to
every subsequent read in the process, so no in-process assertion can see whether
the pages were flushed. Same family as the `force`/lock survivors already
accepted for this class.

## Recording-collaborator pass (2026-07-22)

sava-build's HARDENING.md notes that "wire-invisible" behaviour is usually
observable through an injected recording collaborator, and that capturing the
log stream is the cheap alternative for trivial emissions. Applied here, this
killed 19 survivors that had looked untestable:

- **Log emissions (10).** `GlobalConfigCacheImpl` logs before every rejection
  in `createMapChecked`, `topPriorityForMintChecked` and `checkAccount`, as do
  `BatchSqlExecutorImpl`'s batch reports and `KaminoCacheImpl`'s unhandled
  account branch. `systems.glam.services.tests.LogCapture` attaches a JUL
  handler for the duration of a test, formats `{0}` patterns with their
  parameters, and asserts the record. This pins a real contract — **a rejected
  config, a failed batch or an unrecognised account is never silent** — rather
  than restating the implementation. The tests previously set the logger to
  `Level.OFF`, which is precisely why these survived.
- **Lock release (9).** Every entry point takes a `ReentrantLock` in a
  try/finally, and a dropped `unlock()` is invisible to any single-threaded
  result assertion while deadlocking every other caller in production. The
  locks in `KeyedFlatFileImpl`, `AccountFetcherImpl` and `BatchSqlExecutorImpl`
  are now package-private (the repo's stated preference over reflection), and
  the tests assert `!lock.isLocked()` after the operation returns. Deterministic
  on the calling thread, with no second thread and no waiting.

Remaining in this family and *not* killed this way: the `ReentrantReadWriteLock`
releases in `KaminoCacheImpl` (same technique, needs the parent lock exposed
rather than just the read/write views), and `force()`/`close()` durability calls,
which no in-process assertion can observe.

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

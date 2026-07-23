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
| 2026-07-22 (rw-locks + equality) | 1287 | 1049 | 329 | 882/2260 (39%) |
| 2026-07-22 (change detection) | 1256 | 1040 | 307 | 913/2260 (40%) |
| 2026-07-22 (config transitions) | 1245 | 1031 | 301 | 928/2260 (41%) |
| 2026-07-22 (big-decimal trial + kamino sequences) | 1268 | 936 | 332 | 1032/2263 (45%) |
| 2026-07-22 (scope shapes + fetcher batching) | 1129 | 928 | 264 | 1065/2262 (47%) |
| 2026-07-22 (fetcher dispatch hardening) | 1130 | 928 | 268 | 1068/2264 (47%) |
| 2026-07-22 (state change detector) | 1121 | 924 | 260 | 1079/2264 (47%) |
| 2026-07-22 (config parse + global config validation) | 1082 | 908 | 240 | 1118/2266 (49%) |
| 2026-07-22 (config sections + mint cache) | 1059 | 905 | 220 | 1141/2266 (50%) |
| 2026-07-22 (batch sql executor) | 1033 | 893 | 203 | 1170/2266 (51%) |
| 2026-07-22 (multi-row requeue fix) | 1032 | 893 | 202 | 1170/2267 (51%) |
| 2026-07-23 (fetcher batching + reactive mode) | 993 | 861 | 194 | 1212/2267 (53%) |
| 2026-07-23 (top-up loop rework) | 991 | 861 | 192 | 1215/2268 (53%) |

The dispatch-hardening change wraps every consumer callback in
`AccountFetcherImpl` (the always-call listeners, batch and unique consumers,
and the oversized-batch notification) in its own catch-and-log: previously a
single throwing consumer exited `run()`'s loop and silently stopped account
fetching for every service sharing the fetcher. A consumer's failure is now
its own — logged as "Account consumer failed; continuing to poll" — and the
test drives a throwing listener, batch consumer and unique consumer through
one cycle, asserting the healthy consumer in the same batch is still served,
the loop survives into a second cycle, and the loop-fatal log line never
appears.

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

The `ReentrantReadWriteLock` releases in `KaminoCacheImpl` and
`GlobalConfigCacheImpl` were then killed the same way: both classes discarded
the parent lock and kept only the read/write views, so each now retains it
package-private and tests assert `!lock.isWriteLocked()` and a zero read-lock
count after each entry point, including the throwing path in
`topPriorityForMintChecked`. Still not killable this way: `force()`/`close()`
durability calls, which no in-process assertion can observe.

`GlobalConfigCacheImpl.createMapChecked` was covered only on its *rejection*
side; the transitions it must **accept** and report were the survivors. Tests
now drive an oracle configuration change (priority and max age independently),
an unchanged config that must notify nobody, a rotation of a negative-priority
entry, and an added oracle — each asserting both the listener callback and the
log line, because several of these notifications fire from outside the loop
that logs them, so the listener assertion alone cannot tell whether the loop
ran.

`ReserveContext.changed` got the same treatment, and for the same reason: it
decides what the Kamino cache propagates to listeners and whether a reserve is
merely re-sorted or fully re-indexed, so a dropped comparison leaves downstream
state stale rather than failing. Each of its ten compared fields now has a case
differing in exactly that field, plus the accumulation case (changes add to the
set rather than replacing it), the null-price-chain transitions in both
directions, the different-reserve rejection, and the `onlyCollateralChanged`
fast-path gate.

`MinGlamStateAccount.equals` decides whether a re-fetched account is a change
worth propagating, so a dropped comparison silently reports "unchanged" and
listeners never fire. Each of its ten compared components now has a case
differing in exactly that component (plus symmetry, and the deliberate
exclusion of slot and raw data, so a no-op refresh stays equal).

**`MinGlamStateAccount.hashCode` mixing arithmetic (9 mutants)** — the
`MathMutator` rows on lines 339-347, each swapping a `31 *` for `31 /` or a
`+` for a `-` in the accumulator chain. `hashCode`'s only contract is that
equal accounts hash equally, which every one of these preserves, so nothing
observable distinguishes them: a different-but-still-well-distributed mixing
constant is not a defect. The two properties that *do* matter are asserted —
equal accounts hash equally, and accounts differing in any compared component
hash differently — and those killed the `return 0` mutant that the contract
alone would have allowed. Distinguishing the rest would mean asserting exact
hash values, which pins an implementation detail callers cannot depend on.

## EXPERIMENTAL_BIG_INTEGER / EXPERIMENTAL_BIG_DECIMAL trial (2026-07-22)

Trialled with `./gradlew pitestMutatorTrial -PtrialMutators=EXPERIMENTAL_BIG_INTEGER,EXPERIMENTAL_BIG_DECIMAL`
and **kept for `services`**, which carries the money math the default
arithmetic mutators cannot express — `BigDecimal` share sums in
`RedemptionSummary`/`RedemptionRequest` and `BigInteger` liquidity totals in
`ScopeFeedContext.indexes`:

| Suite | Generated | Killed by existing tests | Unkilled |
|---|---|---|---|
| `services` | 3 (BigDecimalMutator x2, BigIntegerMutator x1) | 3 | 0 |
| `sdk` | 0 — cannot fire | — | — |

Zero baseline cost: every newly expressible mutant was already killed, which is
what a property-asserting suite looks like. Suite total moved 2260 -> 2263
mutants, 928 -> 931 detected. Not enabled for `sdk`, where no such arithmetic
exists.

## Kamino cache sequence pass (2026-07-22)

`KaminoCacheSequenceTests` drives the cache through changed/stale/malformed
*sequences* of the mainnet fixtures — byte-surgical variants using the
generated offset constants (collateral, token name, each vault key) — killing
~101 mutants across the dispatch chain, mapping/reserve/vault update gating,
per-key vault change detection, and the mappings-scan fallback of `indexes`.
The 24 survivors this deeper coverage newly exposed are accepted as follows:

**Feed-map maintenance invisible through the cache API (updateIfChanged
392/395/398/399, reIndexReserves 196/198)** — `resortReserves`,
`removePreviousEntry` and `indexReserveContext` maintain `ScopeFeedContext`'s
internal by-index/by-mint maps, and the cache exposes those only through
`indexes()`, which returns null for the fixture's SOL reserve (composite
`MostRecentOf` chain — see the 6th-pass note). **Unreachable in-harness with
the current fixtures**; the named escape is a reserve whose chain heads with a
direct oracle entry (a second feed snapshot, e.g. the hubble feed), at which
point these become killable and should be.

**In-lock recheck race guards (handleMappingChange 328, updateIfChanged 386
retry, handleVaultStateChange 449)** — double-checks between the optimistic
read and the locked write; single-threaded tests cannot interleave a
concurrent writer between the two. Deterministically forcing that interleaving
is the concurrency-harness problem ravina's triage README documents at length;
accepted with that as the named escape.

**Slot-gate shadowed comparisons (lambda 457 boundary/order)** — the merge
remapping picks the newer context, but `handleVaultStateChange` line 438
already rejects non-newer slots before merge is reached, so the remapping only
ever sees a strictly newer value and its `>=`-vs-`>` boundary cannot be
observed. Defensive redundancy, equivalent in context.

**Remaining per-key `createIfChanged` internals (KaminoVaultContext
112-173, noKeyChange 101)** — the null-transition arms (a key appearing where
none was, or vanishing to the NULL sentinel). The fixture's keys are all
present and real; synthesizing null-key variants means hand-building 62KB
VaultState images. Accepted as unreachable-in-harness; escape: a fixture from
a vault with an unset farm/lookup-table key.
## Scope shapes + fetcher batching pass (2026-07-22)

`ScopeFeedContextTests` gained the multi-reserve shapes the single-reserve
cases could not distinguish: several reserves sharing one chain index
(coexist, replace-within, remove-one-keep-other), removal of unknown keys
against both single- and multi-entry arrays, collateral-ordered serving with
in-place re-sorts, and a chainless reserve skipped by `indexes`. One mutant
was closed by **refactor** instead: `indexReserveContext`'s leading
`indexReserveByIndex` call became a redundant double-index when the 3rd-pass
fix taught both `resortReserves` paths to re-index, so the call is gone and
the mutant cannot exist.

`AccountFetcherTests` gained the batching interior: empty batches dropped by
every queueing flavour, small batchable lists queued whole, a fresh
priority-unique consumer served, the recent-slot scan skipping null accounts,
null contexts and zero slots without letting them overwrite a real slot, a
callback queueing into the batch in flight (served from that same batch — one
RPC call, shared result map), always-fetch keys restored after the cycle trim,
and a full batch absorbing a 100%-overlapping request while deferring a
non-overlapping one to the next cycle.
**Count guards subsumed by range-length comparison
(`MinGlamStateAccount.createIfChanged` 217, 235)** — `sameAssets` and
`sameExternalPositions` each open with `count == this.section.length &&
Arrays.equals(bytes...)`. Forcing the count operand true when the counts
differ changes nothing: the byte ranges are computed from each side's own
count, so `Arrays.equals` over ranges of different lengths returns false
immediately and the flag lands false either way. The count check is a
deliberate short-circuit that skips the byte compare — the same
fast-path-routing family as HARDENING.md's canonical example. The nine
branch mutants that *were* observable (per-section reuse vs reparse, the
enabled flip, and both immutable-base-field guards) are killed by identity
assertions: content equality cannot tell a reuse from a reparse, so the
tests pin the array instances.

## Config parse + global config validation pass (2026-07-22)

`BaseDelegateServiceConfigTests` closed the section-presence guard cluster in
`parseProperties`: an rpc-only config leaves `websocketConfig` null, present
optional sections land on the parsed values (`defensivePolling.globalConfig`),
and an absent `serviceBackoff` defaults to fibonacci — distinguishable from an
empty-parsed exponential at `delay(3)` (3s vs 4s), which is what kills the
absent-vs-empty guard pair.

`GlobalConfigCacheTests` closed the `createMapChecked` rejection branches:
cross-config decimals change (via an oracle change at the same index so the
per-index compare flags-and-continues into the map sweep), one oracle account
reused with a different source, a mint-cache decimals disagreement (plus its
ERROR log), the deprecated push-source rejection log, and the same-index
source-change log. The final per-asset `Arrays.sort` is pinned by demoting the
existing entry in place and appending a better-priority oracle — only the sort
can serve the appended entry first. The `MintContext` overload of
`topPriorityForMintChecked` is pinned by identity against the `PublicKey`
overload. This pass also surfaced and fixed a real bug: a checked lookup after
cache invalidation dereferenced the nulled `assetMetaMap` and threw NPE;
misses now return null until a valid config is re-accepted.

**Null-state rechecks in `topPriorityForMintChecked` (148/154 EQUAL pairs) and
invalidation `signalAll` (159)** — each `||` guard yields one killable mutant
per operand (killed by the decimals-mismatch throw test) and one that only a
concurrent invalidator between the read unlock and write lock could observe —
the same in-lock race-guard family as the KaminoCache acceptances, with the
same concurrency-harness escape. `signalAll` needs a parked waiter to observe;
same family as the accept-path `signalAll` acceptances (694/709).

## Config sections + mint cache pass (2026-07-22)

Killed the section-presence guards that only a *present* section can
distinguish: `glamStateKey`, `minCheckStateDelay`/`maxCheckStateDelay`, a
`signingService` built through the ServiceLoader-registered
`MemorySignerFactory`, a `notificationHooks` webhook whose `postMsg` returns
one pending future (the noop default returns none), a `helius` section
building `feeProviders`, and the no-rpc parse pinning `rpcClients == null`
(the always-parse mutant builds a balancer from an empty prefix instead).
`FulfillmentServiceConfig` now parses fields *after* a leading `softRedeem`
(the stop-early mutant), and its properties path pins the base sections.
`DefensivePollingConfig`'s JSON path parses all five fields distinctly and
throws on an unknown field (the forced-match mutant silently lands unknowns in
the last slot). `MintCacheImpl.close` is pinned by "a closed cache refuses new
entries", and `delete` by a two-instance case: it must not report an entry
whose persistent record was already removed by another cache over the same
file.

**Absent-vs-empty-parse equivalents (`parseProperties` 286/307/363/368 pairs)**
— the always-parse direction on `notificationHooks`, `tableCache`,
`accountFetcher` and `defensivePolling`: parsing an empty section produces the
same value the absent path synthesizes (`NotifyClient.createClient([])`
returns the same noop shape as `setDefaults`; the other three parsers default
every field to exactly their `createDefault` values). No observable output
distinguishes them.

**Null-over-null assigns (`parseProperties` 400/405; `DefensivePollingConfig`
60–76)** — `parseDuration(null)` returns null, so forcing the `!= null` guard
merely re-assigns null over null; `get()`/`setDefaults` re-default nulls
either way.

**True-or-throw returns (`FulfillmentServiceConfig.test` 63)** — the base
`test` either handles a field (returns true) or throws on unknown fields, so
forcing the propagated return to true is indistinguishable.

**Missing-key delete fast path (`MintCacheImpl.delete` 55)** — forcing the
null-check false sends a missing key into `deleteEntry`, which scans, finds
nothing, returns 0 and yields the same null; the guard only skips file I/O.

## Batch SQL executor pass (2026-07-22)

Killed 18 of the 25 `BatchSqlExecutorImpl` survivors. `parseTableName` bounds
are pinned by keyword-only and name-at-end statements. The retry path is
pinned by "an interrupt pending at the backoff sleep cancels the retry"
(removing the sleep re-executes the failed batch before exiting), the
attempt-count log by `Failed 1 times`, the remainder commit log by
`1 out of 1`, and the two catch paths by "a clean interrupt exit logs no
error" and "a runtime error is logged and ends the run without leaking".
The signalling protocol is pinned deterministically — `batchComplete` is now
package-private (same precedent as `lock`) so the test sequences the runner
by state instead of sleeping: the first queued item must wake the parked
runner, filling the batch must cut the delay window short, and a waiter in
`awaitBatchComplete` is released only once the batch has fully executed
(release-time size is asserted). The lost-signal mutants die as timeouts in
those await paths — load-dependent by nature, but each also fails the
5-second join asserts on a quiet machine.

**Spurious-signal directions (`queue` 207 EQUAL_ELSE/ORDER_IF)** — forcing
the signal condition true adds a lock cycle and an extra signal to a runner
that rechecks its guards on wake; no observable difference exists.

**Fast-path skips (`awaitBatchComplete` 145; `run` 72 boundary/ORDER_IF)** —
the outer `batchComplete` check only skips a lock acquisition around a
correctly-guarded while; entering the fill/wait block with a full batch
pending exits the delay window immediately. Both are flicker, not behavior.

**Zero-remaining re-arm (`run` 82 boundary)** — `remainingNanos > 0` vs
`>= 0` differs only when a wait returns exactly 0, which re-arms one
zero-nanos await and exits on its negative return.

**Requeue gap guards — RESOLVED by fix.** The failed-batch walk used to break
at the first unset slot, and a multi-row `StatementPreparer` (the `int`
return contract allows it) left index gaps that silently dropped items from
the retry. `run()` now tracks items and rows separately: `batch[]` is indexed
densely by item, `numRows` drives the execute threshold, and the walk requeues
every slot below `numItems` unconditionally. Pinned by a two-rows-per-item
failure test (the whole batch retries) and a zero-rows-per-item test (the
`numItems == batch.length` guard prevents overflow when rows never
accumulate). The remaining `Arrays.fill` mutant (`run` 76) is now pure GC
hygiene — it releases references while the runner parks between cycles — and
is accepted as unobservable.

**Batch-length equality guard (`run` 111 EQUAL_ELSE on `numRows >=
batchSize`)** — the `||` pairing means one direction only shows when rows and
items disagree at the boundary; the multi-row and zero-row tests pin the
observable directions, the residual direction is a redundant re-check.

## Fetcher batching + reactive mode pass (2026-07-23)

Killed ~40 `AccountFetcherImpl` survivors and fixed two real bugs the
survivors pointed at:

1. **Oversized-union starvation + always-fetch corruption.** When the first
   queued batch plus the always-fetch set exceeded the RPC limit, the old code
   rebuilt the shared `batch` set in place (`batch.clear()`), never dequeued
   the batch, and never scheduled its dispatch: the consumer's future hung,
   and `clearBatch`'s trailing trim then ran against a set whose always-fetch
   prefix was gone — permanently dropping most always-fetch keys from later
   cycles. The branch now builds its request key set separately, dequeues and
   dispatches the batch, and leaves the shared base intact. Pinned by
   `anOversizedFirstCycleServesTheBatchAndPreservesAlwaysFetch`.
2. **Sole-oversized-batch crash.** Dropping a mutated oversized batch ran
   `continue` straight into `iterator.next()` with nothing left, killing the
   polling loop with `NoSuchElementException`. The drop now falls back to the
   always-fetch base when the queue is empty. Pinned by the mutable-batch
   tests, which also cover the previously unreached WARN path.

New deterministic concurrency tests (state-sequenced, no timing guesses):
reactive fetchers park on the condition and wake on the queue signal; polling
fetchers wait quietly on an empty queue and pick up late work; the reactive
minimum delay separates cycles (lower-bounded timing only, so load cannot
flake it); a served unique consumer may re-queue (the guard clears). The slot
timestamp estimate is pinned against an 80ms round trip. Batching interior:
exactly-full batches are served not dropped, deferred batches don't block
later mergeable ones, the overlap scan runs the whole queue, dropped
oversized batches never reprocess, and a fetch failure logs
`Unexpected error fetching accounts` without leaking.

The top-up loop was subsequently reworked to a `spaceAvailable` countdown
(dedup-aware: counted from the set size after adding the batch's keys, so
duplicate keys in the caller's collection cannot over-reserve). The defensive
`hasNext` guard and its accepted-equivalent mutant are gone — the over-limit
precondition proves the iterator cannot run dry — and `currentBatchKeys`
aliases the freshly built set directly: it never escapes or changes after the
return, unlike `createBatchKeys`' snapshot of the shared mutable batch set.
Every mutant of the reworked loop is killed by the existing tests.

**Accepted equivalents:** `++numCallbacks` (293 — only its zero/nonzero
distinction is read);
the `size == MAX` overlap fast path (296 — the general merge loop converges
to the same key set for both subset and non-subset neighbors); the WARN-path
`clearBatch` (257 — later paths re-derive from key sets and the cycle-end
trim restores the base); the reactive zero-remaining re-arm (321) and
`unlock` in delay (329 — `await` releases and restores the full hold count,
masking the drift); the initial-delay recheck (341 — one extra sleep tick);
the in-lock reset recheck (383 — race-guard family).
`UniqueAccountBatchRecord.accept` (449) stays `NO_COVERAGE`: the dispatch
loop's `instanceof` branch always intercepts unique records, so the record's
own delegation is unreachable by design; it must exist to satisfy the
interface.

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

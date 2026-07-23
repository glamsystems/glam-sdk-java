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

Identical rows are sibling mutants of one compound condition — the comparison
is a multiset; never hand-dedupe the CSV. Pure line drift from editing a
mutated file passes on its own with a notice; refresh with
`-PupdateMutationBaseline` at a convenient moment. Anything beyond pure drift
(newly covered, unexplained, changed counts) is triage first, refresh after.

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
| 2026-07-23 (global config init paths) | 968 | 843 | 187 | 1237/2268 (54%) |
| 2026-07-23 (multiset migration) | 1030 | 843 | 187 | 1238/2268 (54%) |
| 2026-07-23 (kamino cache) | 1011 | 835 | 176 | 1257/2268 (55%) |
| 2026-07-23 (vault context + scope indexing) | 972 | 827 | 145 | 1295/2267 (57%) |
| 2026-07-23 (kamino cache lifecycle gates) | 969 | 827 | 142 | 1298/2267 (57%) |
| 2026-07-23 (synthetic direct-oracle feed) | 961 | 824 | 137 | 1306/2267 (57%) |
| 2026-07-23 (init/load + format runtime) | 853 | 686 | 167 | 1414/2267 (62%) |
| 2026-07-23 (cold start + integ tables) | 790 | 621 | 169 | 1477/2267 (65%) |
| 2026-07-23 (instruction processor) | 738 | 564 | 174 | 1529/2267 (67%) |
| 2026-07-23 (service context family) | 628 | 454 | 174 | 1639/2267 (72%) |
| 2026-07-23 (fulfillment services) | 499 | 318 | 181 | 1768/2267 (77%) |
| 2026-07-23 (cache run loops + io tails) | 330 | 131 | 199 | 1939/2269 (85%) |
| 2026-07-23 (init paths + remaining tails) | 272 | 66 | 206 | 1996/2269 (87%) |

The instruction-processor pass covers `InstructionProcessorImpl` against a
scripted `InstructionService` (each call's batch recorded, the next scripted
result returned): success drains the caller's list, size-limit failures on a
multi-instruction batch are **dropped, never retried here** — retries below
the send belong to the `InstructionService`, and a failed result means the
caller re-fetches and rebuilds; that intent is now stated in the code and
pinned by the tests, including the odd-rounds-up halving of the batch bound
governing the *remainder* — the account-64 splitter (duplicates counted
once, lookup-table keys counted against the limit, exactly-64 fits), the
fatal single-instruction-over-limit page with its `numTables` accounting,
the quiet stale-mint-price retry against its three near-misses (wrong code,
wrong program, non-custom error — each must page), and service failures
logged, paged and rethrown.

**Accepted (6):** record-pattern destructure sibling legs on the error
ladder, the `subList`-vs-whole-list boundary (same view, same drain), and
defensive forced-true directions with killed twins named by the verify.

The service-context-family pass covers the three context shells directly:
`ServiceContextImpl` (token typing across owner/length/extension-byte,
clock parsing from synthetic sysvar bytes, the `max(minDelay, backoff)`
sleep floor asserted by elapsed lower bounds in both directions, the
cache-path layout and every accessor including proxied
`NotifyClient`/`RpcCaller`/`DataSource` identities),
`ExecutionServiceContextImpl` (epoch median via a real `Epoch` record,
scripted `InstructionProcessor` returning true-then-false, and a proxied
`ServiceContext` reporting a *low* fee-payer balance — the only way to
tell the delegation from a hardcoded `false`, since the real impl
hardcodes it), the whole `BaseServiceContext` delegation surface, and
`IntegrationServiceContextImpl` with every collaborator a recording
`Proxy` stub (cache lookups return sentinels asserted by identity;
program-key accessors compared against the real `MAIN_NET` account
constants they unwrap).

The fulfillment pass covers the redemption stack end-to-end against a
synthetic staging `StateAccount` (the `priceSingleAssetVault` instruction
is staging-only) and a scripted `ExecutionServiceContext` whose fee-payer
script doubles as the run-loop exit. It surfaced and fixed a **real bug**:
`BaseFulfillmentService.executeRedemptions`'s soft-state/hard-fulfill
branch streamed the retained instructions into `fulFillInstructions::add`
— the immutable `List.of` *field* — instead of the local list it had just
allocated, so every counted fulfill threw `UnsupportedOperationException`
and killed the service loop. Covered: the run loop (missing-ATA wait,
low-fee-payer skip, NAV pricing with `supply≠holdings` so the divide and
both `stripTrailingZeros` calls are observable, failure backoff `1,2` then
reset-to-`1,1`), redemption accounting (seconds/slots/no-soft maturity,
the soft-flag conjunction including zero-share directions, all three
`executeRedemptions` instruction shapes byte-compared, `fetchAccounts`
refetch dropping vanished accounts), construction guards (NONE-mint and
mismatched-mint throws), `awaitChange` clamp-and-floor by elapsed bounds
(including a mid-wait wake proving the top-up is minimum-*minus*-slept),
and the websocket path: queue/deposit wake matrices by parked-thread
observation, foreign account shapes ignored without log noise, malformed
updates logged and swallowed, and the entrypoint monitor loop (services
executed, paced connection checks, close on interrupt).

**Accepted (7):** the `awaitChange` top-up boundary and its forced-true
twin (an equal or negative top-up is a no-op sleep — `TimeUnit.sleep`
ignores non-positive timeouts; `ORDER_ELSE` killed), the `validateMintKey`
null-mint leg (a null mint NPEs in `StateAccountClient` escrow-PDA
derivation before reaching the check, so only the NONE-sentinel leg is
reachable — both its directions killed), and four `compareAndSet` witness
retries (the re-loop only executes when another writer interleaves between
`get` and `compareAndExchange`; unreachable deterministically, and every
sibling leg is detected).

The init-paths pass closed the last broadly coverable surfaces:
`IntegLookupTableCache.initCache` (warm load from `.dat` files with foreign
files ignored, only the missing keys fetched — null and null-data entries
skipped — the fetched table persisted, and the does-not-exist warning
raised for exactly the still-missing key), `ReserveContext` (both null-key
spellings, the shared read/write meta caches served by identity through
the refresh sequence, and all four oracle layouts of
`refreshReserveAccounts`: scope-feed last slot, pyth first slot,
switchboard middle slots, and the no-oracle fatal),
`BaseDelegateServiceConfig.createServiceContext`/`createMintCache` from a
parsed properties config (no hikari files → null datasource; `mints.bin`
created under the cache directory), the default single-key
`AccountFetcher` queues and the default `InstructionProcessor` overload
(null lookup tables pinned through real implementing classes, since
proxies bypass `default` bodies), `persistGlobalConfig` and `FileUtils`
failure branches (occupied-directory targets fail the write *and* the
cleanup delete, both logged), `ScopeAggregateIndexes` statics,
`globalConfigCacheFile`, `MinGlamStateAccount` and serde-length tails.
Also fixed a test-infra trap this pass exposed: `GlobalConfigCacheTests`
attached its capturing handler to a `java.util.logging` Logger held only
by a local — JUL references loggers weakly, so GC could silently detach
the handler mid-run; the logger is now pinned by a static field.

**Accepted (7):** the `createServiceContext` hikari null/empty legs (both
mean "no datasource"; the non-empty direction needs real JDBC properties
to construct a `HikariDataSource`), the table-file filter NakedReceiver
(`path.toString()` ends with the same `.dat` suffix as
`getFileName().toString()` — indistinguishable by any filter input), the
two `ReserveContext` meta-cache hit legs (the static caches persist
across mutants in a shared PIT minion, so the hit path cannot be forced
to miss deterministically; both miss directions are killed), and the
`setScale` NakedReceiver (`setScale(decimals, DOWN).longValue()` is
`longValue()` for every input — DOWN and long truncation both round
toward zero).

The cache-run-loops pass swept the remaining poll/init surfaces and fixed
two more **real bugs**: `KaminoCacheImpl.persistReserve` dereferenced a null
`reserveDataFilePath`, so an RPC-only cache (built by the pathless
`KaminoCache.initService`) crashed its poll loop — and killed the run
thread — on the first feed-priced reserve it accepted (a null guard now
mirrors `deleteScopeConfiguration`'s); and `deleteScopeConfiguration`
deleted the *uncompressed* file names while everything is persisted
compressed, so a dropped Scope configuration resurrected from disk on the
next start (now deletes the `.dat.gz` names, pinned by the poll test).
Covered: the RPC-only Kamino init (feedless-reserve-only retention pinned,
five invalid-account rejections, `listenToAll` registration), the Kamino
poll loop end-to-end (a reserve arriving only once the loop runs — indexed,
notified, persisted; a fetched mappings update applying an appended oracle
entry; vanished scope accounts dropped with their compressed files, the
whichever-is-second bare-mappings deletion warning, and the fetch list
shrinking; write lock released on exit), listener routing (all three
`subscribeToAll` legs by event kind, full and single-key unsubscribes),
`refreshVaults`, the GlobalConfig run loop (delay-paced refetch, forced
refresh consumed-and-rearmed, invalidation exit, fetcher-failure log),
`awaitNewGlobalConfig` (timeout by elapsed bound, waiter woken by a
replacement), the batched accept (sentinel/null/empty entries, unknown and
already-cached mints never re-stored, the mismatched-mint invalidation
throw), the instance `initCache`, the new-asset mint fetch (queued only
when the mint cache lacks it), the StakePoolCache (cold start with
exact-minimum-length boundary, warm start from flat files without
refetching, accept gates, append-after-close rejection, pacing of the poll
loop over a window, failure log), and the FileUtils/MinGlamStateAccount
tails.

**Accepted (20):** four `HashMap`/`ConcurrentHashMap` capacity-hint math
mutants; the `deleteScopeConfiguration` null-path guard leg (deletion is
only driven through disk-backed caches; the deletion direction itself is
detected); GlobalConfig park-loop legs (`run` 228/230, `forceCacheRefresh`
double-check gates, `awaitNewGlobalConfig` timeout legs — in-lock timing
directions whose siblings are detected or timing-equivalent at exactly
zero nanos); StakePool cold-start overwrite/copy boundaries (`i > 0` and
`copyOfRange` at exact length are no-op-equivalent), the redundant
`containsKey` fast path ahead of `putIfAbsent`, and its CAS race-guard
leg; and the Kamino single-chunk fetch exit plus rebuild-list and
park-loop legs (`run` 664/669/692/697 — the chunking directions need more
than `MAX_MULTIPLE_ACCOUNTS` scope accounts to differ, the rebuild is
idempotent from the set, and the park directions are load-dependent).

The cold-start pass built the routed-proxy harness named as the previous
pass's escape: a `SolanaRpcClient` proxy answering `getProgramAccounts` by
target program (vaults, reserves, configurations — each request's data slice
and filters asserted inline) and `getAccounts` for the missing mappings.
`initService` from an empty disk now proves: reserves fetched and routed
(feed-priced, `NONE`-feed, and `nu11…`-sentinel-feed variants), the
configuration fetched, parsed and persisted, the missing mappings resolved
and persisted, and the resulting cache serving the full feed-indexed path —
with everything on disk for the next (warm) start, which the earlier pass
pins. `IntegLookupTableCacheImpl` is covered end-to-end: tables only grow
deeper (equal depth kept by identity), deactivated and vanished tables are
forgotten with their files deleted, grown tables re-persisted byte-exact,
the polling loop drives `queueBatchable` per pass, and persistence failures
log without dropping the in-memory update.

**Accepted (residual legs):** the merge-function and persist-gate directions
only a concurrent merge can distinguish (`integrationTables.merge` legs, the
`result == addressLookupTable` gate), the equal-depth boundary's sibling
directions, capacity-hint arithmetic in the init lambda, and the
partial-persistence fork halves (persisted configs that cover only some
needed feeds) — the one remaining init scenario, named as the next escape if
it ever earns a harness.

The init/load pass opened the service-runtime layer: `FormatUtil` end-to-end
(instruction/simulation/result rendering incl. the glam error-table lookup,
its unknown-code and non-custom fallbacks, sig null/blank forms, fee
stripping, indenting, durations, fixed-length strings), direct `AccountData`
discriminator/length gates, and `KaminoCache.initService` warm-start from
persistence: the synthetic feed and reserve seeded as LEGACY uncompressed
files are migrated (content round-tripped, originals removed), corrupted
files beside them deleted or skipped without failing the boot, the sole
network call is the sliced+filtered vault scan (request captured and
asserted), an invalid vault account fails the future loudly, and the
restored cache serves the full feed-indexed path and registers itself with
the account fetcher.

**Accepted (31 rows, init/load residuals):** the warm-path halves of the
cold/warm forks (`Files.exists`, `containsAll`, config-fetch-skipped legs)
and the reserve-request builder's fluent chain — observable only on a COLD
start that fetches reserves and configurations over RPC; that harness (a
multi-request routed proxy) is the named escape. The remainder are the usual
compound-condition sibling legs (each verify hint names the killed twin),
capacity-hint arithmetic, and defensive forced-true directions
(`compressIfNeeded` on an already-compressed file, constructor loop legs).

Remaining coverage debt is concentrated in the fulfillment services,
`ServiceContextImpl`, `InstructionProcessorImpl` and the entrypoints
(~300 mutants of service wiring needing stubbed RPC/websocket harnesses) —
run `./gradlew pitestServicesDebt` for the live ranking.

The direct-oracle-feed pass built the escape the feed-map acceptances had
named since the 6th pass: `KaminoCacheDirectFeedTests` synthesizes a second
scope feed — a zero-filled Configuration/OracleMappings pair with real
discriminators, direct SwitchboardOnDemand entries at chain indexes 11/12/13,
and the real SOL Reserve re-pointed at it by byte surgery. The feed-indexed
path is distinguishable from the raw-mappings fallback by liquidity (the
fallback reports zero; the feed path sums reserve collateral), which makes
the previously unobservable feed-map maintenance killable through the public
API: new reserves indexed on arrival and served depth-first, collateral
updates re-sourcing the by-mint entry, and structural chain moves replacing
it. `FeedIndexes.compareTo` (deepest feed wins the cross-feed sort) is pinned
directly.

The 2026-07-23 multiset migration added no new mutants: the verify's baseline
comparison became a multiset, materializing 62 sibling-mutant copies (same
`class,method,line,mutator` coordinates, distinct mutants of compound
conditions) that the old set-dedup had silently absorbed into their accepted
twins' rows. All 62 fall inside already-triaged families — the in-lock race
guards and the kamino null-key `createIfChanged` arms. Baseline row counts
now equal the report's unkilled counts exactly.

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

## Global config init paths pass (2026-07-23)

`GlobalConfigCache.initCache`'s three entry conditions are now all pinned: a
missing file goes to the RPC fetch (nested parents created, the fetched
config persisted and re-readable, every unknown mint queued to the account
fetcher, the map actually indexed); an empty persisted file is ignored in
favor of the fetch; and a fetched account with a foreign owner fails the
future with `Unexpected GlobalConfig Account`. A mint cache that already
knows every asset suppresses the mint fetch entirely — not even an empty
queue call. The RPC side runs through a real `RpcCaller` over a
Proxy-backed `SolanaRpcClient` (same harness as the fetcher tests), and the
`AccountFetcher` is a recording proxy.

The interface's file-load `createMap` is pinned by a synthesized config: the
fixture is all single-oracle assets, so the test demotes the first asset's
meta in place, appends a better-priority oracle for the same asset,
serializes the modified `GlobalConfig` through its generated `write`, and
persists it with `persistGlobalConfig` — the load path must index both
entries and serve the better priority first, which only its per-asset sort
can do.

## Kamino cache pass (2026-07-23)

Fixed the test harness before the mutants: `KaminoCacheTests.createCache` had
never created the persistence directories (production `initService` does), so
every persist quietly failed into a WARN — the stack trace repeated in every
PIT run, and the persistence mutants were unkillable by construction. With
the directories in place, persistence is asserted (mappings flat, reserves
under their market directory), the WARN path has its own broken-target test,
and the noise is gone at its source.

Killed ~20: the truncated-account guards on both dispatch paths (sub-8-byte
data is what stands between the length checks and an out-of-bounds
discriminator read — the existing 16-byte wrong-shape case couldn't see
them), the null-entry skip in the list path, configuration change
notification (the recording listener never overrode the change callbacks, so
every change event was invisible to every test), the rekeyed-duplicate drop,
the rekeyed-supersede teardown (`removeConfig` — a leftover registration
must not absorb the original key's re-acceptance), the same-slot vault gate,
and the reserves-only vault notification boundary (a fee change updates the
context silently; only allocation changes notify).

**Accepted:** `handleConfigurationChange` 275 EQUAL_IF — the in-lock
`putIfAbsent` double-check's converging direction, same race-guard family as
the existing `handleMappingChange`/`updateIfChanged` acceptances; its sibling
is killed by the rekeyed-duplicate test. The remaining KaminoCacheImpl
survivors are the previously documented families: in-lock rechecks, the
`signalAll`/`numReserveChanges` concurrency window (333), slot-gate shadowed
comparisons (457), capacity-hint arithmetic (103), and the `indexes`
fallback-scan block pending a second-feed fixture.

## Vault context + scope indexing pass (2026-07-23)

**The kamino null-key acceptance family is closed by kill, and its escape
note was wrong.** The family was accepted as "unreachable-in-harness; escape:
hand-building 62KB VaultState images" — but zeroing the 32-byte farm and
lookup-table keys in the existing mainnet fixture reaches every null-arm
directly. `KaminoVaultContextTests` now drives all four key transitions
(null→null reused, null→set, set→null, set→swapped), every compared field
through `createIfChanged` (value changes reuse untouched key objects by
identity — reparse-into-equal-copies is a mutant, not a refactor), and
reserve parsing: an independently counted stop-at-first-empty-slot oracle,
plus a fully packed allocation table with poisoned bytes *after* the table so
an off-by-one read cannot masquerade as the empty-slot terminator.

`ScopeFeedContext`: the by-mint liquidity order is pinned directly on both
the append and replace paths (the `indexes()` output could not see those
sorts — its own `FilteredReserve` sort re-derives the same order, which is
also why the `FilteredReserve.compareTo`/`sorted()` mutants are accepted
below). Boundary chain indexes (`== PRICE_INFO_ACCOUNTS_LEN`) are skipped by
both the indexer and remover rather than used as array positions; removing
the last reserve forgets the mint outright (no empty array left behind) and
a double remove is a no-op. `reIndexReserves` is pinned end-to-end: exactly
one rewrite when one reserve's chains changed, foreign-feed and
already-settled reserves untouched by identity, the count returned, and the
by-index slot serving the rewritten context. That test also proved the
`removePreviousEntry` call inside `reIndexReserves` redundant by
construction — `withPriceChains` never touches the configuration chain ints
that key the index maps, and both index paths replace in place — so the call
was **refactored away** rather than its mutant accepted.

**Accepted (mutual-redundancy family):** `FilteredReserve.compareTo` (244)
and the `indexes()` `sorted()` naked-receiver (266) — the source by-mint
array is maintained in the same liquidity order the stream sort would
impose, so removing either ordering is unobservable through `indexes()`;
the direct by-mint order tests pin the order itself. `removePreviousEntry`
212 pair — the `numReserves > 0` else-leg guards an empty by-index map that
is never stored (emptied maps are nulled). `indexReserveByIndex` 162/163 —
in-place-replace fast paths whose fallback copy path produces the same
served content. `parseReserveKeys`/`createIfChanged` residual legs are the
same short-circuit sibling family as elsewhere.

## Kamino cache lifecycle gates pass (2026-07-23)

Killed three: a changed configuration's teardown is now pinned by the key's
NEXT arrival (the change path removes the old registration without replacing
it, so a re-accept must register as NEW — a leftover stale entry would
swallow it as unchanged); a changed reserve at the SAME slot is stale by
identity; and null configuration/mappings persistence paths disable
persistence quietly instead of NPE-ing per accept (the reserves path has no
null guard and stays mandatory).

**Accepted — length guards subsumed by a length-safe discriminator
(`accept` 585–616, `acceptReserve` 572; 11 sibling rows):** the truncated
(3-byte) dispatch tests proved `DISCRIMINATOR.equals(data, 0)` returns false
on short data rather than reading out of bounds, so forcing any
`data.length == X.BYTES` guard true routes to a discriminator check that
rejects the account identically. The guards are pure fast-path routing —
HARDENING.md's canonical subsumed-guard family. The remaining
`updateIfChanged` rows (359/363/365/392 double-check guards, 395/398/399
feed-map maintenance) and `handleMappingChange`/`handleVaultStateChange`
residues are the previously documented in-lock, signalling, and
feed-map-unobservable families; the feed-map escape remains a second scope
feed fixture whose chains head with a direct oracle entry.

## Untriaged debt

The baseline was seeded with the full pre-existing survivor population when
the ratchet was adopted, per HARDENING.md's adoption path — **triage debt made
explicit, not acceptance**. For the current per-class ranking, run
`./gradlew pitestServicesDebt` — a hand-maintained list here goes stale the
same week it is written.

Shrinking the baseline is always an improvement; growing it requires a reason
written here.

## Triaged equivalent mutants (accepted with reasons)

Recorded inline in the dated pass sections above, as **bold family
paragraphs** next to the work that triaged them — each names the family, the
rows, the equivalence argument, and (where applicable) the escape that would
make the mutants killable. The recurring families here: in-lock race guards
(single-threaded tests cannot interleave a writer between an optimistic read
and its locked recheck), `signalAll`/waiter notifications needing a parked
thread to observe, fast-path count guards subsumed by later comparisons,
absent-vs-empty-parse equivalence in config sections, null-over-null assigns,
GC-hygiene calls, capacity-hint arithmetic, and unreachable-by-construction
defensive guards. New acceptances continue this pattern: document in the pass
section that does the triage, not here.

# Mutation-testing baseline & triage policy — `services`

`pitestServices` is this module's mutation suite; GLAM policy runs it and
`pitestServicesVerify` before any handoff whose changed code the suite
reaches. The suite's accepted baseline is `services-accepted.csv`, holding the
unkilled rows (`SURVIVED` and `NO_COVERAGE`) keyed by class, method, mutator
and status; its audited timeout set is `services-timeouts.csv`. Every row
triaged out of debt owes its written argument here. The canonical policy is
sava-build's `HARDENING.md`, and `hardeningHelp` is the authority on the
installed plugin's task names; this file records what is accepted *here* and
why.

A new unkilled mutant has exactly three legal outcomes:

1. **Kill it** — add or strengthen a test. Prefer asserting the property the
   mutant breaks over restating the implementation.
2. **Refactor** — restructure so the mutant cannot exist.
3. **Accept it knowingly** — record the reason under "Triaged equivalent
   mutants" below, give the row a short `# <family>` label named in the
   "Family labels" glossary, and write the record with the named task
   (`pitestServicesBaselineUpdate` / `Union` / `Prune` / `Rebase`), never
   by hand. Acceptance is for mutants *equivalent with respect to observable
   behavior*, not for "hard to test".

Identical rows are sibling mutants of one compound condition, not duplicates
to tidy: never hand-dedupe the CSV, and never hand-edit record structure or
provenance stamps. A row whose written argument here no longer fits the code
it names is re-argued before it is reused or removed; anything beyond that
(newly covered, unexplained, changed counts) is triage first, record
after. Any run that supports a record decision must be history-free
(`-PnoMutationHistory`).

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
| 2026-07-23 (delegate gate + init hygiene) | 255 | 57 | 198 | 2014/2269 (88%) |

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
detected); GlobalConfig park-loop legs (`run`'s invalidation exit on a
null update and its `remainingNanos <= 0 || forceRefresh` break,
`forceCacheRefresh` double-check gates, `awaitNewGlobalConfig` timeout
legs — in-lock timing directions whose siblings are detected or
timing-equivalent at exactly zero nanos); StakePool cold-start
overwrite/copy boundaries (`i > 0` and `copyOfRange` at exact length are
no-op-equivalent), the redundant `containsKey` fast path ahead of
`putIfAbsent`, and its CAS race-guard leg; and the Kamino single-chunk
fetch exit plus rebuild-list and park-loop legs (`run`'s chunk-loop
exit at `to == numAccounts`, its `accountsDeleted > 0` list rebuild,
and the `numReserveChanges > 0` and `remaining <= 0` park legs — the
chunking directions need more than `MAX_MULTIPLE_ACCOUNTS` scope
accounts to differ, the rebuild is idempotent from the set, and the
park directions are load-dependent).

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
state handling.

~~One behavior gap pinned by the fixture: the SOL reserve's price chain heads
with a `MostRecentOf` composite, and `ScopeFeedContext.indexes()` matches only
direct `OracleEntry`s.~~ **Closed 2026-07-24 (composite-chain support).**
`ScopeFeedContext.indexes()` now recurses through composite entries
(`MostRecentOf`/`CappedMostRecentOf`, `CappedFloored`, `Conditional`,
`MultiplicationChain`) and matches the requested oracle among a composite's
child prices, returning that child's own scope index. Confirmed against
`Kamino-Finance/scope` `most_recent_of.rs`: a composite reads
`oracle_prices.prices[source_index]` from already-refreshed data, so each
source (and cap/floor/refPrice bound) must be refreshed at its own index —
which is exactly the index the query returns. The real mainnet SOL chain (a
`MostRecentOfEntry` over a Chainlink at index 1 and a PythLazer at index 2)
is now served with the reserve's real liquidity instead of falling through to
the zero-liquidity mappings scan; pinned by `KaminoCacheTests` (real data) and
`ScopeCompositeIndexTests` (every composite shape, over hand-built graphs).
Accepted: the recursion's `index < visited.length` upper bound
(`# defensive bound` — a scope index never reaches the array length, so the
boundary is unobservable).

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
the next phase's work. That pass reported a load-dependent `TIMED_OUT`
population (135 as of 2026-07-26 — see the historical timeout snapshot
below for the per-row structural causes); per HARDENING.md, verify
solo-vs-gate before trusting any flip, and union only observed flips.

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

**`ScopeFeedContext.indexes` — dropped `.sorted()`** on the `FilteredReserve`
stream feeding its `limit(4)`. The stream sorts `FilteredReserve` by
collateral descending, but its source `reservesByMint` is *already* maintained
in that order: `resortReserves` sorts every mutation with
`RESERVE_CONTEXT_BY_LIQUIDITY`, which is the same
descending-unsigned-collateral order, and `Stream.sorted` is stable, so
reserves contributing several matching entries keep their encounter order
either way. Re-sorting an already-sorted source cannot change the result.
Killing it would mean breaking the invariant the rest of the class maintains.

**`KaminoCacheImpl.indexes` — dropped `.sorted()`** on the per-feed
`FeedIndexes` stream ahead of its `findFirst()`. This one picks the
highest-liquidity feed across *scope feeds*, so distinguishing it needs two
feeds whose reserves cover the same mint at different depths. The fixtures
hold a single feed (the klend one), so sorting one element is a no-op —
**unreachable in-harness**, not equivalent. The escape is a second
`Configuration` + `OracleMappings` snapshot (the hubble feed,
`ScopeFeedAccounts.SCOPE_MAINNET_HUBBLE_FEED`) plus reserves pointing at it;
add those and this becomes killable.

**`KeyedFlatFileImpl.deleteEntry` — dropped `mappedBuffer.force()`** after the
swapped-in last entry is written over the deleted slot. Durability only: the
swap is already visible through the same mapping and to every subsequent read
in the process, so no in-process assertion can see whether the pages were
flushed. Same family as the `force`/lock survivors already accepted for this
class.

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
`MathMutator` rows on its nine `result = 31 * result + ...` mixing statements,
each swapping a `31 *` for `31 /` or a `+` for a `-` in the accumulator chain.
`hashCode`'s only contract is that equal accounts hash equally, which every
one of these preserves, so nothing observable distinguishes them: a
different-but-still-well-distributed mixing constant is not a defect. The two
properties that *do* matter are asserted — equal accounts hash equally, and
accounts differing in any compared component hash differently — and those
killed the `return 0` mutant that the contract alone would have allowed.
Distinguishing the rest would mean asserting exact hash values, which pins an
implementation detail callers cannot depend on.

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

**Feed-map maintenance invisible through the cache API (`updateIfChanged`'s
changed path — its `feedContext == null` guard and its `resortReserves` /
`removePreviousEntry` / `indexReserveContext` calls; `reIndexReserves`'
price-feed-match and changed-price-chains guards)** — `resortReserves`,
`removePreviousEntry` and `indexReserveContext` maintain `ScopeFeedContext`'s
internal by-index/by-mint maps, and the cache exposes those only through
`indexes()`, which returns null for the fixture's SOL reserve (composite
`MostRecentOf` chain — see the 6th-pass note). **Unreachable in-harness with
the current fixtures**; the named escape is a reserve whose chain heads with a
direct oracle entry (a second feed snapshot, e.g. the hubble feed), at which
point these become killable and should be.

**In-lock recheck race guards (`handleMappingChange`'s in-lock `witness ==
null || witness.changed(accountInfo)` re-read, `updateIfChanged`'s `previous
!= witness` retry, `handleVaultStateChange`'s `kaminoVaultContext == previous`
recheck)** — double-checks between the optimistic read and the locked write;
single-threaded tests cannot interleave a concurrent writer between the two.
Deterministically forcing that interleaving is the concurrency-harness problem
ravina's triage README documents at length; accepted with that as the named
escape.

**Slot-gate shadowed comparisons (the `vaultStateContextMap.merge` remapping
lambda, boundary/order)** — the merge remapping picks the newer context, but
`handleVaultStateChange`'s `Long.compareUnsigned(previous.slot(), slot) >= 0`
early return already rejects non-newer slots before merge is reached, so the
remapping only ever sees a strictly newer value and its `>=`-vs-`>` boundary
cannot be observed. Defensive redundancy, equivalent in context.

**Remaining per-key `createIfChanged` internals (`KaminoVaultContext`'s
`createIfChanged` key-comparison branches and `noKeyChange`'s `previous ==
null` arm)** — the null-transition arms (a key appearing where none was, or
vanishing to the NULL sentinel). The fixture's keys are all present and real;
synthesizing null-key variants means hand-building 62KB VaultState images.
Accepted as unreachable-in-harness; escape: a fixture from a vault with an
unset farm/lookup-table key.
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
(`MinGlamStateAccount.createIfChanged`'s `sameAssets` and
`sameExternalPositions` count guards)** — `sameAssets` and
`sameExternalPositions` each open with `count == this.section.length &&
Arrays.equals(bytes...)`. Forcing the count operand true when the counts
differ changes nothing: the byte ranges are computed from each side's own
count, so `Arrays.equals` over ranges of different lengths returns false
immediately and the flag lands false either way. The count check is a
deliberate short-circuit that skips the byte compare — the same
fast-path-routing family as HARDENING.md's canonical example. The nine branch
mutants that *were* observable (per-section reuse vs reparse, the enabled
flip, and both immutable-base-field guards) are killed by identity assertions:
content equality cannot tell a reuse from a reparse, so the tests pin the
array instances.

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

**Null-state rechecks in `topPriorityForMintChecked` (the pre-lock and in-lock
`globalConfigUpdate == null || assetMetaMap == null` EQUAL pairs) and the
invalidation `invalidGlobalConfig.signalAll()`** — each `||` guard yields one
killable mutant per operand (killed by the decimals-mismatch throw test) and
one that only a concurrent invalidator between the read unlock and write lock
could observe — the same in-lock race-guard family as the KaminoCache
acceptances, with the same concurrency-harness escape. `signalAll` needs a
parked waiter to observe; same family as the `accept`-path
`invalidGlobalConfig`/`newGlobalConfig` `signalAll` acceptances.

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

**Absent-vs-empty-parse equivalents (the `parseProperties` section-presence
pairs)** — the always-parse direction on `notificationHooks`, `tableCache`,
`accountFetcher` and `defensivePolling`: parsing an empty section produces the
same value the absent path synthesizes (`NotifyClient.createClient([])`
returns the same noop shape as `setDefaults`; the other three parsers default
every field to exactly their `createDefault` values). No observable output
distinguishes them.

**Null-over-null assigns (`parseProperties`'
`minCheckStateDelay`/`maxCheckStateDelay` guards; `DefensivePollingConfig`'s
five `Parser.parseProperties` duration guards)** — `parseDuration(null)`
returns null, so forcing the `!= null` guard merely re-assigns null over null;
`get()`/`setDefaults` re-default nulls either way.

**True-or-throw returns (`FulfillmentServiceConfig.test`'s `super.test(...)`
fall-through)** — the base `test` either handles a field (returns true) or
throws on unknown fields, so forcing the propagated return to true is
indistinguishable.

**Missing-key delete fast path (`MintCacheImpl.delete`'s `removed == null`
guard)** — forcing the null-check false sends a missing key into
`deleteEntry`, which scans, finds nothing, returns 0 and yields the same null;
the guard only skips file I/O.

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

**Spurious-signal directions (`queue`'s signal gate, EQUAL_ELSE/ORDER_IF)**
— forcing the `isEmpty || pending.size() >= batchSize` signal condition true
adds a lock cycle and an extra signal to a runner that rechecks its guards
on wake; no observable difference exists.

**Fast-path skips (`awaitBatchComplete`'s outer `!batchComplete` check;
`run`'s `pending.size() < batchSize` fill/wait entry — boundary/ORDER_IF)**
— the outer `batchComplete` check only skips a lock acquisition around a
correctly-guarded while; entering the fill/wait block with a full batch
pending exits the delay window immediately. Both are flicker, not behavior.

**Zero-remaining re-arm (`run`'s batch-delay await window, boundary)** —
`remainingNanos > 0` vs `>= 0` differs only when a wait returns exactly 0,
which re-arms one zero-nanos await and exits on its negative return.

**Requeue gap guards — RESOLVED by fix.** The failed-batch walk used to break
at the first unset slot, and a multi-row `StatementPreparer` (the `int`
return contract allows it) left index gaps that silently dropped items from
the retry. `run()` now tracks items and rows separately: `batch[]` is indexed
densely by item, `numRows` drives the execute threshold, and the walk requeues
every slot below `numItems` unconditionally. Pinned by a two-rows-per-item
failure test (the whole batch retries) and a zero-rows-per-item test (the
`numItems == batch.length` guard prevents overflow when rows never
accumulate). The remaining `Arrays.fill` mutant (`run`'s pre-park
`Arrays.fill(batch, null)` batch reset) is now pure GC hygiene — it releases
references while the runner parks between cycles — and is accepted as
unobservable.

**Batch-length equality guard (`run` EQUAL_ELSE on `numRows >=
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

**Accepted equivalents:** `createBatch`'s `++numCallbacks` (only its
zero/nonzero distinction is read);
the `size == MAX` overlap fast path (the general merge loop converges
to the same key set for both subset and non-subset neighbors); the WARN-path
`clearBatch` in the oversized-batch drop (later paths re-derive from key
sets and the cycle-end trim restores the base); the reactive
`remainingAwaitNanos <= 0` re-arm and `unlock` in `delay` (`await` releases
and restores the full hold count, masking the drift); `run`'s initial
`queue.isEmpty()` delay check (one extra sleep tick); the in-lock
`currentBatch.isEmpty()` reset recheck (race-guard family).
`UniqueAccountBatchRecord.accept` stays `NO_COVERAGE`: the dispatch
loop's `instanceof` branch always intercepts unique records, so the record's
own delegation is unreachable by design; it must exist to satisfy the
interface.

**Follow-up (2026-08-21):** fresh certification after the Kamino/Scope move
exposed eighteen `AccountFetcherImpl` timeout instances whose only covering
paths entered the long-running poll loop. Direct finite batch-assembly tests
now pin exact selection, reset, top-up, deferral, oversized-drop isolation and
unique-claim release. Failure cleanup snapshots and clears the in-flight deque
under its lock before failing futures and re-queuing callbacks, so its progress
is finite and its lock/reset state is synchronously observable. Wrapped
interrupt cause chains are cycle-safe and tested in both direct and run-loop
paths. The result killed every new batching/failure candidate without adding a
baseline or timeout row.

This also corrects the historical WARN-path `clearBatch` equivalence above:
when a valid neighbor follows a caller-mutated oversized batch, omitting that
reset leaves the shared key set dirty and poisons the neighbor's assembly. The
older statement described the narrower no-neighbor fixture; the current
neighbor-isolation test kills the removal.

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

**Accepted:** `handleConfigurationChange` EQUAL_IF — the in-lock
`putIfAbsent` double-check's converging direction, same race-guard family as
the existing `handleMappingChange`/`updateIfChanged` acceptances; its sibling
is killed by the rekeyed-duplicate test. The remaining KaminoCacheImpl
survivors are the previously documented families: in-lock rechecks, the
`signalAll`/`numReserveChanges` concurrency window in `handleMappingChange`'s
re-index notify, `handleVaultStateChange`'s merge-remap slot comparison
shadowed by its same-slot entry gate, the constructor's `accountsNeededSet`
capacity-hint arithmetic, and the `indexes` fallback-scan block pending a
second-feed fixture.

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

**Accepted (mutual-redundancy family):** `FilteredReserve.compareTo`
and the `indexes()` `sorted()` naked-receiver — the source by-mint
array is maintained in the same liquidity order the stream sort would
impose, so removing either ordering is unobservable through `indexes()`;
the direct by-mint order tests pin the order itself. The
`removePreviousEntry` pair — the `numReserves > 0` else-leg guards an empty
by-index map that is never stored (emptied maps are nulled).
`indexReserveByIndex`'s `containsKey`/`size() == 1` singleton
in-place-replace fast paths, whose fallback copy path produces the same
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
(`accept`'s dispatch chain, `acceptReserve`; 11 sibling rows):** the truncated
(3-byte) dispatch tests proved `DISCRIMINATOR.equals(data, 0)` returns false
on short data rather than reading out of bounds, so forcing any
`data.length == X.BYTES` guard true routes to a discriminator check that
rejects the account identically. The guards are pure fast-path routing —
HARDENING.md's canonical subsumed-guard family. The remaining
`updateIfChanged` rows (its `putIfAbsent`, its in-lock
`witness == reserveContext` and `previous != witness` double-check guards,
and the `feedContext == null` / `onlyCollateralChanged` feed-map maintenance
legs) and `handleMappingChange`/`handleVaultStateChange` residues are the
previously documented in-lock, signalling, and
feed-map-unobservable families; the feed-map escape remains a second scope
feed fixture whose chains head with a direct oracle entry.

## Delegate gate + init hygiene pass (2026-07-23)

`SingleAssetFulfillmentServiceEntrypoint.validateDelegatePermissions` was
widened to package-private (same precedent as the package-private locks —
`createService` offers no seam for a stub client) and its nine mutants are
killed directly: a missing state account and an ungranted delegate are
refused **and reported** (LogCapture pins both ERROR lines; a misconfigured
delegate must never fail silently into a dead run loop), and the granted
delegate passes without noise. The remaining `main`/`createService`
`NO_COVERAGE` rows are config-driven bootstrap wiring — the config builds
its own RPC clients, so there is no injection seam; kill requires a seam
refactor (escape recorded here), not a cleverer test.

`KaminoCache.initService` hygiene, all through real `initService` runs over
Proxy-backed clients: corrupted mappings/reserve files are **deleted**, not
just skipped (`Files::delete` in `loadReserves` and `loadMappings` — a file
left in place is re-read and re-failed on every start); a stray plain file
among the market directories is skipped; an empty cold-start reserve scan
still creates the reserve directory (`loadReserves`'s
`Files.notExists(reserveDataFilePath)` branch — the guard is only reachable
when RPC returns zero reserves); a null slot in the configuration response is
skipped, not dereferenced; a missing mappings account fails init **by
name** (`Oracle Mappings account not found`, not an NPE downstream); and
warm on-disk configurations covering every fetched feed suppress the
configuration re-scan — which no-feed reserves (all-zero or nu11 sentinel)
must not defeat (the reserve scan's two `Arrays.equals` price-feed sentinel
checks: the mutant queues the sentinel as a real feed and forces the fetch,
which the proxy fails loudly).

**Accepted (families already documented):** the six `initService` capacity
hints (`MathMutator` on `newHashMap(n*3)` / `highestOneBit(n) << 1`), and
the nine residual operand legs across `initService`'s warm-configuration
gate (`containsAll(priceFeedsNeeded) && !feedContextMap.isEmpty()`), its
Configuration, OracleMappings and VaultState length/discriminator
validations, and its already-indexed reserve skip — each is the
forced-true direction of a guard whose observable sibling has a named
killing test in the verify hint; only an input that fails one operand while
already failing the other could distinguish them.

~~**Blocked note:** the coverage pass currently requires the local
`includeBuild("../ravina")`.~~ **Resolved:** ravina published the
`META-INF/services` entry (kms-core ≥ 25.5.2, BOM 25.28.3); the suite runs
green against published artifacts.

**Flip insurance:** `KaminoCacheImpl.persistReserve`'s
`Files.notExists(marketFilePath)` guard (`EQUAL_IF`) was pruned as killed in
one run and resurfaced `SURVIVED` in the next — the mutant forces
`createDirectories` on a directory that already exists, a
no-op, so its "kill" was load-dependent. Unioned back with a
`# flip insurance` label; do not prune it on a run that happens to detect
it.

## Untriaged debt

The baseline was seeded with the full pre-existing survivor population when
the ratchet was adopted, per HARDENING.md's adoption path — **triage debt made
explicit, not acceptance**. For the current per-class ranking, run
`./gradlew pitestServicesDebt` — a hand-maintained list here goes stale the
same week it is written.

Shrinking the baseline is always an improvement; growing it requires a reason
written here.

Row labels: back-filled 2026-07-23 from the pass sections above — every
`SURVIVED` row tied to a documented family carries its label, and everything
unattributable stayed `# untriaged` — the honest default; refine labels when
a row's family is pinned down. The untriaged rows are the real remaining
triage debt.

### 2026-08-21 — Kamino cache moved to vault-stat-service

The `systems.glam.services.integrations.kamino` and
`systems.glam.services.oracles.scope` packages (KaminoCache and its context
types) moved to the `vault-stat-service` repo, their only consumer, along with
their tests, fuzz harnesses (`scopeFeedContext`, `reserveContext`,
`kaminoVaultContext`) and seed corpora. Their 74 accepted rows and 6 audited
timeouts migrated verbatim into that repo's `kamino` suite
(`config/pitest/kamino-*.csv`), family labels and arguments included; the
dated pass sections below that argued them remain here as the historical
record. Families whose every member moved (`in-lock race guard`,
`mutual-redundancy family`, `single-feed unobservable`, `subsumed length
guard`, `residual sibling legs`, `capacity-hint`) stay named in the label
registry so those sections still parse.

### Family labels

Each accepted row carries a `# <family>` label whose argument is the pass
section above that triaged it; GLAM policy names every triaged label here in
the same change as the label, before the next `pitestServicesVerify` or
`pitestServicesDebt` run. The families:

- `# in-lock race guard` — an optimistic read rechecked under a lock; a
  single-threaded test cannot interleave a writer between the two.
- `# race-guard family` — the `GlobalConfigCacheImpl` variant of the above
  (null-state rechecks between an unlock and the write lock).
- `# signalAll waiter` — a `signalAll`/`await` notification only a parked
  thread could observe.
- `# subsumed length guard` — a `data.length == X.BYTES` guard whose forced
  direction routes to a length-safe discriminator check that rejects
  identically (accept-path dispatch).
- `# subsumed count guard` — a `count == section.length` short-circuit before
  an `Arrays.equals` over ranges computed from each side's own count.
- `# capacity-hint` — arithmetic sizing a `HashMap`/array capacity
  (`newHashMap(n*3)`, `highestOneBit(n) << 1`); no observable output.
- `# residual sibling legs` — the forced-true direction of a compound
  condition whose observable sibling has a named killing test.
- `# mutual-redundancy family` — `ScopeFeedContext` orderings the source array
  already maintains, so removing them is invisible through `indexes()`.
- `# single-feed unobservable` — the `indexes()` `.sorted()` over a single
  fixture feed: a no-op in-harness, killable with a second feed.
- `# hashcode mixing` — `MinGlamStateAccount.hashCode` mixing arithmetic;
  every mutant preserves the equal-hash contract.
- `# durability unobservable` — `force()`/`close()` durability calls no
  in-process assertion can see.
- `# accepted equivalent` — `AccountFetcherImpl`/`BatchSqlExecutorImpl`
  equivalents argued in their pass sections (spurious-signal directions,
  fast-path skips, GC-hygiene `Arrays.fill`).
- `# seamless bootstrap` — the fulfillment entrypoint's config-driven wiring
  with no injection seam; escape is a seam refactor.
- `# flip insurance` — a load-dependent kill unioned back after it resurfaced
  `SURVIVED`; never prune it (see the delegate-gate pass).

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

## Test-lifecycle contamination, and the survivor it manufactured (2026-08-06)

`KaminoCacheImpl.run` `VoidMethodCallMutator` (the "Scope OracleMappings
account has been deleted" warning) was reported `SURVIVED` and briefly
recorded here as a PIT coverage-attribution artifact. **That conclusion
was wrong.** The mutant is killed by
`KaminoCachePollingTests.thePollLoopAppliesUpdatesAndDropsVanishedScopeAccounts`;
what made it survive was the fixture leaking state between mutants.

`-PisolateMutants` gives the controlled evidence. Same scope, same history-free
run, only mutation-unit size differing:

| run | mutation test units | SURVIVED |
|---|---|---|
| normal batched | 1 | 41 |
| `-PisolateMutants` | 252 | **40** |

Exactly one mutant flipped, and it was this one. A single mutant changing status
purely because it stopped sharing a JVM with its neighbours is inter-mutant
contamination by definition.

The mechanism: the test started its poll thread and attached its `LogCapture`,
then ran every assertion *before* `runner.interrupt()` and `logs.close()`. PIT
runs many mutants in one minion JVM, so each of the ~200 mutants this test kills
left behind a still-polling cache thread and a still-attached log handler. A
later mutant's `assertLogged` then matched a record produced by a **previous**
test's leaked runner, so the removed log call was invisible. `LogCapture` also
collected records into a plain `ArrayList` while service threads published into
it concurrently.

Fixed by making the lifecycle unconditional (the runner is interrupted and
joined in a `finally`, the capture is a try-with-resources) and by making
`LogCapture` thread-safe (`CopyOnWriteArrayList`, idempotent `close()`). After
the fix the ordinary batched scoped run kills it — `testsRun=1`, killed by the
polling test — and batched now agrees with isolated at 40 survivors.

**The lesson generalises beyond this row.** Any fixture that cleans up after its
assertions rather than in a `finally` leaks into every later mutant in the same
minion, and the symptom is a survivor that no amount of reading the code
explains. Suspect fixture lifecycle before writing an equivalence argument, and
use `-PisolateMutants` to confirm — it is diagnostic evidence only and must
never support a baseline decision.

## Timed-out mutants (historical 135-row snapshot; reclassified 2026-08-06)

The discussion below is retained as historical evidence; it is not the current
audited set. The authoritative current inventory is `services-timeouts.csv`
(52 rows, all `cause:liveness`). The Kamino subsection likewise records
pre-move evidence; its six audited timeout keys moved to `vault-stat-service`
on 2026-08-21.

Per HARDENING.md: a timeout-detected mutant was observed for *slowness, not
wrongness* — the watchdog fires whatever the covering assertion says, so for
these rows the ratchet cannot see a weakened test. The compensating control
is this listing: an audited set, not a count, and a **new member outside
these families is something to look at**, not absorb. Membership churns with
load — `KILLED <-> TIMED_OUT` drift is benign (both are *detected*), and
`KeyedFlatFileImpl`'s member has moved between `appendEntry` and
`overwriteFile` across runs; this snapshot's own churn was 3 newly timed out
and 5 no longer. `SURVIVED -> TIMED_OUT` is the flip that matters; never
refresh those out on the strength of one loaded run.
Snapshot: the 2026-07-26 `qualityGate -PnoMutationHistory` run on plugin
21.5.15 — 135 rows.

All rows in this snapshot shared one meta-shape: mutants inside service loops and
lock/condition protocols, where the only observable failure is a thread that
stops making progress (or spins without it) until PIT's watchdog. Structural
causes by class:

### `db.sql.BatchSqlExecutorImpl` — 29

The producer-consumer batch window. Lost signals (`run`'s
`batchCompleteCondition.signalAll()`, `queue`'s `startWindow.signal()` and
`batchLimit.signal()` removed) park `awaitBatchComplete` callers forever;
inverted window gates (`run`'s `pending.size() < batchSize` refill gate, its
`while (pending.isEmpty())` wait and its `awaitNanos` bound loop,
`awaitBatchComplete`'s `!batchComplete` fast path and wait loop, `queue`'s
`isEmpty || pending.size() >= batchSize` and `isEmpty` signal gates) trap a
wait that no signal ends or turn the bounded delay window unbounded; removed
waits (`run`'s `startWindow.await()`, `awaitBatchComplete`'s
`batchCompleteCondition.await()`) become in-lock hot spins; a removed
`pending.addLast` in `queue` starves the consumer the test is awaiting; the
failure-requeue mutants (`run`'s `pending.addFirst(batch[i])` retry loop)
drop retried items the test waits to see durably inserted. Lock-call
removals (`awaitBatchComplete`'s `lock.lock()`) kill the waiting thread with
`IllegalMonitorStateException` under load-dependent timing. The batch
cursor's post-increment (`run`'s `batch[numItems++] = item` Increments,
admitted 2026-07-28 as a `KILLED <-> TIMED_OUT` drifter) mutated to a
decrement indexes `batch[-1]` on the second polled item; the
`ArrayIndexOutOfBoundsException` kills the executor thread outside the
`SQLException` requeue path, so `awaitBatchComplete` waiters never see
`batchComplete` and only the watchdog ends the test.

```
awaitBatchComplete EQUAL_ELSE x2; EQUAL_IF; VoidMethodCall x2
queue ConditionalsBoundary; EQUAL_ELSE; EQUAL_IF x2; ORDER_ELSE; VoidMethodCall x3
run ConditionalsBoundary; EQUAL_ELSE x2; EQUAL_IF x2; Increments; ORDER_ELSE x5; ORDER_IF x2; VoidMethodCall x3
```

### `rpc.AccountFetcherImpl` — 36

The harness drives `run()` deterministically and interrupts the thread on
its *final* batch — so any mutant that keeps the loop from consuming batches
in order also keeps the exit interrupt from ever firing. Starvation shapes:
removed enqueue/`signal` or mis-routed batches (`lockedQueue`'s
`currentBatchKeys.containsAll` overlap gate, its priority
`addFirst`/`addLast` routing and its `newBatch.signal()`; `queue`'s
`lockedQueue` dispatch and `validBatch` gate; `queueUnique`'s
`pendingUniqueConsumers.add` claim gate; `priorityQueue`'s and
`priorityQueueUnique`'s removed delegations to `queue`/`queueUnique`;
`validBatch` forced-false; `createBatch`'s 100%-overlap
`batch.containsAll` gate); removed loop exits (`queueBatchable`'s
`to >= numAccounts` chunk-loop exit, and the skipped chunk submissions
around it, starve downstream); trapped or unbounded waits in `delay` (the
reactive `awaitNanos`/`await` loops and the non-reactive `sleep` spin);
run-loop dispatch/reset guards (`run`'s null-batch and
`currentBatch.isEmpty()` reset legs) that leave `currentBatch` never
draining.

```
createBatch EQUAL_IF
delay EQUAL_ELSE x3; EQUAL_IF x2; ORDER_ELSE; VoidMethodCall x3
lockedQueue EQUAL_ELSE; EQUAL_IF; VoidMethodCall x3
priorityQueue VoidMethodCall
priorityQueueUnique VoidMethodCall
queue EQUAL_ELSE; VoidMethodCall x3
queueBatchable ConditionalsBoundary; ORDER_ELSE; ORDER_IF; VoidMethodCall x3
queueUnique EQUAL_ELSE x2; VoidMethodCall
run EQUAL_ELSE x4; VoidMethodCall
validBatch BooleanFalseReturnVals
```

### `fulfillment.SingleAssetFulfillmentService` — 21

Two shapes. (a) `accept`'s guards decide whether to `wakeUp()` the
fulfillment thread; a suppressed wake leaves it parked in `awaitChange`
while the test waits on fulfillment progress (`accept`'s `previousAmount`
comparison gate on the redemption leg, and the token-account length/owner,
mint, `compareUnsigned` and `outstandingShares().signum()` gates, including
both `wakeUp()` calls themselves). (b) The slot-ordered CAS loops: flipping
`witness == null` / `witness == previous` in `compareAndSet` turns a bounded
compare-and-exchange retry into an infinite spin; the two `NullReturnVals`
on its `BigDecimal.ZERO` and `previous.outstandingShares()` returns feed
`accept`'s `previousAmount` gates and suppress the wake the same way.

```
accept EQUAL_ELSE x6; ORDER_ELSE x2; VoidMethodCall x2
compareAndSet EQUAL_ELSE x6; EQUAL_IF x2; NullReturnVals x2; ORDER_ELSE
```

### `integrations.kamino.KaminoCacheImpl` — 21

The cache's `run()` loop and its lock discipline. Stalled chunk progression
(`run`'s `from + MAX` chunk arithmetic and its removed `to == numAccounts`
exit) makes the sublist walk infinite; the polling window (`run`'s
`awaitNanos` bound and its `numReserveChanges` change-count reset) turns
unbounded; removed accept/update/delete calls (`run`'s `accept`,
`updateIfChanged` and `deleteScopeConfiguration` calls, and
`deleteScopeConfiguration`'s own `removeConfig`) or forced deletion legs
(`deleteScopeConfiguration`'s `configurationsPath`/`mappingsPath` null
guards — the wrong leg NPEs the cache thread) leave the test looping on
state that will never arrive; a leaked read lock (`indexes`' removed
`unlock` in the finally) blocks the writer; the optimistic-recheck flip
(`updateIfChanged`'s `previous != witness` recheck) spins the retry loop
under the write lock; the rpc supplier `NullReturnVals` (`lambda$run$0` and
`lambda$run$1`, the `getProgramAccounts` sweep suppliers) kill the cache
thread through the `join`.

```
deleteScopeConfiguration EQUAL_ELSE x2; VoidMethodCall
indexes VoidMethodCall
lambda$run$0 NullReturnVals
lambda$run$1 NullReturnVals
run ConditionalsBoundary; EQUAL_ELSE x3; EQUAL_IF x2; Math; ORDER_ELSE; ORDER_IF; VoidMethodCall x4
updateIfChanged EQUAL_ELSE; EQUAL_IF
```

### `state.GlobalConfigCacheImpl` — 14

The refresh window and its waiters. A removed `priorityQueue` in `run`
never feeds `accept`, and its `globalConfigUpdate`/`assetMetaMap` null exit
gate plus the `remainingNanos <= 0 || forceRefresh` window bound either exit
the service early (the test then awaits updates that never come) or park it
unbounded; `forceCacheRefresh`'s double-checked `forceRefresh` gate and its
`invalidGlobalConfig.signal()` lose the early-break the test is waiting on;
`accept`'s `newGlobalConfig.signalAll()` and `awaitNewGlobalConfig`'s
elapsed-bound `awaitNanos` loop are the waiter side of the same protocol.
`topPriorityForMintChecked`'s removed `readLock.unlock()` in the finally
leaks the read lock: the decimals-mismatch path then parks the same
thread on `writeLock.lock()` (a read→write upgrade is impossible on a
`ReentrantReadWriteLock`), and every later writer parks behind the leaked
hold (admitted 2026-07-29, first surfaced by `-PstrictTimeoutAudit` under
gate load; a KILLED↔TIMED_OUT drifter of the leaked-unlock family).

```
accept VoidMethodCall
awaitNewGlobalConfig EQUAL_IF; ORDER_ELSE
forceCacheRefresh EQUAL_IF x2; VoidMethodCall x2
run EQUAL_ELSE x2; EQUAL_IF; ORDER_IF; VoidMethodCall x2
topPriorityForMintChecked VoidMethodCall
```

### `fulfillment.BaseFulfillmentService` — 5

The await/wake protocol itself: a removed `stateChange.signalAll()` in
`wakeUp` is a lost wake-up; removed `lock`/`unlock` pairs (`wakeUp`'s and
`awaitChange`'s `lock.lock()`/`lock.unlock()`) either leak the lock every
later locker blocks on or kill the service thread with
`IllegalMonitorStateException` mid-await.

```
awaitChange VoidMethodCall x2
wakeUp VoidMethodCall x3
```

### `state.MinGlamStateAccount` — 2

The length-prefixed byte walk. `delegateAclsOffset` and
`externalPositionsOffset` each iterate `for (j = 0; j < len; ++j)` over a count
read from the account; `RemoveConditionalMutator_ORDER_ELSE` removes that outer
loop's exit jump, so the walk never terminates. Both are synchronous pure
computations reached directly from `createIfChanged` — no fixture deadline could
fail first, no clock or budget reaches the mutated path, and the method never
returns, so there is no synchronous state to read. The watchdog is the only
possible detector.

`delegateAclsOffset` first appeared under gate load (2026-08-06 certification,
both suites serialized) while staying quiet on solo runs — the audited set is
per-key, not per-load, so it is a member regardless of which load surfaces it.

`createRecord`'s `Math` member and `externalPositionsOffset`'s `ORDER_IF` were
retired after the O(1) permission-block fix: the walk no longer iterates an
unvalidated count one constant-sized step at a time, so those paths are finite
and now die deterministically.

```
delegateAclsOffset ORDER_ELSE
externalPositionsOffset ORDER_ELSE
```

### Singles — 8

- `ServiceContextImpl.executeTask`, `execution.BaseServiceContext.executeTask`
  (`VoidMethodCall`) — the removed call *is* the task submission; the test
  awaits the task's effect and only the watchdog can end that.
- `fulfillment.SingleAssetFulfillmentServiceEntrypoint.run`
  (`VoidMethodCall`) — the two removed `executorService.execute` calls never
  start the epoch-info and fulfillment sub-services the test awaits; the
  removed `Thread.sleep(3_000)` turns the `checkConnection` loop into a busy
  spin.
- `integrations.IntegLookupTableCacheImpl.run` (`VoidMethodCall`) —
  removed `queueBatchable` starves the fetch loop; removed sleep spins it;
  either way the driver never reaches its terminal interrupt.
- `io.KeyedFlatFileImpl.overwriteFile` (`VoidMethodCall`) — the removed
  call is `lock.unlock()` in the finally: the leaked lock blocks every
  subsequent operation on the file (the "leaked unlock" shape verbatim).

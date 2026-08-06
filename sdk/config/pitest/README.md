# Mutation-testing baseline & triage policy — `sdk`

Each `pitest<Suite>` run is finalized by `pitest<Suite>Verify`, which diffs the
run's unkilled mutants (`SURVIVED` and `NO_COVERAGE`) against the accepted
baseline in `<suite>-accepted.csv` and **fails on anything new**. Baseline keys
are line-less (`class,method,mutator,STATUS`); `# line` tags are review
metadata, so source movement alone churns nothing. The canonical policy is
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
   (`pitestSdkBaselineUpdate` / `Union` / `Prune` / `Rebase` as the verify's
   hint directs). Acceptance is for mutants *equivalent with respect to
   observable behavior*, not for "hard to test".

Identical rows are sibling mutants of one compound condition — the comparison
is a multiset; never hand-dedupe the CSV, and never hand-edit record structure
or provenance stamps. A new mutant replacing a killed one at the same key can
inherit its acceptance, so treat a line-drift advisory whose written argument
no longer fits the code as that swap until shown otherwise. Anything beyond
drift (newly covered, unexplained, changed counts) is triage first, record
after. Any run that supports a record decision must be history-free
(`-PnoMutationHistory`).

## Suite

One catch-all suite, `pitestSdk`, targeting `systems.glam.sdk.*` by wildcard
with exclusions rather than an allowlist, so a new hand-written class is
mutated by default rather than silently skipped. Excluded: generated
`idl.**.gen.*` code (correctness belongs to idl-src-gen; mutating the
boilerplate would bury the hand-written signal) and test sources sharing the
recompiled root. `build.gradle.kts` is the authoritative definition.

## Baseline composition

| Date | Rows | `NO_COVERAGE` | `SURVIVED` | Killed |
|---|---|---|---|---|
| seeded 2026-07-21 | 627 | 614 | 13* | 22/688 (3%) |
| 2026-07-21 | 447 | 423 | 24 | 208/688 (30%) |
| 2026-07-21 (2nd pass) | 388 | 359 | 29 | 287/703 (40%) |
| 2026-07-21 (3rd pass) | 340 | 305 | 35 | 338/703 (48%) |
| 2026-07-22 | 251 | 236 | 15 | 429/703 (61%) |
| 2026-07-23 (multiset migration) | 292 | 277 | 15 | 456/748 (60%) |
| 2026-07-23 (vault table builder) | 221 | 191 | 30 | 527/748 (70%) |
| 2026-07-23 (kamino lend + fetch) | 169 | 143 | 26 | 579/748 (77%) |
| 2026-07-23 (interface defaults + proxy + pricing) | 62 | 36 | 26 | 688/750 (92%) |
| 2026-07-23 (findings fixed, main() removed) | 38 | 13 | 25 | 690/728 (94%) |

The vault-table-builder pass closed the long-standing `VaultTableBuilderImpl`
add\* block using the kamino mainnet snapshots shared from the services suite
(see `src/test/resources/accounts/kamino/README.md`): the glam vault/mint
account surface, ATA derivation gated to token-program-owned accounts (2022
included, escrow ATA only for the base asset), the kamino vault collection
phase (vault surface keys, allocation reserves and the vault lookup table
queued for the second fetch), and the second phase end-to-end — reserve +
market + the matching mainnet scope feed's prices *and* mappings accounts,
with the vault's lookup table mapped past null and unrelated entries and
registered under the vault key. A mintless state skips the whole mint
surface.

~~**Finding: the system program can never join the table.**~~ **Fixed
2026-07-23:** `addGlamVaultAccounts` used to route the system program
through `addAccount`, whose `PublicKey.NONE` sentinel filter is the same
all-zero key — a silent no-op. It now adds the system program directly
(always a real account), the test asserts it lands, and the 188
`VoidMethodCallMutator` mirror acceptance is killed with it.

**Accepted (residual sibling legs, 15):** forced-true directions of
null-guards and short-circuit operands across the add\* branch chains —
every row's verify hint names the killing test of its observable sibling;
the surviving leg is the direction only a sentinel-colliding or
already-guarded input could distinguish. Same family as the services-side
compound-condition acceptances.

The kamino-lend + fetch pass (2026-07-23) closed the remaining
`VaultTableBuilderImpl` collection paths — 52 baseline rows dropped by the
first shrink-only prune. The obligation fixture is synthesized
(a zero-filled `Obligation` image with the real discriminator, market and
deposit/borrow reserve keys written at the generated offsets; empty slots
hold the all-zero key the collectors must filter as `NONE`), the deposit
reserve is the shared mainnet SOL reserve snapshot, and the borrow reserve
serves the same reserve image under its own key so both mapMulti loops are
independently observable. Covered: `addKaminoLendAccounts` (obligation
registration past wrong-owner/length/discriminator/null accounts, market +
market-authority PDA, main-market table registration),
`addKaminoAccountsSecondPhase` (reserve surface: liquidity mint, collateral
supply vault and mint; unreferenced reserves skipped),
both `removeKamino*TableAccounts` paths, `addJupiterSwapAccounts`,
`fetchGlamVaultTables` (Proxy-backed `SolanaRpcClient`; the ALT program and
the exact active + prefix-memcmp filter pair are pinned via `toJson`), the
token-2022 position leg of `addKaminoVaultAccounts`, and three previously
accepted survivors now killed: the second-table free-space derivation in
`batchTableTasks` (a 40-account roll across two partially-filled tables),
the absent-vault-table guard (a null entry must not register), and the
unknown-scope-feed guard (no scope accounts for a re-pointed reserve). The
newly-covered length-guard leg (275 `EQUAL_IF`) was killed rather than
accepted as subsumed: unlike the services dispatch guards, a truncated
account with a *valid* discriminator routes into `Obligation.read` and an
out-of-bounds read — the guard is load-bearing; the test carries that input.

~~**Remaining `VaultTableBuilderImpl` debt is `main()`.**~~ Resolved
2026-07-23 by refactor-out-of-existence: the untestable scratch `main()`
(null-`Signer` NPE, live RPC) was removed from the class, taking its 23
`NO_COVERAGE` rows and ~22 mutants with it.

The interface-defaults + proxy + pricing pass (2026-07-23, later) closed most
remaining `NO_COVERAGE` blocks — 108 baseline rows dropped:

- **`VaultTableBuilder` interface defaults + `Builder`**: the full pipeline
  (fetch → ACL-gated adds → second-phase fetch → external-table removals)
  driven end to end through `Builder.create` on a fully-enabled state, with
  a disabled-state twin asserting every gated phase stays silent. The
  fetch defaults pin the exact requested key lists; the removal default
  seeds keys covered by both registered tables plus one that must survive.
- **`GlamVaultAccounts`**: `loadMappingConfigs` against a temp directory
  holding a valid config, a wrong-extension file, an unreadable `.json`,
  and a *directory named* `nested.json` (the regular-file filter is what
  stands between it and a crash in the parser); both `createMapper`
  overloads. The mapping-config JSON is inlined so tests never depend on
  the untracked `glam/` download.
- **`proxy.CachedDynamicGlamAccountFactory`**: every dynamic-account name
  routed through `setAccount` into a live array (each slot must hold
  exactly the meta the name stands for), unknown/null names rejected, and
  the cache pinned by identity across equal configs.
- **`GlamAccountClient(+Impl)` pricing family**: all thirteen convenience
  overloads equal their no-CPI form (this family produced the real
  dropped-oracle-keys bug), the four production `cpiEmitEvents` branches
  swap the program slot for the mint event authority, staging-only methods
  driven through the staging client; plus `createAccount`,
  `createAccountWithSeed`, the escrow ATA and `updateState` wiring.
- **`GlamJupiterProgramClient(+Impl)`**: every swap convenience overload
  equals its fully-explicit form, program-state keys survive the
  delegation hops *and* reach the CPI, the route's accounts ride as extra
  accounts (a dropped `extraAccounts()` result loses the route — the
  wrapSOL variant of this was a real bug), and the program-state variants'
  wrap gate fires only for a wSOL input with `wrapSOL=true`.

**Accepted:** `loadMappingConfigs` 55 `NakedReceiverMutator` — replacing
`getFileName().toString()` with `path.toString()` cannot change an
`endsWith(".json")` test, because a path's string form always ends with its
filename's string form. Equivalent by construction.

~~**Finding: `addKaminoVaultAccounts` crashes on mint accounts.**~~ **Fixed
2026-07-23:** it called `TokenAccount.read` on every token-program-owned
response entry with no shape guard, but the first phase always fetches the
state's *mints* (token-program-owned, 82 bytes — shorter than a
`TokenAccount`), so any kamino-vaults-enabled run would have thrown
`IndexOutOfBoundsException` on the first mint. A `TokenAccount.BYTES`
length guard now skips non-token-account shapes; the pipeline and
vault-collection tests serve real 82-byte mint entries through it.

The multiset migration added no new mutants: the verify's baseline comparison
became a multiset (one row per sibling mutant of a compound condition, not one
per unique row text), materializing previously-absorbed sibling copies. All
fall inside already-triaged rows; baseline counts now equal the report's
unkilled counts exactly.

The 2026-07-22 pass covered `GlamStagingAccountClientImpl` /
`StagingStateAccountClientImpl` (every staging pricing method's event-authority
branches, staging token/fulfill routing, and state-client construction from the
real staging fixture including the skipped drift ACL), killed the
`GlamAccountsBuilder` setter survivors by exercising all seventeen setters from
a `@Test` (static-initializer coverage attribution is unstable), the
`fixCPICallerRights` no-signer loop-boundary mutants, and the wrap-condition
operand mutants in the jupiter swap paths.

The 3rd pass covered `idl.programs.glam.jupiter.*` — `fixCPICallerRights`
(first-signer stripping), the jupiterSwapV2 CPI wiring with and without the
quote-price check, wrap-SOL and create-ATA branches, and the swap-token-account
maps.

*the seed run reported 52 survived raw; 13 unique rows after dedup by
`class,method,line,mutator,status` — the builder's repeated setter shapes
collapse.

The 2026-07-21 pass covered the value layer (`GlamUtil`, `GlamEnv`,
`Protocol`, `GlamAccounts` + builder + record, `GlamVaultAccounts`) and the
production client (`GlamAccountClient` statics, `GlamAccountClientImpl`
instruction wiring, `StateAccountClient`/`StateAccountClientImpl`/
`BaseStateAccountClient`). The `SURVIVED` count *rose* because previously
uncovered code is now executed; the two triaged rows are below, the rest of
the 24 are untriaged survivors in still-partially-covered classes.

## EXPERIMENTAL_NAKED_RECEIVER trial (2026-07-22)

Trialled per sava-build's HARDENING.md and **kept**, since it fires here:

| Suite | Mutants | Detected | New unkilled |
|---|---|---|---|
| `sdk` | 703 -> 748 (+45) | 429 -> 456 (+27) | 18 |

All 18 new rows are `NO_COVERAGE` in classes that already carry untriaged debt
(`VaultTableBuilderImpl`, the staging state client, `proxy`); the mutator added
no new survivors, so nothing here needed triage. Roughly a third of the new
mutants were killed outright by existing tests.

## Row labels (2026-07-23)

Baseline rows now carry the family label the acceptance belongs to
(`# residual sibling legs`, `# unreachable type-check arm`,
`# equivalent path-suffix`), with the full argument in the pass sections
above; everything else is `# untriaged`. The verify and debt tasks print the
per-label counts, and refreshes seed `# untriaged` on new rows — triage
means replacing the label.

## Untriaged debt

For the current per-class ranking, run `./gradlew pitestSdkDebt` — a
hand-maintained list here goes stale the same week it is written. What the
task cannot tell you is *why* blocks are still open:
`VaultTableBuilderImpl.main` is untestable scratch pending relocation (see
the kamino-lend pass above); the `VaultTableBuilder` interface and
`GlamAccountClientImpl` / jupiter-client `NO_COVERAGE` blocks are the
remaining mechanical work.

The baseline was seeded with the full pre-existing survivor population when
the ratchet was adopted, per HARDENING.md's adoption path — triage debt made
explicit, not acceptance. Shrinking the baseline is always an improvement;
growing it requires a reason written here.

## Triaged mutants (accepted with reasons)

### ~~`BaseStateAccountClient.delegateHasPermissions` — `MathMutator`~~ — resolved 2026-07-21

The semantics question was decided: the conventional direction (every
*required* bit must be granted, `(required & granted) != required`), with
misses — an absent integration entry or protocol entry — returning false
rather than throwing. The code was fixed accordingly, subset-mask tests were
added, and the mutant is killed. No acceptance remains.

### `StateAccountClientImpl.protocolBitmask:88` — `RemoveConditionalMutator_EQUAL_IF`

`integrationAclMap.get(..) instanceof IntegrationAcl(_, bitmask, _)` compiles
to a null check plus a type check; the mutated type-check arm is unreachable
in context because the map's values are always `IntegrationAcl` — the only
observable branch is the null (absent program) case, which is covered. The
staging twin (`StagingStateAccountClientImpl.protocolBitmask`) will earn the
same acceptance when its class is covered.

## Timed-out mutants (audited set, 2026-07-26)

For exactly these mutants the ratchet cannot see a weakened covering assertion —
a timeout keeps "detecting" whatever the test asserts — so each member carries an
admissible cause, and only `cause:liveness` may remain in the set: the mutated
path must have no path-owned finite completion guarantee. `KILLED <-> TIMED_OUT`
drift is benign (both are detected, neither is ever baselined); `SURVIVED ->
TIMED_OUT` is the flip the verify names separately.

All five members below qualify on the strict reading, and the reason is worth
stating because it is what separates them from a timeout that is really a
harness artifact: `batchTableTasks` is a synchronous pure computation. The test
calls it directly, so there is **no fixture deadline that could have failed
first**, no clock or budget the mutated path receives, and no synchronous state
reader — the method simply never returns. The watchdog is the only possible
detector. Contrast the `services` suite, where several timeouts came from
fixtures whose own deadline outlived PIT's watchdog budget; those were bounded
failures being relabelled, not liveness, and were fixed by shortening the
fixture rather than by classifying the mutant.

Classified 2026-08-06; observed on a history-free `pitestSdk` run against
sava-build `0.0.0-test`.

### `lut.VaultTableBuilderImpl.batchTableTasks` — 5

All five are the same structural cause: the chunking loop's index `i`
advances only through the per-chunk inner loops, so any mutant that stalls
chunk formation makes `batchTableTasks` never return. `:119` (the outer
`i < accounts.length` bound), `:138` (`to = i + add` arithmetic and the
inner-loop bound — a non-positive `add` yields an empty chunk and `i` stops
moving), and `:158` (the `tableSpace == 0` rollover — skipping it drives
`tableSpace` negative, so every later `Math.min(tableSpace, ...)` chunk is
empty). Watchdog-detected infinite loops, not load-slowed kills — expected
to be stable members.

```
batchTableTasks:119 ConditionalsBoundary; :119 ORDER_IF; :138 Math; :138 ORDER_ELSE; :158 EQUAL_ELSE
```

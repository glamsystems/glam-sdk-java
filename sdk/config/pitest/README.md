# Mutation-testing baseline & triage policy — `sdk`

`pitestSdk` is this module's mutation suite; GLAM policy runs it and
`pitestSdkVerify` before any handoff whose changed code the suite reaches. The
suite's accepted baseline is `sdk-accepted.csv`, holding the unkilled rows
(`SURVIVED` and `NO_COVERAGE`) keyed by class, method, mutator and status; its
audited timeout set is `sdk-timeouts.csv`. Every row triaged out of debt owes
its written argument here. The canonical policy is sava-build's
`HARDENING.md`, and `hardeningHelp` is the authority on the installed plugin's
task names; this file records what is accepted *here* and why.

A new unkilled mutant has exactly three legal outcomes:

1. **Kill it** — add or strengthen a test. Prefer asserting the property the
   mutant breaks over restating the implementation.
2. **Refactor** — restructure so the mutant cannot exist.
3. **Accept it knowingly** — record the reason under "Triaged equivalent
   mutants" below, give the row a short `# <family>` label named in the
   "Family labels" glossary, and write the record with the named task
   (`pitestSdkBaselineUpdate` / `Union` / `Prune` / `Rebase`), never by
   hand. Acceptance is for mutants *equivalent with respect to
   observable behavior*, not for "hard to test".

Identical rows are sibling mutants of one compound condition, not duplicates
to tidy: never hand-dedupe the CSV, and never hand-edit record structure or
provenance stamps. A row whose written argument here no longer fits the code
it names is re-argued before it is reused or removed; anything beyond that
(newly covered, unexplained, changed counts) is triage first, record
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
| 2026-09-24 (lut package removed) | 24 | 13 | 11 | 464/488 (95%) |
| 2026-09-25 (ix-mapper mapping documents) | 22 | 12 | 10 | 454/476 (95%) |

The 2026-07-23 vault-table-builder, kamino-lend + fetch and findings-fixed
passes covered `lut.VaultTableBuilderImpl`, the vault address-lookup-table
builder, against kamino mainnet snapshot fixtures, fixed two findings there
(the system program could never join a table; the kamino-vault collection
would have crashed on the state's mint accounts), and removed its untestable
scratch `main()`. The whole `lut` package, its tests and those fixtures were
deleted on 2026-09-24 (see "`lut` package removed (2026-09-24)" below); the
pass notes, findings and acceptance argument are in this file's history.

The interface-defaults + proxy + pricing pass (2026-07-23, later) closed most
remaining `NO_COVERAGE` blocks — 108 baseline rows dropped:

- **`VaultTableBuilder` interface defaults + `Builder`** (the `lut` package,
  deleted 2026-09-24).
- **`GlamVaultAccounts`** (since removed, 2026-09-25): `loadMappingConfigs`
  against a temp directory holding a valid config, a wrong-extension file, an
  unreadable `.json`, and a *directory named* `nested.json` (the regular-file
  filter is what stood between it and a crash in the parser); both
  `createMapper` overloads. The mapping-config JSON was inlined so tests never
  depended on the untracked `glam/` download.
- **`proxy.CachedDynamicGlamAccountFactory`** (since removed, 2026-09-25):
  every dynamic-account name routed through `setAccount` into a live array
  (each slot must hold exactly the meta the name stands for), unknown/null
  names rejected, and the cache pinned by identity across equal configs.
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

**Accepted** (row pruned with the method on 2026-09-25): `loadMappingConfigs`'s
`.json` suffix filter, `NakedReceiverMutator` — replacing
`getFileName().toString()` with `path.toString()` cannot change an
`endsWith(".json")` test, because a path's string form always ends with its
filename's string form. Equivalent by construction.

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
(`# unreachable type-check arm`; `# equivalent path-suffix` until its only
row left with `loadMappingConfigs` on 2026-09-25, see below), with the full
argument in the pass sections above; everything else is `# untriaged` —
triage means replacing that label with the family the row's argument belongs
to.

## `lut` package removed (2026-09-24)

sava now supports v1 transactions, which need no address lookup tables, so
the vault lookup-table builder for v0 transactions went: the whole
`systems.glam.sdk.lut` package, `VaultTableBuilderTests`, the kamino snapshot
fixtures under `src/test/resources/accounts/kamino/`, and the vault-table
filters of `GlamVaultAccounts`. The fresh history-free `pitestSdk` observation
that day (488 mutants, 464 detected, no fresh rows) left 14 accepted rows
unmatched, and `pitestSdkBaselinePrune` removed exactly those the same day
(baseline 38 -> 24): 13 `lut.VaultTableBuilderImpl` and 1
`lut.VaultTableBuilder$Builder`, all `# residual sibling legs`. No other row
carried that family, so it left the label list above; it is named here as
history. The audited timeout set's five members, all
`lut.VaultTableBuilderImpl,batchTableTasks` (`cause:liveness`), were retired
from `sdk-timeouts.csv` as stale, leaving the set empty. The only other
baseline movement was pure line drift on the `# equivalent path-suffix` row.

## Untriaged debt

For the current per-class ranking, run `./gradlew pitestSdkDebt` — a
hand-maintained list here goes stale the same week it is written.

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

### `StateAccountClientImpl.protocolBitmask` — `RemoveConditionalMutator_EQUAL_IF`

`integrationAclMap.get(..) instanceof IntegrationAcl(_, bitmask, _)` compiles
to a null check plus a type check; the mutated type-check arm is unreachable
in context because the map's values are always `IntegrationAcl` — the only
observable branch is the null (absent program) case, which is covered. The
staging twin (`StagingStateAccountClientImpl.protocolBitmask`) will earn the
same acceptance when its class is covered.

## ix-mapper mapping documents (2026-09-25)

ix-mapper-java 25.1.0 replaced its index-map configs with the mapping
documents ix-mapper-ts generates (`src/generated/mapping/<environment>/`),
and with them the whole `DynamicAccount` factory API. The sdk's side shrank
to two seams: `GlamAccounts.createMapper` builds an ix-proxy
`InstructionMapper` from one environment's documents and refuses another
environment's, and `GlamVaultAccounts.mappingContext()` hands the mapper a
vault's state, vault and fee-payer keys plus the integration-authority lookup.
Deleted: `GlamVaultAccounts.loadMappingConfigs` and both `createMapper`
statics, the `systems.glam.sdk.proxy` package (`DynamicGlamAccountFactory`,
`CachedDynamicGlamAccountFactory`, the five `Indexed*` records) with
`CachedDynamicGlamAccountFactoryTests`, and the `fuzzMappingConfig` target
with its corpus — the sdk parses no external input of its own any more;
ix-proxy's `fuzzMappingConfig` and `fuzzIxMapper` cover the parser and the
mapper. The passages above that argued `loadMappingConfigs` and the proxy
factory stand as dated history.

The new seams are covered by `GlamAccountsMapperTests` (mapper built from the
system-program documents, then checked-in copies under
`src/test/resources/mapping/`, since the tracked `ix-mapper-ts/` set (see
"Embedded mapping documents" below); environment guard both ways, ix-proxy's own refusal of an empty document set,
and a vault SOL transfer mapped against the generated
`GlamProtocolProgram.systemTransfer` layout plus the document's trailing
Token-program seat) and `GlamVaultAccountsTests.mappingContextCarriesTheVaultAndItsAuthorities`
(every integration authority resolved through the context, unknown programs
to null). The fresh history-free `pitestSdk` observation that day (471
mutants, 449 detected) killed every mutant of the new code and left exactly
two accepted rows unmatched, which `pitestSdkBaselinePrune` removed after two
matching previews (baseline 24 -> 22): `GlamAccounts,createMapper`
`NullReturnValsMutator` (`NO_COVERAGE`, now covered and killed) and
`GlamVaultAccounts,lambda$loadMappingConfigs$1` `NakedReceiverMutator`
(`# equivalent path-suffix`, the deleted `.json` suffix filter). No other row
carried that family, so it leaves the label list above and is named here as
history. The same write refreshed `GlamAccounts,main`'s `# line` tag
(170 -> 206) for the methods added above it; the row itself is unchanged.

## Embedded mapping documents (2026-09-25, later)

The `feat/mapping-document` branch's push model was ported onto the mapper
seam above: the ix-mapper-ts documents are tracked under `ix-mapper-ts/` (the
GLAM monorepo's sync workflow writes that directory; until the first sync it
holds a copy made by hand from ix-mapper-ts 16320bf, byte-identical to the
copy ix-mapper-java tests against), `processResources` embeds both
environments as `glam/ix-mappings/{production,staging}` beside a build-time
`index.json`, and `EmbeddedMappings` reads the index and each document back
out of the jar, refusing a document that declares another environment or
proxies through a program the deployment's `GlamAccounts` do not hold.
`GlamAccounts.createMapper()` and `GlamVaultAccounts.createMapper()` serve
that set with no directory; the `Path` and `Collection` overloads stay for a
local set, and `GlamEnv.ofProtocolProgram` refuses a protocol program the sdk
does not know rather than reading it as staging. The pull model went with it:
`downloadMappings.sh`, `syncMappings.sh`, the `downloadMappings` task and the
fixture copies under `src/test/resources/mapping/`; `GlamAccountsMapperTests`
now copies the tracked system-program document into its temporary set.

Coverage: `EmbeddedMappingsTests` (the index names every embedded file and
each parses; the embedded set equals the tracked set byte for byte and file
for file; each environment holds to its own GLAM programs and the other's
refuse it by name; a foreign proxy, a document of another environment, an
empty set, a document that does not admit, a missing resource and every
index-parser refusal, each by message; `createMapper()` serves the
deployment's set; an unknown protocol program is refused),
`EmbeddedMapperTests` (a vault SOL transfer mapped onto the protocol proxy
in both environments with the foreign-source refusal, the Kamino and CCTP
documents keyed and proxied as each deployment carries them, and a Phoenix
deposit seating the vault as the trader without a signer bit — the
`glamsystems/glam#1393` review finding, also a conformance case in
ix-mapper-java), and the `mappingIndex` fuzz target over the index parser,
the one JSON reader this module owns on the mapper's startup path, with its
seeds replayed inside `check`. The fresh history-free `pitestSdk` observation
(526 mutants, 504 detected) killed every mutant of `EmbeddedMappings` and of
the new `GlamAccounts`, `GlamVaultAccounts` and `GlamEnv` members and added
no rows; `pitestSdkBaselineRetag` refreshed `GlamAccounts,main`'s `# line`
tag for the methods added above it.

## Timed-out mutants (audited set, 2026-07-26)

For a member of this set a weakened covering assertion would not show up as a
survivor — a timeout keeps "detecting" whatever the test asserts — so each
member carries a written cause in `sdk-timeouts.csv` and its structural
argument here. The strict reading GLAM holds them to: the mutated path must
have no path-owned finite completion guarantee.

The set is empty. Its five members, all `cause:liveness` on the synchronous
`lut.VaultTableBuilderImpl.batchTableTasks` chunking loop (classified
2026-08-06), were retired on 2026-09-24 with the class (see "`lut` package
removed (2026-09-24)" above); their structural argument is in this file's
history.

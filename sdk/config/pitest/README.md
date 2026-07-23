# Mutation-testing baseline & triage policy — `sdk`

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

## Untriaged debt

For the current per-class ranking, run `./gradlew pitestSdkDebt` — a
hand-maintained list here goes stale the same week it is written. What the
task cannot tell you is *why* the big block is still open:
`lut.VaultTableBuilderImpl`'s add\* methods (the kamino/jupiter account
collection paths — `addKaminoLendAccounts`, `addKaminoVaultAccounts`,
second-phase variants) need `Obligation`/`VaultState`/lookup-table fixtures;
kamino mainnet fixtures already exist under
`services/src/test/resources/accounts/kamino/` and may be shareable into the
sdk suite.

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

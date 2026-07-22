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

Line numbers are part of the baseline key, so unrelated edits to a mutated file
shift entries: the verify task then reports both stale and "new" rows. Confirm
the new rows are the shifted old ones, then refresh with
`-PupdateMutationBaseline`.

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

## Untriaged debt, in priority order

The 2nd pass covered `lut` batching/tasks (surfacing and fixing an
out-of-bounds crash plus dead `DynamicExtendTable` routing in
`batchTableTasks`) and closed the `delegateHasPermissions` semantics question.
Remaining, in priority order:

1. **`lut.VaultTableBuilderImpl` add\* methods** — the kamino/jupiter account
   collection paths (`addKaminoLendAccounts`, `addKaminoVaultAccounts`,
   second-phase variants) need `Obligation`/`VaultState`/table fixtures
   (~79 `NO_COVERAGE`).
2. **`GlamStagingAccountClientImpl` / `StagingStateAccountClientImpl`** — the
   staging mirror of the covered production client (~55).
3. **`proxy.*`** — dynamic account factories (~13).
5. **Remaining `GlamAccountClientImpl` methods** — the `priceDrift*`,
   `priceKamino*`, `updateState`, `createEscrowAssociatedTokenIdempotent`
   family (~14).
6. **Fresh `SURVIVED` rows from newly covered code** (29 total) — includes
   likely equivalence-family members: allocation-size `MathMutator`s in
   `batchTableTasks`, the empty-set fast path at its head, and the
   `!protocolPermissionsMap.isEmpty()` guard in `StateAccountClient` (an
   empty-map entry vs an absent entry is unobservable through
   `delegateHasPermissions`).

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

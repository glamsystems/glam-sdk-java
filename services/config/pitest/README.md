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

## Untriaged debt

The baseline was seeded with the full pre-existing survivor population when
the ratchet was adopted, per HARDENING.md's adoption path — **triage debt made
explicit, not acceptance**. Nothing in it has been triaged yet; expect the
bulk to be `NO_COVERAGE` (untested classes — mechanical test work, never
acceptable as "equivalent").

Shrinking the baseline is always an improvement; growing it requires a reason
written here.

## Triaged equivalent mutants (accepted with reasons)

None yet.

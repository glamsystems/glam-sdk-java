# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

Everything here is portable — true of any checkout. Machine-specific context
(local sibling checkouts, credentials, observed timings) belongs in an
untracked `AGENTS.local.md`, not here.

## What this repository is

The GLAM Java SDK: clients for the GLAM asset-management protocol on Solana
(vaults, tokenized mints, and the integration programs GLAM proxies into),
plus service components for operating against it.

### Module layout

- `sdk/` (`systems.glam.sdk`) — the published SDK. Two distinct populations:
  - **Generated IDL clients** under `systems.glam.sdk.idl.programs.glam.**.gen`
    — accounts, instructions, types, events, PDAs for the GLAM programs
    (protocol, mint, config, policy, and per-integration trees such as
    `spl`, `kamino`, `jupiter`, plus a parallel `staging/` tree for the
    staging deployment). Regenerated from IDLs by `idl-src-gen` (the
    scheduled CI workflow checks out `sava-software/idl-src-gen` and
    regenerates before `check`). **Do not hand-edit generated code** — fix
    the generator or the IDL and regenerate.
  - **Hand-written layer**: `GlamAccountClient` (extends sava's
    `SPLAccountClient`; `createClient` picks the staging vs prod impl by
    protocol program), `GlamAccounts` / `GlamVaultAccounts` (program IDs and
    PDA derivation), `proxy/` (dynamic account remapping — resolves
    ix-mapper `DynamicAccountConfig`s against a vault's accounts, with a
    caching factory), `lut/` (vault address-lookup-table building), and the
    hand-written Jupiter swap wrapper in `idl/programs/glam/jupiter/`.
- `services/` (`systems.glam.services`) — delegate-service runtime layered on
  the sdk: account fetching (`rpc/AccountFetcher`), caches (`mints/`,
  `state/GlobalConfigCache`, `integrations/kamino/`), the fulfillment service
  (`fulfillment/`), scope oracle feed mapping (`oracles/scope/`), batched SQL
  (`db/sql/`), and instruction execution (`execution/`).
- `examples/` — scratch/example module; not part of the hardening surface.
- `glam/` (untracked) — mapping configs cloned from
  `glamsystems/ix-mapper-ts` by `./downloadMappings.sh`; the sdk jar embeds
  `glam/mapping-configs-v1` as `glam/ix-mappings`. Run the script after a
  fresh clone if the sdk jar task complains.
- `Integ.*` files are git-ignored scratch — present on a dev machine, absent
  in CI. Never make anything depend on them.

## Build & test

- Java 25, full JPMS, Gradle wrapper. Build logic comes from the external
  `software.sava.build` convention plugin (separate repo `sava-build`; version
  pinned in `settings.gradle.kts`). No root `build.gradle.kts`; shared
  coordinates and the Solana BOM version live in `gradle/sava.properties`.
- Resolving dependencies requires GitHub Packages credentials
  (`savaGithubPackagesUsername` / `savaGithubPackagesPassword` in
  `~/.gradle/gradle.properties`).
- `./gradlew check` — full build + tests. CI (reusable workflows from
  sava-build) runs exactly this; keep it green.
- Commits follow Conventional Commits (`feat(sdk): ...`, `fix(services): ...`);
  release-please cuts releases from them. Don't hand-edit versions or
  `CHANGELOG.md`.

## Changing a dependency

Much of what this SDK is built on lives in sibling repositories, published
through the Solana BOM (`solanaBOMVersion` in `gradle/sava.properties`):

| Repo | What it owns here |
|---|---|
| `../ravina` | `software.sava.services.*` — RPC calling, backoff/retry, request capacity, load balancing, tx monitoring, epoch service, config parsing (`BackoffConfig`, `ServiceConfigUtil`) |
| `../sava` | `software.sava.core.*` / `software.sava.rpc.*` — keys, instructions, transactions, RPC client |
| `../idl-clients` | `software.sava.idl.clients.*` — SPL, Kamino, Jupiter, Marinade clients |
| `../sava-build` | the convention plugin, the hardening feature, and `HARDENING.md` itself |

**A fix belongs in the repo that owns the code, not worked around here.** When
a defect traces into one of the above:

1. Fix it there, and follow *that* repo's process — it has the same hardening
   ratchet. Run its module `test`, then the `pitest<Suite>` owning the file
   (`grep` its `build.gradle.kts` to find which suite claims the class), and
   keep its accepted baselines green.
2. Editing a mutated file shifts line numbers; pure drift now passes its
   ratchet on its own with a notice. Anything beyond pure drift (newly
   covered, unexplained, changed counts) is triage before refresh — same
   rule as here.
3. To build against the change before it is published, uncomment the matching
   `includeBuild("../<repo>")` at the bottom of `settings.gradle.kts` (Gradle
   substitutes the published module for the local project — verify with
   `./gradlew :services:dependencies --configuration runtimeClasspath`, which
   should show `-> project ':ravina:...'`). `sava-build` is different: it is
   resolved in `pluginManagement`, so uncomment the guarded `includeBuild`
   block there instead.
4. **The `includeBuild` line is temporary and must not ship.** CI has no
   sibling checkout, and leaving it in silently builds every developer against
   whatever they happen to have on disk. Publish the dependency, bump
   `solanaBOMVersion`, re-comment the line, and re-run `check` against the
   published artifact before releasing.

A change that spans both repos is therefore two commits and a publish, not
one. Say so plainly when handing off — the SDK-side commit is not releasable
until the dependency version is bumped.

## Testing conventions

- JUnit 5, built-in `Assertions`, package-private `final class *Tests`, placed
  in the **same package** as the code under test (JPMS whitebox patching is
  wired by the build plugin) — reach for package-private access, not
  reflection, when a test needs an internal.
- Tests never hit the network. Account fixtures are checked-in binary/base64
  snapshots under `src/test/resources/accounts/` (see
  `systems.glam.services.tests.ResourceUtil`); prefer extending that pattern
  over inventing byte arrays by hand.
- Randomized tests use fixed seeds; nothing sleeps. Time-dependent code should
  take a clock seam rather than the wall clock (see ravina's `NanoClock`
  pattern) — give test clocks a non-zero origin.

## Hardening: mutation testing (PIT) and fuzzing (Jazzer)

The `sdk` and `services` modules register PIT mutation suites via the
`software.sava.build.feature.hardening` plugin: `pitestSdk` (hand-written sdk
classes; generated `**.gen.*` code is excluded — its correctness belongs to
idl-src-gen) and `pitestServices` (everything in `services`). Each suite diffs
its unkilled mutants against the accepted baseline in the module's
`config/pitest/` and fails on anything new. The baselines were **seeded with
the full pre-existing survivor population** — that is untriaged debt made
explicit, not acceptance; the per-module `config/pitest/README.md` tracks the
triage state. No fuzz targets are registered yet: adding harnesses for the
external-input parsers (account readers, mapping configs) is planned work —
register them in the module's `hardening` block when they land.

The full policy is sava-build's `HARDENING.md`; the process contract for
changes here:

<!-- This section adapts the agent-instructions template in sava-build's
     HARDENING.md; `agentsTemplateInSync` (wired into `check`) fails when the
     template changes until the block is re-diffed — sync or ACT on each
     changed bullet (a new bullet may need code, not prose) — and the digest
     updated. -->
<!-- hardening-template sha256:e6d8a19c3b67 -->

1. **Scale verification to the change.** Iterate with the module's `test`
   task; before handing off, run only the `pitest<Suite>`(s) whose mutated
   code the change can reach — `pitestServices` also covers changes to sdk
   APIs it calls, and test-only edits still owe the owning suite (a weakened
   test is exactly what the ratchet catches). Doc, comment and build-script
   changes owe no suite. `qualityGate` (every suite, serialized) is the
   pre-release check, not the inner loop; it is owned by the **local release
   checklist** — CI deliberately runs only `check`, so run the gate locally
   before deciding to release.
2. **A new unkilled mutant has exactly three legal outcomes**: kill it with a
   test that asserts the property it breaks (not one restating the
   implementation), refactor it out of existence, or accept it with a written
   reason in the module's `config/pitest/README.md`. Never run
   `-PupdateMutationBaseline` just to make the build pass.
3. **`SURVIVED` and `NO_COVERAGE` are different problems.** A survivor ran
   the line and the test could not tell — a judgment call about equivalence.
   A no-coverage mutant was never executed — mechanical work, and **never
   acceptable as "equivalent"**, because you have not observed its behaviour.
4. **Pure line drift passes on its own** — when every new baseline entry is a
   same-status shift of a stale one and the per-method population is
   unchanged, the verify passes with a notice; refresh at a convenient
   moment. Anything mixed in (newly covered, unexplained, changed counts) is
   triage first, refresh after. `-PnoDriftTolerance` restores strict mode for
   certifying runs.
5. **Iterate with `-PmutateOnly=<class-glob>`** while killing a cluster —
   seconds instead of the full suite — then re-run unscoped before any
   refresh; the tooling refuses to let a scoped report touch the baseline.
6. **Identical baseline rows are sibling mutants** of one compound condition
   and the comparison is a multiset: never hand-dedupe the CSV. When one
   sibling survives, the verify names the killed sibling's test — the
   survivor is the opposite branch direction; triage it as its own mutant.
7. **Determinism is the whole point.** Fixed seeds, no real waits (PIT
   re-runs covering tests once per mutant, so one sleep is multiplied by the
   mutant count), and no reliance on PIT's timeout: `TIMED_OUT` counts as
   detected but is load-dependent — the same mutant can report `SURVIVED`
   alone and `TIMED_OUT` under `qualityGate`. Verify baselines in both modes;
   union only rows observed to flip. A flaky harness is worse than recorded
   debt — if an interleaving cannot be made deterministic, accept the mutant
   with a written reason.
8. **A suite's percentage is not a target.** An accepted mutant with a
   written reason is finished work, not debt. Before trying to raise a
   number, check whether the remainder is `NO_COVERAGE` (real work) or
   documented equivalents (already closed).
9. **Allocation and timing harnesses are a last resort**, reserved for
   properties that are a stated design goal; they need a `volatile` sink and
   flap when margins are thin.
10. When a test you believe in will not go green, **suspect the code before
   you soften the assertion** — that is where this process finds real bugs.
11. **A wandering unkilled count is a defect, not noise** — chase it before
   refreshing any baseline. Known causes: real waits, `TIMED_OUT` load flips,
   `@Execution`/`@TestInstance` on an abstract base not reaching concrete
   classes (JUnit-version-dependent; `javap` the resolved jar), and coverage
   attributed to field initializers — exercise factories from inside a
   `@Test`.
12. **Kill rates are bounded by the mutator set.** `BigInteger`/`BigDecimal`
    arithmetic is method calls, invisible to the default arithmetic mutators —
    fixed-point and fee math needs `EXPERIMENTAL_BIG_INTEGER` (pitest ≥
    1.25.8) — and fluent calls returning their receiver are expressions,
    invisible to `VoidMethodCallMutator`, so builder-style writes need
    `EXPERIMENTAL_NAKED_RECEIVER`. Trial per suite, enable only what fires,
    and record the numbers in `config/pitest/README.md`. Both suites here run
    `STRONGER,EXPERIMENTAL_NAKED_RECEIVER`; the trial numbers are recorded.
13. **PIT minions run on the class path**, even though this repo's tasks run
    on the module path: `module-info` services are invisible to them, and a
    test-resources `META-INF/services` is invisible to the module-path `test`
    task. Real services are declared in both places; a harness whose result
    depends on which task ran it is never committed. This repo currently
    declares no services and uses no `ServiceLoader` (audited 2026-07-22), so
    there is nothing to keep in sync — re-check when adding one.
14. **Exclusions must cover the test source set, not a naming convention**:
    shared fakes named `RecordingFoo` / `StubFoo` match no `*Test*` pattern.
    After registering or widening a suite, list the mutated classes and
    confirm none live under `src/test` (`pitest<Suite>Verify` warns, naming
    them).
15. **Verify by the absence of failures, not the presence of passes.** A
    green build can mean the task was up-to-date rather than that tests ran;
    check the failure count and that the task executed. A *failed* PIT run
    leaves the previous report in `build/reports/pitest/<suite>/`, so the
    summary you read can describe a run that never happened — trust the exit
    code, and delete report directories when comparing runs.
16. **A suite that got faster without getting narrower is a bug report** —
    unless the summary carries the `[history]` marker (arcmutate incremental
    reuse, where fast is expected; the pre-release gate still runs
    `-PnoMutationHistory` to re-earn every status from scratch).
17. **Transient infra failures are not results.** PIT `MINION_DIED` fails
    before writing a report — re-run the suite; a Gradle-worker
    `EOFException` death is the same shape, and per-mutant `RUN_ERROR` under
    load the same shape smaller (not counted as detected). The daemon log
    (`~/.gradle/daemon/<version>/daemon-<pid>.out.log`) keeps a failed
    build's full output even when the shell discarded it.
18. **Fuzz findings become a committed seed input and a named regression
    test**, never just a fix — and the committed corpus is replayed by a unit
    test inside `check`, so it cannot rot between fuzz runs. **When one thing
    has two representations, fuzz the differential** — assert the two agree
    rather than that neither crashes; crash-only fuzzing cannot see a wrong
    answer.

When adding a parser, algorithm or strategy: add unit tests, put it in a
mutation suite (the wildcard targeting already mutates new classes by
default), and add a fuzz harness if it consumes external input.

## Gotchas & invariants worth knowing

- `GlamAccountClient.createClient` routes to `GlamStagingAccountClientImpl`
  when the accounts' protocol program equals
  `GlamAccounts.MAIN_NET_STAGING.protocolProgram()` — prod and staging are
  separate generated trees, and instruction layouts can differ between them.
- The generated `gen` trees are large (hundreds of files); searches are much
  faster when scoped to the hand-written packages (`-not -path '*/gen/*'`).
- The sdk jar embeds the untracked `glam/mapping-configs-v1` directory; a
  clean clone needs `./downloadMappings.sh` before the jar is meaningful.

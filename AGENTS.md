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
   resolved in `pluginManagement` via its local test repo — run sava-build's
   `publishSavaBuildTestPublicationToSavaTestRepoRepository`, then build here
   with `-PsavaBuildLocalRepo=../sava-build/build/sava-test-repo`. The plugin
   itself announces local-repo resolution (with the last-publish age) at the
   end of every such build — including configuration-cache hits — so a build
   that prints no notice did NOT run 0.0.0-test. That publish is not
   automatic: re-run it after every sava-build edit, or chain the two
   (`(cd ../sava-build && ./gradlew publish...) && ./gradlew check -P...`) so
   the stale case is unreachable. The property lives on the CLI or in
   `~/.gradle/gradle.properties`, never in the file, so unlike an
   `includeBuild` there is nothing to un-ship.
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
triage state. Fuzzing is underway — six targets, all seeded from mainnet
snapshots, corpora replayed inside `check`: `services:fuzzAccountData` (the
compressed persistence format — decode + write/read differential; found and
fixed an unbounded-decompression hang), `services:fuzzScopeFeedContext` (the
Scope `Configuration` reader), `services:fuzzReserveContext` (the Reserve
reader + price-chain resolution, the composite-`MostRecentOf` path),
`services:fuzzKaminoVaultContext` (the `VaultState` reader +
allocation-table walk), `services:fuzzMinGlamStateAccount` (the state-account
walk over nested length-prefixed ACL sections, plus the change-detection
re-walk; found a base-asset index landmine), and `sdk:fuzzMappingConfig` (the
ix-mapper config JSON via `ProgramMapConfig.parseConfig`). Register new harnesses in the
owning module's `hardening` block with `targetClass` AND `seedCorpus` (both
are required — a missing `seedCorpus` silently skips the replay test).

The full policy is sava-build's `HARDENING.md`; the process contract for
changes here:

<!-- The block below is the agent-instructions template generated verbatim by
     sava-build's `hardeningAgentTemplate`, pinned by the digest that closes it;
     `agentsTemplateInSync` (wired into `check`) flags it when the installed
     plugin's template changes. Re-diff and ACT on each changed bullet (a new
     bullet may need code, not prose), then move the digest. Do not paraphrase or
     extend it in place — GLAM-specific ownership, measurements and evidence go
     under "GLAM-local hardening facts" below it. -->

- **Scale verification to the change.** Iterate with the module's `test`
  task; before handing off, run only the `pitest<Suite>`(s) whose mutated
  code the change can reach — including suites in dependent modules that
  call a changed API, and the owning suite for test-only edits (a weakened
  test is exactly what the ratchet catches). When the production-class inventory
  changes (add/remove/rename/move), or mutation target/exclusion rules change,
  also run the cheap whole-population
  `mutationOwnershipAudit` before handoff. The full `hardeningCertify` — every
  suite freshly observed, serialized, provenance-bound, diffed against
  `config/pitest/`, with strict timeout and ownership audits — is the pre-release
  check, owned by CI or by the release checklist (this repo records which); it is
  not the inner loop.
- A new unkilled mutant has exactly three legal outcomes: **kill it** with a
  test (prefer asserting the property it breaks over restating the
  implementation), **refactor** it out of existence, or **accept it** with a
  written reason in `config/pitest/README.md` **and a short family label on
  the row itself** — refreshes seed new rows `# untriaged`, and triage means
  replacing that label, so the baseline always says which rows are argued
  and which are debt. Never run a baseline-update task just to make the build
  pass.
- **A mutant is a question, not a specification.** Before writing a killing
  test, state the externally intended property and an oracle independent of the
  current implementation: public contract, protocol specification, caller
  invariant, reference implementation, or domain rule. If it contradicts current
  behavior, first demonstrate the bug with a regression test that fails against
  the unmutated code, then fix production; never add a passing assertion that
  merely locks in the bug. At PR or handoff, report each nontrivial behavioral
  cluster — not each mutant — as `Property: ... | Oracle: ... | Outcome: missing
  assertion / production bug / accepted equivalent`. Test names and assertions
  normally carry the durable property; comment only when the oracle or unusual
  setup would otherwise be lost, and never embed PIT coordinates or line numbers.
- Baseline keys are line-less (`class,method,mutator,STATUS`) — editing
  above a mutated method churns nothing, and `# line` tags are review
  metadata. A new mutant replacing a killed one at the same key can inherit
  its acceptance, so treat a line-drift advisory whose written argument no
  longer fits the code as that swap until shown otherwise. Use the installed
  plugin's named writer tasks and heed their candidate previews; never hand-edit
  record structure or provenance stamps. A PIT, PIT-plugin/tool-artifact,
  ArcMutate-base, or certificate change uses `pitest<Suite>BaselineRebase`: it
  preserves every old row, seeds new rows `# untriaged`, and stamps the reviewed
  toolchain only after a successful fresh observation. Perform a schema
  migration/rollback only with a fleet pin plan. A `[history]` report may check
  the ratchet but cannot support adding, removing, or relabelling
  accepted/timeout records; run `pitest<Suite> -PnoMutationHistory` first.
- Consumer hardening notes contain only local ownership, measurements, acceptance
  reasons, and provenance. `AGENTS.md` may carry this exact generated,
  digest-pinned template plus those local facts, but no independently maintained
  copy of plugin task semantics; use `hardeningHelp` and
  `hardeningAgentTemplate` as the installed-version authorities.
- **Iterate with `-PmutateOnly=<class-glob>`** while killing a cluster —
  seconds instead of the full suite — then re-run unscoped with
  `-PnoMutationHistory` before any record decision; the tooling refuses to let
  a scoped report touch the baseline.
- Identical baseline rows are sibling mutants of one compound condition and
  the comparison is a multiset: never hand-dedupe. When one sibling
  survives, the verify names the killed sibling's test — the survivor is
  the opposite branch direction; triage it as its own mutant.
- **A survivor contradicted by an existing oracle may be contaminated evidence.**
  Open PIT's HTML **Covering tests** list, then compare the same scoped,
  history-free population with and without isolation:
  `-PmutateOnly=<class> -PnoMutationHistory`, then
  `-PmutateOnly=<class> -PisolateMutants`. An isolation-only kill points
  to state leaked between mutants — commonly a thread, executor, handler, or
  static fixture whose cleanup an earlier assertion failure skipped. Put
  teardown in `finally`/`try`-with-resources and rerun normally, history-free;
  isolated execution is diagnostic evidence, never a baseline decision.
- **Stubs and fixtures return distinguishable, non-default values.** A stub
  returning null/0/""/true/empty makes the matching return-value mutant
  equivalent by accident of the fixture — the clock non-zero-origin rule
  generalized to every stubbed return.
- **Copy-on-write clusters split by direction.** Assert immutability of
  returned collections (`assertThrows(UnsupportedOperationException, ...)`)
  at every size: the mutable-escape direction is a kill, not an acceptance;
  only the content-equal siblings are family-accepted equivalents.
- **Randomized tests use fixed seeds, and never sleep**: the ratchet needs
  deterministic kills, and PIT re-runs the suite per mutant, so one real wait
  costs minutes. Exploration belongs to the fuzz targets.
- **Do not rely on PIT's timeout to detect a mutant.** `TIMED_OUT` counts as
  detected and is not written to the baseline, and it is load-dependent — the
  same mutant can report `SURVIVED` alone and `TIMED_OUT` under
  `qualityGate`. Verify a baseline in both modes; union only rows observed to
  flip, never every `TIMED_OUT` row.
- **A new timed-out mutant is a reviewer-stop, not detection noise.** For
  exactly these mutants the ratchet cannot see a weakened covering
  assertion — a timeout keeps "detecting" whatever the test asserts — so
  each suite's timeouts are an audited set, not a count:
  `config/pitest/<suite>-timeouts.csv` holds line-less `class,method,mutator`
  keys plus a comment category; `# line` tags are diagnostic metadata only. Only
  `cause:liveness` is admissible watchdog detection after deterministic
  seams/budgets are exhausted: the mutated path has no path-owned finite
  completion guarantee. A fixture's emergency exit does not demote that
  liveness loss to resource work; record the fixture bound in the README. If that
  bound is the claimed deterministic oracle, compare it with PIT's
  `duration × timeoutFactor + timeoutConst`: a bound that cannot fail first
  contributes no cause evidence, so shorten it and re-observe history-free. A
  later emergency ceiling may coexist with production liveness but cannot prove it.
  A straight-line path with no loop, retry, lock, wait, blocking
  call, or external completion dependency is not credible liveness evidence.
  Before
  admitting liveness, prove the mutated path receives the clock/budget the test
  observes, and check for a synchronous state reader that can expose the defect
  without waiting. A `TestClock` on a collaborator cannot observe a subject using
  the system clock. Seeded
  `cause:untriaged`, missing/unknown categories, finite `cause:resource`, and
  `cause:harness` work are reviewer-stops. `cause:harness` is the explicit
  non-certifying holding state for a demonstrated finite covering-path/watchdog
  race; it never makes the timeout admissible. Resource behavior gets a
  deterministic contract test/fix when promised, otherwise a stable `SURVIVED`
  equivalence argument —
  never silent timeout membership. Liveness authorizes valid `TIMED_OUT`
  evidence only, never `MEMORY_ERROR`: if a non-advancing loop races the heap
  against the watchdog, make every covering path fail deterministically without
  relying on PIT test order, or refactor the manual progress mutation site out
  while preserving the tested contract.
  `config/pitest/README.md` still holds the
  full structural cause per member. The verify warns on any timeout outside
  the set — paste the printed row, classify it, then write the cause — and on
  members matching no mutant. Membership and cause are key-level, so a liveness
  token claims every sibling under that key. A key proven to mix liveness and
  finite causes is not representable as an honest certifying row: split/refactor
  it into distinct method keys or eliminate the ambiguous site, then re-observe
  history-free. A source-line qualifier cannot fix the identity without making
  formatting a release gate. Positive multiplicity drift prints all current
  line-full candidates for review;
  source-line movement itself never warns, fails, or requires re-anchoring. Adding
  a method, moving imports, or reflowing an expression is not a hardening record
  change. Strict workflows run the
  committed-file half before PIT; use `pitest<Suite>Debt` for the same quick
  manual preview. `TimeoutAuditInit` deliberately seeds an uncertifiable file —
  classify every row before certification. For an otherwise admissible liveness
  member, do not retire it until the tool emits its 3+ distinct fresh full-run quiet
  notice over identical evidence inputs and the absence is confirmed under the
  relevant solo/gate load. A finite KILLED↔TIMED_OUT race is benign only to baseline
  arithmetic, never certifying evidence; repair/retime its covering path instead of
  admitting it or waiting on the liveness-retirement rule. The quiet stash
  is a machine-local nomination: never copy or merge it, and retain the row when a
  same-input gate confirmation is unavailable. Assisted reports are
  previews and do not
  advance timeout status or quiet-run evidence.
- **A flaky harness is worse than recorded debt.** If an interleaving or a
  boundary cannot be made deterministic, accept the mutant with a written
  reason rather than chasing it with sleeps or spin-waits.
- **A suite's percentage is not a target.** An accepted mutant with a written
  reason is finished work, not debt. Before trying to raise a number, check
  whether the remainder is `NO_COVERAGE` (real work) or documented
  equivalents (already closed).
- **Allocation and timing harnesses are a last resort for thin constant-factor
  differences**, reserved for properties that are a stated design goal. A
  removed growth/capacity/amortisation guard that changes complexity class is
  not “allocation-size only”: use a small input with an orders-of-magnitude
  margin and the correct path through the mutated code. Harnesses re-run once
  per mutant, need a `volatile` sink so escape analysis cannot delete what they
  measure, and flap when the margin is thin.
- When a test you believe in will not go green, **suspect the code before you
  soften the assertion** — that is where this process finds real bugs.
- **A wandering unkilled count is a defect, not noise** — chase it before
  changing any baseline. Reproduce it under the relevant solo/gate loads,
  inspect per-mutant coordinates, remove real waits, and move construction
  coverage into the test body before deciding whether it is a product defect,
  a load-dependent timeout, or a harness defect.
- **Build the subject under test inside the test body, not in a field.**
  Under `PER_CLASS` lifecycle a field-initialized client's construction
  coverage attaches to whichever test runs first, so wiring mutants can
  never pair with the test that drives what they wire — they survive even
  under a harness that asserts every request. One test that constructs the
  client in the test method and drives each configured URL restores the
  pairing.
- **Kill rates are bounded by the mutator set.** `BigInteger`/`BigDecimal`
  arithmetic and receiver-returning fluent calls can be invisible to the
  enabled defaults. Follow the plugin's trial advice per suite, enable only
  mutators proved to fire, and record the measured numbers and declines.
- Module-path and mutation-test service discovery can differ. Declare real
  services in every runtime representation the project supports, probe the
  active environment in test-only scaffolding, and never commit a harness
  whose pass/fail result depends on which task launched it.
- `SURVIVED` and `NO_COVERAGE` are different problems: the first is a
  judgment call about equivalence, the second is usually an untested line
  and is mechanical. Never accept a `NO_COVERAGE` mutant as "equivalent" —
  you have not observed its behaviour. One structural exception: a block
  that always exits by throw reads `NO_COVERAGE` forever, executed or not
  (PIT probes a block at its end), and its return-value mutants can never
  change status. Such a line is owed a test asserting the throw's contract,
  not coverage — and never leave one untested fearing a covered-line
  `SURVIVED` conversion, which would require the block to complete.
- Exclusions must cover the **test source set**, not a naming convention:
  shared fakes are named `RecordingFoo` / `StubFoo` and match no `*Test*`
  pattern. After registering or widening a suite, list the mutated classes and
  confirm none live under `src/test`.
- **Verify by the absence of failures, not the presence of passes.** Counting
  `PASSED` lines hides a failure sitting next to them, and a green
  `clean build` can mean the build cache short-circuited rather than that
  tests ran. Check the failure count and confirm the task actually executed.
  A mutation run has a second version of this: a *failed* PIT run leaves the
  previous run's report in place, so the summary you read can describe a run
  that never happened. Trust the exit code, and delete report directories
  when comparing runs.
- **A suite that got faster without getting narrower is a bug report.** Real
  speedups come from fewer mutants or faster covering tests; an unexplained
  one usually means the run did less than you think. Read the task's evidence
  markers and scope; only a fresh full certification may support a release.
  The process itself needs no ArcMutate licence and applies to any Java package.
- **Invalid execution outcomes are not results.** PIT `MINION_DIED` fails
  before writing a report, so it cannot corrupt one — re-run the suite; a
  Gradle-worker `EOFException` death is the same shape, and a per-mutant
  `RUN_ERROR` often first observed in a multi-suite run is the same
  shape smaller (load average itself proves nothing; the hardening parser refuses
  the report rather than certifying PIT's detected score). The refusal and
  `pitest<Suite>Debt` name every offending row; retain the coordinate before a
  quiet re-run replaces the report. `RUN_ERROR` alone diagnoses neither load nor
  memory and never justifies changing threads or heap; record load/RSS as context,
  retry once quietly, and tune only when PIT explicitly diagnoses a process-resource
  failure. A repeat at the same coordinate is not evidence
  of load: investigate the mutated bytecode, its covering tests, and the tool failure.
  The daemon log
  (`~/.gradle/daemon/<version>/daemon-<pid>.out.log`) keeps a failed build's
  full output even when the shell discarded it — read it before calling a
  failure unexplained.
- Fuzz findings become a committed seed input **and** a named regression
  test, never just a fix — and the committed corpus is replayed by a unit
  test inside `check`, so it cannot rot between fuzz runs.
- **Run fuzz campaigns explicitly and locally.** `fuzzAll` is derived from every
  registered target, so it cannot drift from a hand-written workflow task list;
  set and record `-PmaxFuzzTime=<seconds>` and
  `-PmaxParallelFuzzTargets=<count>` before release. Scheduled GitHub fuzz
  workflows are optional and are not release evidence.
- **When one thing has two representations, fuzz the differential.** Two
  parsers for one config, an encode/decode round trip, a fast path beside a
  reference path: assert the two *agree* rather than that neither crashes.
  Crash-only fuzzing cannot see a wrong answer.
- **Time-dependent code takes a clock**, so tests advance time instead of
  waiting. Give test clocks a non-zero origin — a clock starting at 0 makes
  every "start timestamp mutated to 0" mutant equivalent by accident.
<!-- hardening-template sha256:46f7174e51fb -->

### GLAM-local hardening facts

Only local ownership, measurements, acceptance reasons and provenance belong
here; `hardeningHelp` and `hardeningAgentTemplate` are the authorities on task
semantics for the installed plugin version.

**Ownership.** The pre-release `hardeningCertify` is owned by the **local
release checklist**, not CI — CI deliberately runs only `check`, so certify
locally before deciding to release. `pitestServices` also covers `:sdk` API
changes it calls, so a change under `sdk/` that `services` reaches owes both
suites.

**ArcMutate.** GLAM is outside the Sava ArcMutate certificate — it does not
cover `systems.glam.*` — so both suites run open-source PIT, no `[history]`
report is available here, and no history-assisted exception ever applies to a
GLAM result.

**Mutators.** `pitestSdk` runs `STRONGER,EXPERIMENTAL_NAKED_RECEIVER`;
`pitestServices` adds `EXPERIMENTAL_BIG_INTEGER,EXPERIMENTAL_BIG_DECIMAL`,
which fire only there — `services` carries the money math (`BigDecimal` share
sums, `BigInteger` liquidity totals) those mutators can express and `sdk`
generates none. Both trials, with their measured numbers, are recorded in each
module's `config/pitest/README.md`. `pitestServices` also sets
`timeoutFactor = 2.0` / `timeoutConst = 1500` and was trialed at 8 threads;
the reasoning is in `services/build.gradle.kts`.

**Test lifecycle.** Neither module uses `@TestInstance(PER_CLASS)` (audited
2026-07-23), so field initializers re-run per test — still build the subject
under test in the test body, and re-audit if a test class adopts `PER_CLASS`.

**Service discovery.** This repo's Gradle tasks run on the **module path** while
PIT minions run on the **class path**, so `module-info` services are invisible
to the minions and a test-resources `META-INF/services` is invisible to the
module-path `test` task. This repo declares no services of its own (audited
2026-07-22), but the trap reached it through a dependency: a test that
ServiceLoads ravina's `MemorySignerFactory` failed under PIT while that jar
declared the service only in `module-info` (hit 2026-07-23). Ravina fixed it in
`bde97ec` ("restore classpath service discovery"); `pitestServices` needs a
ravina release carrying that fix.

**A wandering count, locally.** Causes already seen here: real waits,
`TIMED_OUT` load flips, coverage attributed to field initializers, and
`@Execution` / `@TestInstance` on an abstract base not reaching concrete
subclasses (JUnit-version-dependent — `javap` the resolved jar rather than
trusting the source).

**Fuzz corpora.** Minimize with `fuzz<Target>Minimize` rather than by hand,
review the resulting corpus diff, and update the corpus provenance notes in
`src/test/resources/fuzz/` when seeds change.

**Local instances of the generic rules.** The recording proxies here are
`SolanaRpcClient` / `AccountFetcher` — give them return values a real assertion
can tell from a mutated default. `KaminoCacheImpl.vaultContexts` is the local
copy-on-write cluster: assert the `UnsupportedOperationException` as well as the
contents.

**Family labels.** Every `# <family>` label on an accepted row must be named in
the owning module's `config/pitest/README.md` "Family labels" glossary, so a
typo or an orphaned argument surfaces instead of silently opening a bucket.

**Timeout audit.** Both suites' audited timeout sets and their `cause:*`
classifications live in `config/pitest/<suite>-timeouts.csv`, with the
structural argument per member under "Timed-out mutants (audited set)" in the
owning `config/pitest/README.md`.

**Fixture deadlines must sit inside the watchdog budget.** `pitestServices` sets
`timeoutConst = 1500` and `timeoutFactor = 2.0`, so a covering test's PIT budget
is `1500ms + 2x its own runtime` — roughly 1.6–2.8s here. A fixture that waits
longer than that never gets to fail its own assertion: the watchdog fires first
and reports `TIMED_OUT`, which is the one status the ratchet cannot see a
weakened assertion behind. This suite carried fourteen 5s fixture deadlines
against that budget; lowering them (1s, and 2s for `MintCacheImplTest`, which
does ~600ms of real work across 128 virtual threads) converted watchdog
"detections" into real kills and exposed assertions that had never actually been
testing anything. **Before classifying any timeout, check this arithmetic first** —
a bounded fixture that outlives the budget is a harness defect, not liveness.
Keep new fixture deadlines well under 1.5s, and prefer a synchronous state
reader (`lock.getReadLockCount()`, a cache accessor) over any wait at all.

When adding a parser, algorithm or strategy: add unit tests, put it in a
mutation suite (the wildcard targeting already mutates new classes by default),
and add a fuzz harness if it consumes external input.

## Gotchas & invariants worth knowing

- `GlamAccountClient.createClient` routes to `GlamStagingAccountClientImpl`
  when the accounts' protocol program equals
  `GlamAccounts.MAIN_NET_STAGING.protocolProgram()` — prod and staging are
  separate generated trees, and instruction layouts can differ between them.
- The generated `gen` trees are large (hundreds of files); searches are much
  faster when scoped to the hand-written packages (`-not -path '*/gen/*'`).
- The sdk jar embeds the untracked `glam/mapping-configs-v1` directory; a
  clean clone needs `./downloadMappings.sh` before the jar is meaningful.

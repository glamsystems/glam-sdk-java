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
    the generator or the IDL and regenerate, and commit the movement report
    beside any record a regeneration moves (see "IDL channel records and
    movement evidence").
  - **Hand-written layer**: `GlamAccountClient` (extends sava's
    `SPLAccountClient`; `createClient` picks the staging vs prod impl by
    protocol program), `GlamAccounts` / `GlamVaultAccounts` (program IDs and
    PDA derivation), `proxy/` (dynamic account remapping — resolves
    ix-mapper `DynamicAccountConfig`s against a vault's accounts, with a
    caching factory), `lut/` (vault address-lookup-table building), and the
    hand-written Jupiter swap wrapper in `idl/programs/glam/jupiter/`.
- `services/` (`systems.glam.services`) — delegate-service runtime layered on
  the sdk: account fetching (`rpc/AccountFetcher`), caches (`mints/`,
  `state/GlobalConfigCache`), integration lookup-table caching
  (`integrations/IntegLookupTableCache`), the fulfillment service
  (`fulfillment/`), batched SQL (`db/sql/`), and instruction execution
  (`execution/`).
- `examples/` — scratch/example module; not part of the hardening surface.
- `glam/` (untracked) — mapping configs from `glamsystems/ix-mapper-ts` at
  the commit `./downloadMappings.sh` pins (`MAPPINGS_REF`); the sdk `jar` task
  runs the script itself and embeds `glam/mapping-configs-v1` as
  `glam/ix-mappings`, refusing to build or to write an archive without them.
  `./syncMappings.sh [sha]` moves the pin; commit the pin change with the
  jar it produces.
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
2. Editing a mutated file shifts line numbers; anything beyond that pure
   drift (newly covered, unexplained, changed counts) is triage before
   refresh — same rule as here.
3. To build against the change before it is published, uncomment the matching
   `includeBuild("../<repo>")` at the bottom of `settings.gradle.kts` (Gradle
   substitutes the published module for the local project — verify with
   `./gradlew :services:dependencies --configuration runtimeClasspath`, which
   should show `-> project ':ravina:...'`). `sava-build` is different: it is
   resolved in `pluginManagement` via its local test repo — run sava-build's
   `publishSavaBuildTestPublicationToSavaTestRepoRepository`, then build here
   with `-PsavaBuildLocalRepo=../sava-build/build/sava-test-repo`, which
   substitutes sava-build `0.0.0-test`. Confirm from the build's own output
   that the run really resolved the local repo before trusting a result from
   it. That publish is not
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
idl-src-gen) and `pitestServices` (everything in `services`). Each suite's
accepted baseline lives in the module's `config/pitest/`. The baselines were
**seeded with the full pre-existing survivor population** — that is untriaged
debt made explicit, not acceptance; the per-module `config/pitest/README.md`
tracks the triage state. Fuzzing is underway — three targets, all seeded from
mainnet snapshots, corpora replayed inside `check`: `services:fuzzAccountData`
(the compressed persistence format — decode + write/read differential; found
and fixed an unbounded-decompression hang), `services:fuzzMinGlamStateAccount`
(the state-account walk over nested length-prefixed ACL sections, plus the
change-detection re-walk; found a base-asset index landmine), and
`sdk:fuzzMappingConfig` (the ix-mapper config JSON via
`ProgramMapConfig.parseConfig`). Register new harnesses in the owning module's
`hardening` block with both `targetClass` AND `seedCorpus` — GLAM registers no
fuzz target without a checked-in corpus to replay.

The full policy is sava-build's `HARDENING.md`; the process contract for
changes here:

<!-- The block below is the agent-instructions template generated verbatim by
     sava-build's `hardeningAgentTemplate`, pinned by the digest that closes it.
     glam-sdk-java policy on every template-digest move: re-diff with the
     project-qualified `hardeningAgentTemplateDiff` (e.g.
     `:sdk:hardeningAgentTemplateDiff`), ACT on each changed bullet (a new
     bullet may need code, not prose), and only then move the digest. See
     `:sdk:hardeningHelp` for the installed task surface. Do not paraphrase or
     extend the block in place — glam-sdk-java-specific ownership, measurements
     and evidence go under "GLAM-local hardening facts" below it. -->

<!-- hardening-template block:start -->
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
  and which are debt. For an existing baseline, use `BaselineUnion` after
  reviewing the fresh rows: it appends them without deleting unmatched evidence.
  Reserve `BaselineUpdate` for a first seed or an independently reviewed complete
  rewrite; never run it just to make the build pass. A family label groups
  individually reviewed instances; it never authorizes the next syntactically
  similar mutant.
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
  metadata. New or edited mutation-evidence prose should use line-less
  class/method/mutator identifiers rather than source line numbers. Existing prose
  is not a plugin-upgrade gate; repair a stale locator when ordinary review encounters
  it. The current PIT report and the row's `# line` tag are the sole transient locators.
  A new mutant replacing a
  killed one at the same key can inherit
  its acceptance, so treat a line-drift advisory whose written argument no
  longer fits the code as that swap until shown otherwise. After review, use
  `BaselineRetag` to refresh only matched line metadata while preserving every
  accepted row; never use an unrelated acceptance or deletion merely to clear
  the advisory. Use the installed plugin's named writer tasks and heed their
  candidate previews. Before `BaselinePrune` can delete, two distinct completed
  fresh full history-free previews must have the exact same candidate multiset;
  its own third fresh write-boundary run must match them too. Candidate drift is a
  reviewer-stop, and matching bytes do not replace review of the relevant
  solo/gate load context or each removal criterion. Never hand-edit
  record structure or provenance stamps. A PIT, PIT-plugin/tool-artifact,
  ArcMutate-base, or certificate change uses `pitest<Suite>BaselineRebase`: it
  preserves every old row, seeds new rows `# untriaged`, and stamps the reviewed
  toolchain only after a successful fresh observation. That provenance binds the
  current transition and observation; it does not claim that every conservatively
  preserved row was generated by the new toolchain. Perform a schema
  migration/rollback only with a fleet pin plan. A `[history]` report may check
  the ratchet but cannot support adding, removing, or relabelling
  accepted/timeout records; run `pitest<Suite> -PnoMutationHistory` first.
- Consumer hardening notes should focus on local ownership, measurements, acceptance
  reasons, and provenance. Prefer a `hardeningHelp` pointer over a detailed copy of
  installed task behavior, but do not turn a plugin upgrade into a repository-wide
  prose migration. `AGENTS.md` carries this exact generated, digest-pinned template
  with repository-specific facts outside its bounded block. Use `hardeningHelp` and
  project-qualified `hardeningAgentTemplate` as the installed-version authorities,
  and run the matching read-only `hardeningAgentTemplateDiff` against its explicitly
  bounded block on every template-digest move before acknowledging the new marker.
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
  detected and is not written to the baseline, but it proves only watchdog
  detection. Load can change the observed status and line-less keys can conflate
  siblings. Verify a baseline in both modes; for measured load-flip insurance,
  union only rows observed to flip, never every `TIMED_OUT` row. This does not
  restrict additive `BaselineUnion` acceptance of separately reviewed fresh debt.
- **A new timed-out mutant is a reviewer-stop, not detection noise.** A timeout
  can mask a weakened assertion; audit a set, not a count. **Record.**
  `config/pitest/<suite>-timeouts.csv` holds line-less
  `class,method,mutator` keys and a cause; `# line` is diagnostic, while
  `config/pitest/README.md` records the full cause. Verification warns on outside
  timeouts and stale members. `pitest<Suite>Debt` previews the pre-PIT
  file check. `TimeoutAuditInit` seeds an uncertifiable file: classify every row.
  **Classify.** Only `cause:liveness` certifies: after deterministic seams and
  budgets, the mutated path has no path-owned finite completion. A fixture's
  emergency exit does not demote that loss; record its bound. A bound claimed
  as the deterministic oracle must beat PIT's
  `duration × timeoutFactor + timeoutConst`; otherwise shorten it and re-observe
  history-free — it contributes no cause evidence. A later emergency
  ceiling cannot prove liveness.
  A straight-line path without a loop, retry, lock, wait, blocking call, or external
  completion dependency is not credible liveness evidence. Prove the mutated path
  receives the test clock/budget and check for a synchronous state reader; a
  collaborator's `TestClock` cannot observe a system clock.
  Missing/unknown causes, `cause:untriaged`, finite `cause:resource`, and
  `cause:harness` are reviewer-stops; harness records a finite covering-path/watchdog
  race without authorizing it. Resource behavior needs its promised contract test/fix
  or a stable `SURVIVED` equivalence argument. Liveness authorizes `TIMED_OUT`, never
  `MEMORY_ERROR`: for a non-advancing loop racing the heap, make every covering path
  fail deterministically without relying on PIT test order, or refactor out the
  mutation site.
  **Disambiguate.** A cause covers every `TIMED_OUT` sibling under its key. A finite
  sibling observed `KILLED` or another valid non-timeout does not itself create
  mixed timeout causes, but a key
  cannot certify when trustworthy fresh evidence shows distinct same-key siblings
  timing out under different cause categories. One later `KILLED` does not erase that
  conflict; `KILLED`↔`TIMED_OUT` movement alone does not prove it. Repair the finite
  path and establish repeated fresh history-free non-timeout observations under
  solo/gate load, or split/refactor/eliminate the site. Multiplicity drift prints
  all current line-full candidates, but lines cannot define identity: moving imports,
  adding a method, or reflowing code never warns, fails, or requires re-anchoring.
  **Retire.** Remove an admissible liveness member only after the tool reports 3+
  distinct fresh full-run quiet observations over identical execution inputs,
  confirmed under solo/gate load. When retirement semantics are unchanged, a plugin
  fingerprint change alone does not reset this advisory; captured PIT-input changes
  do, and unmodeled semantic changes require a timeout-quiet format bump. A
  finite `KILLED`↔`TIMED_OUT` race never certifies: repair it instead of waiting on
  liveness retirement. The quiet stash is a machine-local nomination; never copy or
  merge it, and retain the row without same-input gate confirmation. Assisted
  reports are previews and advance neither timeout status nor quiet-run evidence.
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
  A mutation run has a second version of this: PIT writes reports incrementally,
  so a failed run can otherwise look complete. The plugin clears known
  decision-grade leaves before each attempt, writes `.running` until clean
  completion, and retains unfiltered `pitest.stdout.log` / `pitest.stderr.log`
  beside the selected report. Trust the exit code and sentinel, not a summary
  from a failed attempt. Use `pitest<Suite>Diagnostic` for isolated
  `VERBOSE_NO_SPINNER`, history-free investigation; its report and raw logs are
  machine-local diagnostic output, may contain sensitive test/process details,
  and can never support a record or certification decision.
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
  failure. Recurrence localizes a repeatable observation, not its cause: stable
  mutation-unit partition can report an aggregate-contention minion death at the same
  coordinate repeatedly. Compare fresh history-free full attempts with
  `-PmutateOnly=<class> -PnoMutationHistory`; a reliable scoped kill points away from
  the mutant alone without proving load, while a scoped batched/`-PisolateMutants`
  difference says the mutation-unit boundary matters — inspect leaked state first,
  then packing/process overhead. Run `pitest<Suite>Diagnostic` full and scoped when
  per-process progress is missing; its separate raw streams establish no total order,
  and the last announced mutation is context, not cause. Only a clean fresh full
  unscoped run can support records or certification. Such a later clean run (or a
  successful `hardeningCertify`) is sufficient closure for a non-recurring invalid
  outcome: it does not diagnose that failure, and the invalid attempt creates no
  mutation-record debt. If certification was interrupted, retry the affected
  project's whole `hardeningCertify`; its receipt deliberately re-executes every
  suite in that project in one invocation rather than stitching attempts, while
  other project receipts remain independent.
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
<!-- hardening-template block:end -->
<!-- hardening-template sha256:4700f2aad913 -->

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
can tell from a mutated default.

**Family labels.** Every `# <family>` label on an accepted row must be named in
the owning module's `config/pitest/README.md` "Family labels" glossary; here a
row is not triaged until that glossary entry exists.

**Timeout audit.** Both suites' audited timeout sets and their `cause:*`
classifications live in `config/pitest/<suite>-timeouts.csv`, with the
structural argument per member under "Timed-out mutants (audited set)" in the
owning `config/pitest/README.md`.

**Fixture deadlines must sit inside the watchdog budget.** `pitestServices` sets
`timeoutConst = 1500` and `timeoutFactor = 2.0`, which puts a covering test's
PIT budget at roughly 1.6–2.8s here. A fixture that waits longer than that never
gets to fail its own assertion. This suite carried fourteen 5s fixture deadlines
against that budget; lowering them (1s, and 2s for `MintCacheImplTest`, which
does ~600ms of real work across 128 virtual threads) converted watchdog
"detections" into real kills and exposed assertions that had never actually been
testing anything. **Before classifying any timeout, check this budget first** —
a bounded fixture that outlives the budget is a harness defect, not liveness.
Keep new fixture deadlines well under 1.5s, and prefer a synchronous state
reader (`lock.getReadLockCount()`, a cache accessor) over any wait at all.

When adding a parser, algorithm or strategy: add unit tests, put it in a
mutation suite (the wildcard targeting already mutates new classes by default),
and add a fuzz harness if it consumes external input.

## IDL channel records and movement evidence

Each generated `gen` tree carries a `sources.json` channel record: which published
descriptions of the program exist, their content hashes, and the program's deploy
slot and image hash. The scheduled monitor (`build-scheduled.yml`) regenerates and
compares against these committed records every eight hours; its Slack digest is the
redeploy signal, and the committed records are the baseline it compares against.

Always generate with `--report=idl-change-report.txt` (the genSrc.sh default) and
commit **both** reports one run writes: that file, which carries the movement this
run saw, and `idl-change-report-gap.txt` beside it, the standing gap dashboard. A
change to a generated `sources.json` hash without a matching change to the
*movement* report means the generation was run without retaining its
channel-movement evidence. Movement is an event: the next run reports nothing, and
what this one saw is then unrecoverable. The gap file is not evidence of anything —
it re-renders whether or not the run saw movement. Both files are explicitly
re-included in `.gitignore`; before those rules existed the deny-by-default rule
swallowed them silently, which is how `92318b2` lost its report — the three
earlier record commits predate the generator writing a report by default
(idl-src-gen `42ea388`) and never carried one either way.

`.github/report-evidence.sh` is that sentence as a gate, run over every pushed
range by the `Report Evidence` workflow. Read the script for what it keys on; it
is the authority, and `1fee3f8` is the local precedent for a format-only restamp
across all 24 records. A record moved with no generation behind it says so in
commit trailers: a `Report-Evidence:` trailer carrying the why in prose, plus one
`Report-Evidence-Path:` trailer per moved record.

The script and `.github/hooks/pre-push` are **vendored, byte-identical copies** of
`consumer/` in sava-software/idl-src-gen, which is canonical — the audit's key set
and line-anchored greps are contracts with the serializer there, and
`ReportEvidenceScriptTests` in that repository holds the two together. Never edit
the copies here: fix canonically, then re-vendor with idl-src-gen's
`consumer/sync.sh`. Both the push-triggered workflow and the scheduled monitor
diff the copies against canonical and fail on drift.

The hook runs the same audit over the commits a push would publish, which is the
one moment the fix is still free — a pushed commit is an ancestor of a remote ref
and must not be rewritten. Install it with `git config core.hooksPath
.github/hooks`, noting that this redirects *all* hook lookups to that directory.
It does not replace the workflow: a hook lives in one clone and `--no-verify`
skips it, so the two answer different halves.

## Gotchas & invariants worth knowing

- `GlamAccountClient.createClient` routes to `GlamStagingAccountClientImpl`
  when the accounts' protocol program equals
  `GlamAccounts.MAIN_NET_STAGING.protocolProgram()` — prod and staging are
  separate generated trees, and instruction layouts can differ between them.
- The generated `gen` trees are large (hundreds of files); searches are much
  faster when scoped to the hand-written packages (`-not -path '*/gen/*'`).
- The sdk jar embeds the untracked `glam/mapping-configs-v1` directory and
  materializes it itself at the pinned ix-mapper-ts commit; a jar without
  `glam/ix-mappings/*.json` entries fails the `jar` task rather than
  publishing empty, which is what every release before the pin did.

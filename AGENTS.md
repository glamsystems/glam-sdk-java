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
    PDA derivation, and the ix-mapper seam: `GlamAccounts.createMapper()`
    builds an ix-proxy `InstructionMapper` over the documents the jar embeds
    for the deployment, the `Path` and `Collection` overloads over a local
    set, every one refusing another environment's documents, and
    `GlamVaultAccounts.mappingContext()` is what a mapping needs from a
    vault — state, vault, fee payer as signer, integration authorities — and
    its `mappingContext(supplier)` overload adds the supplier of the accounts
    a handler reads that a native instruction never carries),
    `mapping/` (`GlamSuppliedAccounts` and the resolvers behind it: the Orca
    liquidity handlers' oracle prefix under GLAM's one oracle-selection rule,
    and the Loopscale `update_strategy` market),
    `EmbeddedMappings` (the documents the jar carries, indexed and held to
    their environment and proxy programs), and the hand-written Jupiter swap
    wrapper in `idl/programs/glam/jupiter/`.
- `services/` (`systems.glam.services`) — delegate-service runtime layered on
  the sdk: account fetching (`rpc/AccountFetcher`), caches (`mints/`,
  `state/GlobalConfigCache`), the integration service context
  (`integrations/`), the fulfillment service (`fulfillment/`), batched SQL
  (`db/sql/`), and instruction execution (`execution/`).
- `examples/` — scratch/example module; not part of the hardening surface.
- `ix-mapper-ts/` — the generated ix-mapper mapping documents
  (`src/generated/mapping/{production,staging}` of the TypeScript mapper
  package, in its layout), written by the GLAM monorepo's public-sync
  workflow and never by hand (`ix-mapper-ts/README.md`; the first sync,
  `24a856a`, replaced the copy made by hand from ix-mapper-ts 16320bf, and an
  upstream change reaches the jar once its sync lands). The sdk's
  `processResources` embeds both sets as `glam/ix-mappings/{production,staging}`
  beside a build-time `index.json`, `jar` refuses an archive without them,
  and `EmbeddedMappings` / `GlamAccounts.createMapper()` read them back, so a
  consumer supplies no directory; the sdk tests read the same tracked set.
  `-PglamMappingsDir=<path>` points the build at a local checkout of the
  package instead, for documents that are not synced yet.
- `Integ.*` files and other scratch programs are git-ignored and run from the
  IDE: present on a dev machine, absent in CI. Never make anything depend on
  them. In `sdk` and `services` they live in the module's `src/scratch/java`, as
  package-private classes in the package whose package-private members they
  reach for: a suite the module-testing plugin patches into the module, outside
  `main` and `test`, so no mutation suite, fuzz target or certification reads
  them and `check` never compiles them.

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
  release-please cuts releases from them under `always-bump-patch`, so a
  breaking change is named with a `Release-As: x.y.0` footer on a commit that
  changes a file of the package (release-please splits commits by path and
  drops one that touches nothing outside the excluded `ix-mapper-ts/` tree, an
  empty commit included). Don't hand-edit versions or `CHANGELOG.md`.

## Changing a dependency

Much of what this SDK is built on lives in sibling repositories, published
through the Solana BOM (`solanaBOMVersion` in `gradle/sava.properties`):

| Repo | What it owns here |
|---|---|
| `../ravina` | `software.sava.services.*` — RPC calling, backoff/retry, request capacity, load balancing, tx monitoring, epoch service, config parsing (`BackoffConfig`, `ServiceConfigUtil`) |
| `../sava` | `software.sava.core.*` / `software.sava.rpc.*` — keys, instructions, transactions, RPC client |
| `../idl-clients` | `software.sava.idl.clients.*` — SPL, Kamino, Jupiter, Marinade clients |
| `../ix-mapper-java` | `systems.glam.ix_proxy` — the mapping-document parser and the instruction mapper (`ix-proxy`), with their fuzz targets and the ix-mapper-ts conformance suite |
| `../sava-build` | the convention plugin, the hardening feature, and `HARDENING.md` itself |

**A fix belongs in the repo that owns the code, not worked around here.** When
a defect traces into one of the above:

1. Fix it there, and follow *that* repo's process: its AGENTS.md hardening
   block says when its mutation gate runs and which `pitest<Suite>` tasks a
   change there owes; keep its accepted baselines green. sava-build carries no
   mutation suite of its own; its README says how a change there is adopted
   and released.
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
debt made explicit, not acceptance: a row's label is its triage state
(`# untriaged` until a family argument covers it), `pitest<Suite>Debt` prints
the counts, and the block below says what the per-module
`config/pitest/README.md` holds. Every registered fuzz target replays its
checked-in corpus inside `check`, and `:module:hardeningHelp` lists the targets
a module registers. Among them: `services:fuzzAccountData` (the compressed
persistence format — decode + write/read differential, seeded from a mainnet
snapshot; found and fixed an unbounded-decompression hang),
`services:fuzzMinGlamStateAccount` (the state-account walk over nested
length-prefixed ACL sections, plus the change-detection re-walk, seeded from a
mainnet snapshot; found a base-asset index landmine), and
`sdk:fuzzMappingIndex` (the embedded-mappings `index.json` parser on the
mapper's startup path, seeded with an index of the shape the build writes). The
mapping documents themselves are parsed by ix-proxy, whose own
`fuzzMappingConfig` and `fuzzIxMapper` targets cover the parser and the mapper.
Register new harnesses in the owning module's `hardening` block with both
`targetClass` AND `seedCorpus` — GLAM registers no fuzz target without a
checked-in corpus to replay.

The full policy is sava-build's `HARDENING.md`; the process contract for
changes here:

<!-- The block below is a verbatim copy of sava-build's agent-instructions
     template, exactly as `./gradlew --no-daemon -q :sdk:hardeningAgentTemplate`
     prints it for the installed plugin, boundary comments included. Re-take it
     whole whenever that printed block changes; nothing checks it against the
     plugin. See `:sdk:hardeningHelp` for the installed task surface. Do not
     paraphrase or extend the block in place — glam-sdk-java-specific ownership,
     measurements and evidence go under "GLAM-local hardening facts" below it. -->

<!-- hardening-template block:start -->
- Work with the module's `test` task. The mutation suites are a final gate, run once
  per unpushed range when the work is complete and reviewed, before the push: each
  `pitest<Suite>` whose mutated code the range can reach, including suites in
  dependent modules, plus `mutationOwnershipAudit` when production classes or
  target/exclusion rules changed. Never per commit, amend or review round; a change
  the gate forces goes back through review as a delta. `hardeningCertify` (or
  `:hardeningCertifyAll`) and `fuzzAll` are the pre-release checks this repo's notes
  assign an owner to.
- Test-only edits owe the suites whose mutated code those tests exercise; when scope
  is unclear, run `./gradlew qualityGate` from the Gradle root. Doc and comment
  edits owe no suite; a build-script edit only when it changes what
  PIT is given. A change the gate forced owes it again by the same reachability rule
  once reviewed. `pitest<Suite>Verify` answers one way: it keeps its report while only
  recompiled Java sources changed and every recompiled class is byte-identical, which
  proves that suite is owed nothing; a refusal (a moved line, a resource, a build
  script, `src/main/java` under ArcMutate) names its cause and proves nothing by itself.
- When the gate reports unkilled mutants, iterate on one cluster with
  `-PmutateOnly=<class-glob>`. Before any record decision, re-run unscoped with
  `-PnoMutationHistory`: a `[history]` report cannot support adding, removing, or
  relabelling records.
- An unkilled mutant has three outcomes: kill it with a test that asserts the
  property it breaks, refactor it out of existence, or accept it with a written
  reason in `config/pitest/README.md` and a family label on the row. Refreshes seed
  rows `# untriaged`; triage replaces that label. Never accept a `NO_COVERAGE`
  mutant as equivalent; it is an untested line.
- A mutant is a question, not a specification. State the intended property and an
  oracle independent of the implementation before writing the killing test. If they
  contradict current behaviour, prove the bug with a failing regression test first,
  then fix production; never lock a bug in with a passing assertion.
- Write records only through the installed writer tasks: `BaselineUnion` adds
  reviewed rows, `BaselineRetag` refreshes `# line` metadata, `BaselinePrune` deletes
  only after two matching fresh history-free previews, `BaselineUpdate` is for a
  first seed or a reviewed complete rewrite, and `pitest<Suite>BaselineRebase`
  follows a PIT, PIT-plugin/tool-artifact, ArcMutate-base, or certificate change.
  Never hand-edit baseline
  rows or provenance stamps.
- Baseline keys are line-less (`class,method,mutator,STATUS`); `# line` tags are
  review metadata that belong to their row: `BaselineRetag` refreshes them, a hand
  edit is a hand-edited row. Identical rows are sibling mutants and the comparison
  is a multiset: never hand-dedupe.
- `config/pitest/README.md` holds the arguments in force, each updated in place and
  never appended to as a pass report: a family's members, reason, oracle and the
  condition that invalidates it; an audited timeout's cause. The totals the build prints
  are not restated there; the measurements it cannot reconstruct are kept.
- A new `TIMED_OUT` mutant is a reviewer stop, never detection. Record it in
  `config/pitest/<suite>-timeouts.csv` with a cause and argue it in the README; only
  `cause:liveness` certifies. A member whose coordinate has left the population is
  removed by hand after one fresh history-free run with valid committed provenance
  omits it; while provenance is invalid, repair or rebase it first.
- Tests are deterministic: fixed seeds, no sleeps, a clock with a non-zero origin,
  stubs that return distinguishable non-default values, and the subject built inside
  the test body. Exclusions must cover the test source set, not a naming convention.
- Verify by the absence of failures: trust the exit code and the `.running`
  sentinel, not a summary. `MINION_DIED` and `RUN_ERROR` are not results; re-run once
  on a quiet machine. A suite that got faster without getting narrower is a bug report.
- Fuzz findings become a committed seed input and a named regression test. Run
  `fuzzAll` locally with an explicit `-PmaxFuzzTime` and `-PmaxParallelFuzzTargets`
  before a release. Where one thing has two representations, fuzz the differential.
- `./gradlew :module:hardeningHelp` lists the installed tasks and options;
  sava-build's HARDENING.md holds the argument behind every rule above.
<!-- hardening-template block:end -->

### GLAM-local hardening facts

Only local ownership, measurements, acceptance reasons and provenance belong
here; `hardeningHelp` and `hardeningAgentTemplate` are the authorities on task
semantics for the installed plugin version.

**Ownership.** The pre-release `hardeningCertify` (or `:hardeningCertifyAll`,
every project in one invocation) and the `fuzzAll` campaign are owned by the
**local release checklist**, not CI, so certify and fuzz locally before
deciding to release. Both run from a clean
`git worktree add --detach <path> <commit>` of the commit being released, so
no source input outside that tree reaches them; user-level Gradle properties
still do, so check that `savaBuildIdentity` reports no local override and that
no `glamMappingsDir` is set before certifying. `pitestServices` also covers
`:sdk` API changes it calls, so a change under `sdk/` that `services` reaches
owes both suites.

**ArcMutate.** GLAM is outside the Sava ArcMutate certificate — it does not
cover `systems.glam.*` — so both suites run open-source PIT, no `[history]`
report is available here, and no history-assisted exception ever applies to a
GLAM result.

**Mutators.** `pitestSdk` runs `STRONGER,EXPERIMENTAL_NAKED_RECEIVER`;
`pitestServices` adds `EXPERIMENTAL_BIG_DECIMAL` for the `BigDecimal` share math
in `services`; `EXPERIMENTAL_BIG_INTEGER` left the set when its 2026-10-09
re-trial generated no mutants, the `BigInteger` liquidity totals it was enabled
for having left with the Kamino cache. The trials, with their measured numbers,
are under "Mutator trials" in each module's `config/pitest/README.md`. `pitestServices` also sets its own
`timeoutFactor` and `timeoutConst`, and was trialed at 8 threads; the values
and the reasoning are in `services/build.gradle.kts`.

**Test lifecycle.** Neither module uses `@TestInstance(PER_CLASS)` (audited
2026-07-23), so field initializers re-run per test — still build the subject
under test in the test body, and re-audit if a test class adopts `PER_CLASS`.

**Service discovery.** This repo's Gradle tasks run on the **module path** while
PIT minions run on the **class path**, so `module-info` services are invisible
to the minions and a test-resources `META-INF/services` is invisible to the
module-path `test` task. This repo declares no services of its own (audited
2026-07-22), but the trap reached it through a dependency: a test that
ServiceLoads ravina's `MemorySignerFactory` failed under PIT while that jar
declared the service only in `module-info` (hit 2026-07-23). Ravina fixed it
on its main branch in `78b1a3c` ("restore classpath service discovery": a
`META-INF/services` entry beside each `module-info` provider), first released
in 25.5.2; every ravina the BOM has pinned since carries it.

**A wandering count, locally.** Causes already seen here: real waits,
`TIMED_OUT` load flips, coverage attributed to field initializers, and
`@Execution` / `@TestInstance` on an abstract base not reaching concrete
subclasses (JUnit-version-dependent — `javap` the resolved jar rather than
trusting the source).

**Fuzz corpora.** Minimize with `fuzz<Target>Minimize` rather than by hand,
review the resulting corpus diff, and update the corpus provenance notes in
`src/test/resources/fuzz/` when seeds change.

**Fuzz budget.** The pre-release campaign is
`./gradlew --no-daemon fuzzAll -PmaxFuzzTime=300 -PmaxParallelFuzzTargets=3`:
300 seconds per target, three at a time (chosen 2026-09-26), run from the
same clean detached worktree as certification.

**Local instances of the generic rules.** The recording proxies here are
`SolanaRpcClient` / `AccountFetcher` — give them return values a real assertion
can tell from a mutated default.

**The records are a registry.** Each module's `config/pitest/README.md` holds
what the ratchet accepts now: a bullet per family label, a cause per audited
timeout member, the open debt by class, and the measurements behind the suite's
mutator set. The totals the build prints are not restated there and history is
git's (HARDENING.md's README rule, and the block above); the journal as it stood
on 2026-10-09 is `config/pitest/HISTORY.md` beside it, kept verbatim and
unmaintained. Two local rules keep it honest: its prose carries no dates outside
trial measurements, writer receipts, restated policy or source line numbers; and
a member whose recorded argument does not hold keeps its label and says so ("its
acceptance is an open owner decision"; on an audited timeout member, "its
admissibility an open owner decision") until the owner relabels it, retires it or
adopts its seam.

**Family labels.** Every `# <family>` label on an accepted row has its bullet
under "Families" in the owning module's `config/pitest/README.md`; here a row is
not triaged until it does.

**Timeout audit.** Each suite's audited timeout set and its `cause:*`
classifications live in `config/pitest/<suite>-timeouts.csv`; each member's
structural cause belongs in the owning `config/pitest/README.md`, under its class
in "Timed-out mutants (audited set)".

**Fixture deadlines must sit inside the watchdog budget.** A covering test's PIT
budget is its own duration × `timeoutFactor` + `timeoutConst` (HARDENING.md), with
`pitestServices`' values set in `services/build.gradle.kts`. A fixture that waits
longer than that never gets to fail its own assertion. This suite carried
fourteen 5s fixture deadlines against a budget of roughly 1.6–2.8s at the time;
lowering them (1s, and 2s for `MintCacheImplTest`, which did ~600ms of real work
across 128 virtual threads) converted watchdog "detections" into real kills and
exposed assertions that had never actually been testing anything. **Before
classifying any timeout, check this budget first** — a bounded fixture that
outlives the budget is a harness defect, not liveness. Keep new fixture
deadlines well under `timeoutConst`, and prefer a synchronous state reader
(`lock.getReadLockCount()`, a cache accessor) over any wait at all.

When adding a parser, algorithm or strategy: add unit tests, put it in a
mutation suite (the wildcard targeting already mutates new classes by default),
and add a fuzz harness if it consumes external input.

## IDL channel records and movement evidence

Each generated `gen` tree carries a `sources.json` channel record: which published
descriptions of the program exist, their content hashes, and the program's deploy
slot and image hash. The scheduled monitor (`build-scheduled.yml`) regenerates and
compares against these committed records on that workflow's cron schedule; its
Slack digest is the redeploy signal, and the committed records are the baseline it
compares against.

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

`genSrc.sh` refuses to start (exit 6) while the movement report on disk carries entries and
its content is not what HEAD holds — the report of a run whose records were committed
without it, or of a run not committed at all. A second generation would compare against
the records the first already rewrote, report nothing, and overwrite the only copy of what
it saw. The refusal spells out how to keep the report and when discarding it is still safe;
`reportGuard.sh` beside `genSrc.sh` in idl-src-gen is the check, and it never refuses a
fresh checkout, whose report is exactly HEAD's.

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
- The sdk jar embeds the tracked `ix-mapper-ts/src/generated/mapping` sets as
  `glam/ix-mappings/{production,staging}` with an index; a jar without them
  fails the `jar` task rather than publishing empty, which is what every
  release before the embedding did. A set is held to its environment twice:
  `EmbeddedMappings` refuses a document that declares another environment or
  proxies through a program the deployment's `GlamAccounts` do not hold, and
  `GlamAccounts.createMapper(Path|Collection)` refuses the other
  environment's documents by name. The proxy program ids differ, and a wrong
  mapper would fail on chain, not here.
- Every long-running `services` loop (`BatchSqlExecutor`, `AccountFetcher`,
  `GlobalConfigCache`, `StakePoolCache`) takes a trailing
  `systems.glam.services.LoopHeartbeat` through a factory overload and ticks
  it once per completed cycle, on its own thread; the factory javadoc and
  each `run()` say what the cycle is and where the tick sits. A tick must be
  prompt and must not block or throw: an idle batch executor and a reactive
  fetcher tick under the lock their producers queue through, and every loop
  ends on a throwing tick. The heartbeat-less factories pass
  `LoopHeartbeat.NONE`; the one behavioural difference from before the seam
  is that an idle `BatchSqlExecutor` and an idle reactive `AccountFetcher`
  park in timed waits (one wake-up per idle window) instead of indefinitely.
  A supervisor that ticks a lifeline from that hook sets its dead-after
  threshold above the loop's longest *healthy* cycle, not just its idle
  cadence: a batch executor idles on `batchDelay` floored at 100ms, but while
  the database is failing its cycle is the datasource's connection timeout
  plus the backoff delay (`Backoff.fibonacci(1, 21)` by default, so up to
  21s); a reactive fetcher idles on `fetchDelay` floored at 100ms; a polling
  fetcher and the caches tick once per `fetchDelay` as configured plus the
  RPC call and consumer callbacks of one pass (`DefensivePollingConfig`
  defaults: global config 1m, stake pools 12h -- a cadence no plausible
  threshold covers, so leave such a loop exit-only or override its
  threshold).

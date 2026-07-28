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
   with `-PsavaBuildLocalRepo=../sava-build/build/sava-test-repo` (the
   settings block warns loudly and prints the last-publish age). That publish
   is not automatic: re-run it after every sava-build edit, and a *forgotten*
   publish is silent under configuration-cache reuse — if behaviour looks
   stale, check the test repo's `maven-metadata.xml` timestamp directly. The
   property lives on the CLI or in `~/.gradle/gradle.properties`, never in
   the file, so unlike an `includeBuild` there is nothing to un-ship.
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
triage state. Fuzzing is underway — five targets, all seeded from mainnet
snapshots, corpora replayed inside `check`: `services:fuzzAccountData` (the
compressed persistence format — decode + write/read differential; found and
fixed an unbounded-decompression hang), `services:fuzzScopeFeedContext` (the
Scope `Configuration` reader), `services:fuzzReserveContext` (the Reserve
reader + price-chain resolution, the composite-`MostRecentOf` path),
`services:fuzzKaminoVaultContext` (the `VaultState` reader +
allocation-table walk), and `sdk:fuzzMappingConfig` (the ix-mapper config
JSON via `ProgramMapConfig.parseConfig`). Register new harnesses in the
owning module's `hardening` block with `targetClass` AND `seedCorpus` (both
are required — a missing `seedCorpus` silently skips the replay test).

The full policy is sava-build's `HARDENING.md`; the process contract for
changes here:

<!-- This section adapts the agent-instructions template in sava-build's
     HARDENING.md; `agentsTemplateInSync` (wired into `check`) fails when the
     template changes until the block is re-diffed — sync or ACT on each
     changed bullet (a new bullet may need code, not prose) — and the digest
     updated. -->
<!-- hardening-template sha256:f6dea3f41ab7 -->

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
   reason in the module's `config/pitest/README.md` **and a short family
   label on the row itself** — refreshes seed new rows `# untriaged`, and
   triage means replacing that label, so the baseline always says which rows
   are argued and which are debt. Never run `-PupdateMutationBaseline` just
   to make the build pass.
3. **`SURVIVED` and `NO_COVERAGE` are different problems.** A survivor ran
   the line and the test could not tell — a judgment call about equivalence.
   A no-coverage mutant was never executed — mechanical work, and **never
   acceptable as "equivalent"**, because you have not observed its behaviour.
4. **Pure line drift passes on its own** — when every new baseline entry is a
   same-status shift of a stale one and the per-method population is
   unchanged, the verify passes with a notice; refresh at a convenient
   moment. Anything mixed in (newly covered, unexplained, changed counts) is
   triage first, refresh after. `-PnoDriftTolerance` restores strict mode for
   certifying runs. When stale rows are all since-killed and nothing is new,
   `-PpruneMutationBaseline` is the safe shrink-only refresh: it drops rows
   matching nothing this run and never adds or rewrites (`TIMED_OUT`
   coordinates and pending coverage flips are kept, named in the output).
   The three refresh flags are mutually exclusive; the verify's stale-entry
   hint names the safe one per case — prefer it over any hand-rolled cleanup.
   A full `-PupdateMutationBaseline` across a status flip carries the old
   row's `# note` onto the new row, annotated for re-reading — a reason
   written for an unreached mutant is not automatically a reason once its
   behaviour is observable.
5. **Iterate with `-PmutateOnly=<class-glob>`** while killing a cluster —
   seconds instead of the full suite — then re-run unscoped before any
   refresh; the tooling refuses to let a scoped report touch the baseline.
6. **Identical baseline rows are sibling mutants** of one compound condition
   and the comparison is a multiset: never hand-dedupe the CSV. When one
   sibling survives, the verify names the killed sibling's test — the
   survivor is the opposite branch direction; triage it as its own mutant.
   A "new" row identical to an accepted row is classified by the verify as
   a sibling surfaced by the multiset comparison (what upgrading an older
   set-based baseline materializes) — pre-existing debt made visible, to
   accept into its documented family or kill, not a regression. And status
   is part of the row: a `NO_COVERAGE -> SURVIVED` flip is two different
   rows at one coordinate — another reason scripts must never touch the CSV.
7. **Determinism is the whole point.** Fixed seeds, no real waits (PIT
   re-runs covering tests once per mutant, so one sleep is multiplied by the
   mutant count), and no reliance on PIT's timeout: `TIMED_OUT` counts as
   detected but is load-dependent — the same mutant can report `SURVIVED`
   alone and `TIMED_OUT` under `qualityGate`. Verify baselines in both modes;
   union only rows observed to flip. A flaky harness is worse than recorded
   debt — if an interleaving cannot be made deterministic, accept the mutant
   with a written reason.
8. **A new timed-out mutant is a reviewer-stop, not detection noise.** For
   exactly these mutants the ratchet cannot see a weakened covering
   assertion — a timeout keeps "detecting" whatever the test asserts — so
   each suite's timeouts are an audited set, not a count:
   `config/pitest/<suite>-timeouts.csv` holds line-less
   `class,method,mutator` keys, and the README's "Timed-out mutants
   (audited set)" section the structural cause per member (the stalled
   chunking loop, the lost signal, the leaked unlock). The verify warns on
   any timeout outside the set — paste the printed row, then write the
   cause — and on members matching no mutant; admit a newcomer only with
   its cause written. The key is the check's resolution: a new timed-out
   mutant in an already-audited method+mutator draws no warning, so name
   the line in the README cause and re-read it when that code changes.
9. **A suite's percentage is not a target.** An accepted mutant with a
   written reason is finished work, not debt. Before trying to raise a
   number, check whether the remainder is `NO_COVERAGE` (real work) or
   documented equivalents (already closed).
10. **Allocation and timing harnesses are a last resort**, reserved for
   properties that are a stated design goal; they need a `volatile` sink and
   flap when margins are thin.
11. When a test you believe in will not go green, **suspect the code before
   you soften the assertion** — that is where this process finds real bugs.
12. **A wandering unkilled count is a defect, not noise** — chase it before
   refreshing any baseline. Known causes: real waits, `TIMED_OUT` load flips,
   `@Execution`/`@TestInstance` on an abstract base not reaching concrete
   classes (JUnit-version-dependent; `javap` the resolved jar), and coverage
   attributed to field initializers — exercise factories from inside a
   `@Test`.
13. **Build the subject under test inside the test body, not in a field.**
    Under `PER_CLASS` lifecycle a field-initialized client's construction
    coverage attaches to whichever test runs first, so wiring mutants can
    never pair with the test that drives what they wire — they survive even
    under a harness that asserts every request; one test that constructs
    the client in the test method and drives each configured path restores
    the pairing. This repo's tests use the default per-method lifecycle (no
    `@TestInstance(PER_CLASS)` in either module, audited 2026-07-23), so
    field initializers re-run per test — but prefer building in the test
    body anyway, and re-audit if a test class adopts `PER_CLASS`.
14. **Kill rates are bounded by the mutator set.** `BigInteger`/`BigDecimal`
    arithmetic is method calls, invisible to the default arithmetic mutators —
    fixed-point and fee math needs `EXPERIMENTAL_BIG_INTEGER` (pitest ≥
    1.25.8) — and fluent calls returning their receiver are expressions,
    invisible to `VoidMethodCallMutator`, so builder-style writes need
    `EXPERIMENTAL_NAKED_RECEIVER`. Trial per suite, enable only what fires,
    and record the numbers in `config/pitest/README.md`. Both suites here run
    `STRONGER,EXPERIMENTAL_NAKED_RECEIVER`; the trial numbers are recorded.
15. **PIT minions run on the class path**, even though this repo's tasks run
    on the module path: `module-info` services are invisible to them, and a
    test-resources `META-INF/services` is invisible to the module-path `test`
    task. Real services are declared in both places; a harness whose result
    depends on which task ran it is never committed. This repo declares no
    services of its own (audited 2026-07-22), but the trap reaches through
    **dependencies** too: a test that ServiceLoads a factory from a dependency
    jar (ravina's `MemorySignerFactory`) fails under PIT when that jar
    declares the service only in `module-info` — hit 2026-07-23; ravina fixed
    it in `bde97ec` ("restore classpath service discovery"), and glam's
    `pitestServices` needs a ravina release carrying it.
16. **Exclusions must cover the test source set, not a naming convention**:
    shared fakes named `RecordingFoo` / `StubFoo` match no `*Test*` pattern.
    After registering or widening a suite, list the mutated classes and
    confirm none live under `src/test` (`pitest<Suite>Verify` warns, naming
    them).
17. **Verify by the absence of failures, not the presence of passes.** A
    green build can mean the task was up-to-date rather than that tests ran;
    check the failure count and that the task executed. A *failed* PIT run
    leaves the previous report in `build/reports/pitest/<suite>/`, so the
    summary you read can describe a run that never happened — trust the exit
    code, and delete report directories when comparing runs.
18. **A suite that got faster without getting narrower is a bug report** —
    unless the summary carries the `[history]` marker (arcmutate incremental
    reuse, where fast is expected; the pre-release gate still runs
    `-PnoMutationHistory` to re-earn every status from scratch).
19. **Transient infra failures are not results.** PIT `MINION_DIED` fails
    before writing a report — re-run the suite; a Gradle-worker
    `EOFException` death is the same shape, and per-mutant `RUN_ERROR` under
    load the same shape smaller (not counted as detected). The daemon log
    (`~/.gradle/daemon/<version>/daemon-<pid>.out.log`) keeps a failed
    build's full output even when the shell discarded it.
20. **Fuzz findings become a committed seed input and a named regression
    test**, never just a fix — and the committed corpus is replayed by a unit
    test inside `check`, so it cannot rot between fuzz runs. **When one thing
    has two representations, fuzz the differential** — assert the two agree
    rather than that neither crashes; crash-only fuzzing cannot see a wrong
    answer. Minimize a corpus with `fuzz<Target>Minimize` (libFuzzer
    `-merge=1`; `-PadoptLocalCorpus` opts in locally found inputs), never by
    hand — review the diff and update the corpus provenance README.
21. **Stubs and fixtures return distinguishable, non-default values.** A stub
    or proxy returning `null`/`0`/`""`/`true`/empty makes the matching
    return-value mutant equivalent by accident of the fixture — the same trap
    as a test clock starting at 0. Give recording proxies (the `SolanaRpcClient`
    / `AccountFetcher` proxies here) return values a real assertion can tell
    from the mutated default.
22. **Copy-on-write clusters split by direction.** For a method returning an
    unmodifiable view (`List.copyOf(...)`, `List.of(...)` — e.g.
    `KaminoCacheImpl.vaultContexts`), assert the immutability
    (`assertThrows(UnsupportedOperationException, ...)`) as well as the
    contents: the mutable-escape direction is a kill, and only the
    content-equal siblings are family-accepted equivalents.

When adding a parser, algorithm or strategy: add unit tests, put it in a
mutation suite (the wildcard targeting already mutates new classes by
default), and add a fuzz harness if it consumes external input.

Every `# <family>` label on an accepted baseline row must be named in the
module's `config/pitest/README.md` (a "Family labels" glossary) — the verify
and debt tasks warn on any label with no `# <label>` mention there, so a
typo or an orphaned argument surfaces instead of silently opening a bucket.

## Gotchas & invariants worth knowing

- `GlamAccountClient.createClient` routes to `GlamStagingAccountClientImpl`
  when the accounts' protocol program equals
  `GlamAccounts.MAIN_NET_STAGING.protocolProgram()` — prod and staging are
  separate generated trees, and instruction layouts can differ between them.
- The generated `gen` trees are large (hundreds of files); searches are much
  faster when scoped to the hand-written packages (`-not -path '*/gen/*'`).
- The sdk jar embeds the untracked `glam/mapping-configs-v1` directory; a
  clean clone needs `./downloadMappings.sh` before the jar is meaningful.

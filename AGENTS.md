# AGENTS.md

Guidance for AI coding agents (JetBrains AI Assistant, Junie, etc.) working in this repository.

## Project

`glam-sdk-java` — the Java SDK for GLAM. Published as a sibling Java dependency
consumed by other GLAM services (e.g., `vault-stat-service`).
Build: Gradle (`settings.gradle.kts`, multi-module). Top-level modules:

- `sdk/` — the core SDK library.
- `services/` — service-layer helpers built on top of the SDK.
- `examples/` — runnable example programs.
- `glam/mapping-configs-v1/` — mapping configuration resources.

Supporting files: `main_net_programs.json`, `conf/`, `downloadMappings.sh`,
`syncMappings.sh`, `release-please-config.json`.

## Related source code (symlinks/)

The `symlinks/` directory at the repository root contains symlinks to source trees of
dependencies and related codebases that live outside this repository on the local
machine. Agents should read from these locations when they need to understand,
cross-reference, or debug behavior that originates in a dependency rather than in
this SDK itself.

The `symlinks/` tree is organized into two groups:

- `symlinks/deps/` — sibling Java projects this SDK depends on, developed in
  lockstep with `glam-sdk-java`. **Code changes in these repos are permitted**
  when a task requires it (e.g., adding a missing API, fixing a bug in a shared
  library). Coordinate the change with this SDK's update. If a
  [sava-software](https://github.com/sava-software)-owned project dependency is
  missing from `symlinks/deps/`, the folder may be created and the repository
  checked out from `https://github.com/sava-software/<COMPONENT_NAME>.git`
  (e.g., `https://github.com/sava-software/idl-clients`).
- `symlinks/external/` — third-party / non-sibling repos. Treat as read-only
  reference material; do not modify unless the task explicitly requests it.

Contents of `symlinks/deps/` (sibling Java dependencies — editable):

- `deps/ravina` → `/Users/jim/src/ravina` — shared Java libraries used by this SDK.
- `deps/idl-clients` → `/Users/jim/src/idl-clients` — IDL-generated Java clients
  for on-chain programs (Kamino, etc.). Any type under the
  `software.sava.idl.clients.*` package — including generated account/state
  classes such as `VaultState` and their `*_OFFSET` constants — is sourced from
  this repo under
  `idl-clients-<program>/src/main/java/software/sava/idl/clients/<program>/...`.
  When you need a field offset, layout, or generated type referenced from this
  SDK, look here first rather than guessing.

Contents of `symlinks/external/` (third-party / non-sibling repos — read-only):

- `external/HikariCP` → `/Users/jim/src/HikariCP` — JDBC connection pool (Java)
  referenced by downstream consumers of this SDK.

### How to use them

- Code changes are allowed in `symlinks/deps/` when the task requires updating a
  sibling Java dependency; everything else under `symlinks/` is read-only by
  default and must not be modified unless the task explicitly requests it.
- When investigating types, IDL definitions, account layouts, or program logic
  referenced from this codebase, prefer reading the corresponding source under
  `symlinks/` over guessing from class/method names.
- For Java dependency behavior, look under `symlinks/deps/ravina` or
  `symlinks/external/HikariCP` as appropriate.
- Within any repo under `symlinks/deps/`, **ignore nested `symlinks/`
  directories** (e.g., `symlinks/deps/ravina/symlinks/`). Those are that repo's
  own dependency links and following them leads out of the intended reference
  scope. Focus instead on the project's own source — primarily `src/main/java/`
  — along with any in-repo documentation (top-level `README*`, `AGENTS.md`,
  `docs/`, Javadoc, and module-level `build.gradle*`). Build outputs, generated
  sources, IDE config, and test fixtures can also be skipped unless directly
  relevant.

### Keeping symlinked repos up to date

The repositories under `symlinks/external/` should be refreshed via `git pull`
from this project before relying on their contents. Entries under
`symlinks/deps/` are sibling Java projects developed in lockstep with this SDK;
their checkouts are managed by the developer and should not be pulled
implicitly as part of a task here.

Each symlink target is an independent git checkout on the local machine, so run
`git pull` in each relevant one (on its current branch) at the start of a task
that depends on it:

```sh
for d in symlinks/external/*/; do
  echo "=== $d ==="
  git -C "$d" pull --ff-only || echo "skip: $d"
done
```

Notes:

- Use `--ff-only` to avoid creating accidental merge commits in those external
  repos. If a pull is rejected, investigate that repo manually rather than
  forcing it from this project.
- Do not commit changes inside the symlinked repos as part of work on this
  SDK unless the task explicitly requests it. When you do make code changes
  under `symlinks/deps/`, commit them in their own repository, not here.

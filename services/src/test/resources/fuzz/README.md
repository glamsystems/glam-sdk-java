# Fuzz seed corpora

## accountData

Inputs are raw payload bytes: the harness both decodes them as a compressed
account file and round-trips them through the write path (see
`AccountDataFuzz`). Seeds:

- `mainnet-mappings.gz` — the checked-in mainnet Scope OracleMappings
  snapshot's compressed file bytes (provenance: `../accounts/kamino/README.md`),
  so the decode half starts from a real well-formed file.
- `gzip-magic-only` — the two-byte gzip magic, pinning the truncated-header
  rejection path.
- `empty` — the zero-length file the corrupted-file deletion tests care about.
- `bomb-16m-zeros.gz` — a 16KB file that decompresses to 16MiB of zeros. This
  is a **finding**: `readAccountData` used to `readAllBytes` unbounded, so a
  corrupted or hostile file hung the reader inflating gigabytes until memory
  died (found here; the campaign RSS climbed without limit). Fixed by a 10MiB
  read cap (the Solana account ceiling); pinned by
  `FileUtilsTests.aDecompressionBombIsRejectedNotInflated`.

Findings become a named seed here **and** a regression test; the committed
corpus is replayed inside `check` by the generated
`AccountDataFuzzSeedReplayTest`.

## scopeFeedContext

Raw Scope `Configuration` account bytes fed to `ScopeFeedContext.createContext`
plus every offset accessor (`ScopeFeedContextFuzz`). Crash-only: garbage in ->
`RuntimeException` out. The reader does not validate length — production gates
these bytes behind `KaminoCacheImpl.accept`'s length + discriminator check, so
the target pins the parser directly against a gate-dropping refactor. Seeds:

- `mainnet-configuration` — the real mainnet Configuration snapshot decompressed
  to raw account bytes (provenance: `../accounts/kamino/README.md`).
- `short-past-offsets` — a 160-byte buffer that builds a context but throws on
  the deeper accessor reads; pins the tolerated short-buffer path.

## reserveContext

Raw Kamino Reserve bytes fed to `ReserveContext.createContext` against a real
mainnet `MappingsContext` (`ReserveContextFuzz`), so the seed resolves an actual
price chain and the mutator drives `ScopeEntries.readPriceChains` — where the
composite (`MostRecentOf`) chain handling lives — with hostile reserve bytes.
Crash-only: garbage in -> `RuntimeException` out; a `StackOverflowError` from a
cyclic chain is a finding (keeps the upstream cycle-guard regression covered).
The raw `OracleMappings` parser is fuzzed upstream in idl-clients. Seeds:

- `mainnet-sol-reserve` — the real SOL Reserve snapshot decompressed to raw
  bytes; its price feed matches the registered mappings, so it resolves a chain.
- `short` — a 200-byte buffer pinning the truncated-reserve rejection.

## kaminoVaultContext

Raw Kamino `VaultState` bytes fed to `KaminoVaultContext.createContext`
(`KaminoVaultContextFuzz`), exercising `parseReserveKeys` — the allocation-table
walk whose slot count comes from the bytes and which had off-by-one/terminator
bugs before. Crash-only. Seeds:

- `mainnet-vault-state` — the real vault-state snapshot decompressed to raw bytes.
- `short` — a 200-byte buffer pinning the truncated-vault rejection.

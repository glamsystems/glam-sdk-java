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

## minGlamStateAccount

Raw Glam state-account bytes fed to `MinGlamStateAccount.createRecord` and then
through `createIfChanged` (`MinGlamStateAccountFuzz`). The layout is a chain of
length-prefixed sections — assets, integration ACLs, delegate ACLs with nested
integration- and protocol-permission blocks, external positions — and every one
of those counts comes from the account. The change-detection path re-walks the
same prefixes independently of the parse path, so both are driven. Crash-only:
garbage in -> `RuntimeException` out. Seeds:

- `mainnet-state-account` — the real mainnet state-account snapshot (the same
  bytes as `MinGlamStateAccountTests.STATE_B64`), decompressed to raw bytes.
- `truncated` — its first 512 bytes, pinning the short-buffer rejection.
- `absent-base-asset` — a **finding** (first campaign, 2026-08-06):
  `Arrays.binarySearch` returns `-(insertion point) - 1` for an absent key, and
  the record stored that index for `baseAssetMint()` to index `assets` with. A
  base asset missing from its own assets vector parsed cleanly and produced a
  record that threw `ArrayIndexOutOfBoundsException: Index -3 out of bounds for
  length 5` at first use — a landmine far from its cause. Now rejected at parse;
  pinned by
  `MinGlamStateAccountMalformedTests.aBaseAssetMissingFromTheAssetsVectorIsRejectedAtParse`.

This target was added after two robustness defects were found here by hand
(unvalidated counts used as array sizes; an O(n) walk over an unvalidated
count). It found a third in five seconds.

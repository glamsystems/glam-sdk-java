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

## scopeFeedContext / reserveContext / kaminoVaultContext — moved

These three targets, their harnesses and seed corpora moved to
`vault-stat-service` with the Kamino cache on 2026-08-21; their provenance
notes moved with them.

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

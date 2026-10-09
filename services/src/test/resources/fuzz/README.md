# Fuzz seed corpora

## accountData

Inputs are raw payload bytes: the harness both decodes them as a compressed
account file and round-trips them through the write path (see
`AccountDataFuzz`). Seeds:

- `mainnet-mappings.gz` — the compressed file bytes of a mainnet Scope
  OracleMappings snapshot (account `4zh6bmb7…`, fetched 2026-07-21 with
  `getAccountInfo` from `api.mainnet-beta.solana.com`; the Kamino fixtures it
  came from moved to vault-stat-service on 2026-08-21), so the decode half
  starts from a real well-formed file.
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

Raw Glam state-account bytes, each driven three ways by `MinGlamStateAccountFuzz`:
parsed by `MinGlamStateAccount.createRecord`; if it parses, offered back to its
own record through `createIfChanged` with the same bytes at a newer slot (the
unchanged-bytes walk); and, whatever the parse said, offered to `createIfChanged`
as an update of the well-formed mainnet record (`mainnet-state-account` below),
so every section the input changes is reparsed from counts the input controls.
The layout is a chain of length-prefixed sections — assets, integration ACLs,
delegate ACLs with nested integration- and protocol-permission blocks, external
positions — and every one of those counts comes from the account. The
change-detection path re-walks the same prefixes independently of the parse
path, so both are driven. Crash-only: garbage in -> `RuntimeException` out,
except a raw `NegativeArraySizeException`, which is an array sized by an
unchecked count rather than a rejection. Seeds:

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
- `negative-external-positions-count` — a **finding** (2026-10-09), though not a
  campaign's: the review of the `delegateAclsOffset` and `externalPositionsOffset`
  `RemoveConditionalMutator_ORDER_ELSE` timeouts read it off the code. No
  campaign could have reached it, because the harness then offered
  `createIfChanged` only the bytes `createRecord` had just accepted, so every
  section compared unchanged; the update drive above closes that gap.
  `createIfChanged` sized each section it reparses from the raw count, so an
  update whose external-positions count is -1 threw a raw
  `NegativeArraySizeException`, and one whose count is 0x72e2ac09 would have
  allocated about 7.2 GiB before `readArray` ran out of bytes. Now each count is
  bounded before it is walked or allocated from, as `createRecord` bounds it, and
  the update is refused with the exception the parse throws for the same bytes;
  pinned by
  `MinGlamStateAccountMalformedTests.aNegativeExternalPositionsCountIsRejectedOnUpdate`
  and its siblings, a negative and an oversized count for each of the four
  sections. The seed is the mainnet snapshot with only that count set to -1. A
  large count would reproduce the finding too, but replayed under a mutant that
  disables the bound it would allocate gigabytes inside the replay test, where
  -1 fails in microseconds.
- `zero-stride-policy-data-length` and `wrapping-protocol-permissions-count` — a
  **finding** (2026-10-09), read off the code in the same review. Both walks
  iterated each integration ACL's policy count and each delegate's
  integration-permissions count unchecked, and advanced by a stride the account
  controls: a policy data length of -6, or a protocol-permissions count whose
  ten-byte block wraps an int to -36, holds the walk in place, so a large count
  beside it would spin the walk for up to 2^31 iterations. Every nested count and
  length is now bounded the same way in both walks; pinned by
  `MinGlamStateAccountMalformedTests.aNegativePolicyDataLengthIsRejected`,
  `aWrappingProtocolPermissionsCountIsRejected` and their siblings. The seeds keep
  the snapshot's count of one beside the bad stride, so a mutant that disables
  the bound replays one stalled step, never a spin.

This target was added after two robustness defects were found here by hand
(unvalidated counts used as array sizes; an O(n) walk over an unvalidated
count). It found a third in five seconds.

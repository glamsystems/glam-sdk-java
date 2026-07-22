Mainnet account snapshots fetched 2026-07-21 via `getAccountInfo` (base64) from
`https://api.mainnet-beta.solana.com`, stored as gzipped raw account data.

- `6cMwdbrJ…` — scope Configuration (klend feed, `ScopeFeedAccounts.SCOPE_MAINNET_KLEND_FEED`)
- `4zh6bmb7…` — its OracleMappings
- `d4A2prbA…` — main-market SOL Reserve (KLend)
- `5YxwKgsv…` — a KVault VaultState

To refresh: fetch the same keys, verify lengths still match the generated
`*.BYTES` constants, and re-gzip. KaminoCacheTests pins the snapshot's SOL
price chain shape (MostRecentOf composite head) — re-check that assertion
after refreshing.

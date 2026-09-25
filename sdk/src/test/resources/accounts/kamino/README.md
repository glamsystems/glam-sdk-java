# Kamino mainnet account fixture

`d4A2prbA…` is the main-market SOL Reserve (KLend), a mainnet snapshot fetched
2026-07-21 via `getAccountInfo` (base64) from `https://api.mainnet-beta.solana.com`
and stored as gzipped raw account data; the same snapshot the services suite
carried under `accounts/kamino/` before the Kamino cache moved to
vault-stat-service. `GlamJupiterProgramClientTests` decodes it to check that a
reserve's refresh accounts come from its own bytes: market
`7u3HeHxYDLhnCoErrtycNokbQYbWGzLs6JSDqGAv5PfF`, wSOL liquidity, a Scope price
feed and no Pyth or Switchboard oracle.

To refresh: fetch the same key, verify the length still matches the generated
`Reserve.BYTES`, re-gzip, and re-check the assertions above.

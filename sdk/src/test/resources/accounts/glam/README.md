# GLAM GlobalConfig fixture

`6avract7…` is the mainnet GlobalConfig account of the glam_config program (the PDA of
seed `global-config`, shared by the production and staging deployments), stored as
gzipped raw account data. It is a byte-identical copy (sha1
`9efbb8ac87e11f5f5e85a367f128bc0ebc19e18f`) of
`services/src/test/resources/accounts/glam/global/6avract7….json.gz`: the same bytes
were first committed there in `c21dbf0` (2026-01-25, as a zip whose entry is dated
2026-01-24) and repacked as `.json.gz` in `d5e2167` (2026-06-12), so the snapshot is
from January 2026 and records no fetch slot. It carries 52 asset
metas, all at priority 0: among them USDC (`EPjF…`, PythLazerStableCoin), wrapped SOL
(`So11…`, PythLazer), mSOL (PythLazer), METADDFL (SwitchboardOnDemand) and four xStock
mints priced through ChainlinkRWA; no SOL-denominated (stake pool or Marinade) source
and no KaminoReserve source, which the resolver tests build synthetically.

`systems.glam.sdk.mapping` tests decode it to check the oracle prefix the Orca liquidity
handlers read. To refresh: fetch the same key (`getAccountInfo`, base64), re-gzip, and
re-check the oracle keys the tests name.

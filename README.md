# GLAM Java SDK

## CCTP program selection

Current CCTP instructions and permissions are provided by the `ext_bridge` bindings under
`systems.glam.sdk.idl.programs.glam.bridge.gen`. Use `GlamAccounts.bridgeIntegrationProgram()`
and `Protocol.CCTP`; the bridge permission flags are `Send`, `Validate`, and `Settle`.

The standalone `ext_cctp` bindings remain available for existing vaults, but that program is
legacy. Use the explicit `GlamAccounts.legacyCctpIntegrationProgram()` and
`Protocol.LEGACY_CCTP` APIs for it. The older `cctpIntegrationProgram()`-named accessors are
deprecated compatibility aliases and continue to point to the legacy program, not `ext_bridge`.

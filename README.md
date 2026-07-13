# GLAM Java SDK

## CCTP program selection

Current CCTP instructions and permissions are provided by the `ext_bridge` bindings under
`systems.glam.sdk.idl.programs.glam.bridge.gen`. Use `GlamAccounts.bridgeIntegrationProgram()`
and `Protocol.BRIDGE_CCTP`; the bridge permission flags are `Send`, `Validate`, and `Settle`.
LayerZero OFT under the same integration is identified consistently as
`Protocol.BRIDGE_LAYERZERO_OFT`.

The standalone `ext_cctp` bindings remain available for existing vaults through
`GlamAccounts.cctpIntegrationProgram()` and `Protocol.CCTP`. That integration is deprecated for
new vault configuration, but its program identity and existing API names remain unchanged.

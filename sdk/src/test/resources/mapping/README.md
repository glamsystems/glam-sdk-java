# Mapping-document fixtures

The system-program mapping documents of both environments, copied verbatim from
`src/generated/mapping/{production,staging}/11111111111111111111111111111111.json` of
glamsystems/ix-mapper-ts at `16320bf603b45f251567370172de8a0498bbeac4` (the commit
`downloadMappings.sh` pins). They let the sdk's mapper tests run without the untracked
`glam/` download: `GlamAccountsMapperTests` builds a mapper from each, checks the
environment guard against the other, and maps a vault SOL transfer against the
generated `GlamProtocolProgram.systemTransfer` layout.

`syncMappings.sh` refreshes them from the newly pinned documents whenever it moves the
pin; a document here is a test input, never edited by hand.

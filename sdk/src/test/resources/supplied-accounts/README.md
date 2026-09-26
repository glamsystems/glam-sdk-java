# Supplied-accounts test documents

`staging/whirLbMi….json` and `staging/1oopBoJG….json` are the staging mapping documents
the monorepo generates once the five supplied-account entries flip from `unsupported`,
copied verbatim from glamsystems/glam PR #1440 at commit 9ad419cca
(`packages/glam/ix-mapper-ts/src/generated/mapping/staging/`, generator
idl-src-gen@633ad602). They are test resources only, under a path the jar does not embed;
the tracked `ix-mapper-ts/` set at the repository root, and so the jar's embedded documents,
carry these entries once the public sync lands, at which point these copies are due a
refresh or a switch to the embedded set.

`SuppliedAccountsMapperTests` maps native instructions through them with the
`systems.glam.sdk.mapping` supplier and checks the inserted prefix.

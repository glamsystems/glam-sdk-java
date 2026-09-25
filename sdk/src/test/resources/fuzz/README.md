# Fuzz seed corpora

## mappingIndex

Arbitrary bytes parsed as the embedded-mappings index, exactly as `EmbeddedMappings.index()`
parses `glam/ix-mappings/index.json` out of the jar (see `MappingIndexFuzz`). Malformed-input
contract: garbage in -> `RuntimeException` out; an admitted index names only plain
`<program>.json` file names. Seeds:

- `index.json` — the shape the build writes: a source and both environments' file names.
- `empty-sets` / `empty-object` — minimal shapes pinning the empty-and-absent paths.
- `path-in-name` — a name that would read outside the set, which the parser refuses.

The mapping documents themselves are parsed by ix-mapper-java, which fuzzes its own parser.

Findings become a named seed here **and** a regression test; the committed corpus is replayed
inside `check` by the generated `MappingIndexFuzzSeedReplayTest`.

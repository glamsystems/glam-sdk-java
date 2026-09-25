package systems.glam.sdk;

/// Jazzer entry point for the embedded-mappings index parser, the one JSON reader this
/// module owns on the mapper's startup path: `EmbeddedMappings.index()` reads
/// `glam/ix-mappings/index.json` from the jar before any document is opened. (The documents'
/// own parser is fuzzed where it lives, in ix-mapper-java.)
///
/// The fuzz payload is arbitrary bytes parsed as the index, exactly as the jar's bytes are.
/// Malformed-input contract: garbage in -> `RuntimeException` out; an index that admits names
/// only plain `<program>.json` file names, so the loader can never read outside the set.
/// Jazzer flags what the contract forbids: hangs, memory exhaustion, any
/// non-`RuntimeException` throwable, and an admitted name that is not a file name.
///
/// Seeded from the index the build writes, under src/test/resources/fuzz/mappingIndex.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :sdk:fuzzMappingIndex [-PmaxFuzzTime=<seconds>]`.
public final class MappingIndexFuzz {

  public static void fuzzerTestOneInput(final byte[] data) {
    final EmbeddedMappings.Index index;
    try {
      index = EmbeddedMappings.parseIndex(data);
    } catch (final RuntimeException tolerated) {
      // malformed or truncated index JSON: rejection is in contract
      return;
    }
    if (index.source() == null) {
      throw new AssertionError("an admitted index has no source");
    }
    for (final var env : GlamEnv.values()) {
      for (final var name : index.files(env)) {
        if (name == null || !name.endsWith(".json") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
          throw new AssertionError("an admitted index names " + name);
        }
      }
    }
  }
}

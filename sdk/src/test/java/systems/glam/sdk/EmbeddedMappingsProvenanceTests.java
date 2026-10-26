package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/// Every embedded document names the proxy IDL it was generated from by its digest (`glam managed
/// <store> (idl <sha256 prefix>)`), and the sdk carries that IDL verbatim as the proxy program's
/// `gen/idl.json`, beside the client generated from it. The two move separately: idl-src-gen
/// regenerates the client here when the program is redeployed, and the monorepo's public sync
/// brings the documents regenerated from the same store, so one is behind the other until both
/// have landed. A document behind the client seats a handler's accounts as they were, which fails
/// on chain, not here; this holds the pair together so that the one behind fails `check` instead,
/// naming which.
final class EmbeddedMappingsProvenanceTests {

  /// The digest as the provenance spells it: at least the twelve hex digits the generator writes.
  private static final Pattern IDL_DIGEST = Pattern.compile("\\(idl ([0-9a-f]{12,64})\\)");
  /// The generated program trees, from the module directory the build runs tests in.
  private static final Path GEN_ROOT = Path.of("src/main/java/systems/glam/sdk/idl/programs/glam");

  /// The trees from the module directory, or from the repository root when run from there.
  private static Path genRoot() {
    if (Files.isDirectory(GEN_ROOT)) {
      return GEN_ROOT;
    }
    final var fromRepositoryRoot = Path.of("sdk").resolve(GEN_ROOT);
    assertTrue(Files.isDirectory(fromRepositoryRoot),
        "no generated program trees under " + GEN_ROOT.toAbsolutePath() + " or " + fromRepositoryRoot.toAbsolutePath());
    return fromRepositoryRoot;
  }

  /// The address an IDL declares at its top level; the nested accounts' addresses are skipped with
  /// the fields that hold them.
  private static PublicKey address(final byte[] idl) {
    return JsonIterator.parse(idl).parseObject(new AddressParser());
  }

  private static final class AddressParser implements FieldBufferPredicate, Supplier<PublicKey> {

    private PublicKey address;

    @Override
    public PublicKey get() {
      return address;
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("address", buf, offset, len)) {
        address = PublicKey.fromBase58Encoded(ji.readString());
      } else {
        ji.skip();
      }
      return true;
    }
  }

  /// True for a path below the trees with a `next` element: the `next/gen` tree renders the vcs
  /// channel beside the client and is no client of the store the documents name.
  private static boolean isNextTree(final Path root, final Path file) {
    for (final var element : root.relativize(file)) {
      if (element.toString().equals("next")) {
        return true;
      }
    }
    return false;
  }

  /// The sha256 of each program tree's `gen/idl.json`, by the address it declares, one tree per
  /// program.
  private static Map<PublicKey, String> carriedIdlDigests() throws IOException, NoSuchAlgorithmException {
    final var root = genRoot();
    final var sha256 = MessageDigest.getInstance("SHA-256");
    final var digests = new HashMap<PublicKey, String>();
    try (final var files = Files.walk(root)) {
      for (final var file : files.filter(f -> f.getFileName().toString().equals("idl.json")).toList()) {
        if (!file.getParent().getFileName().toString().equals("gen") || isNextTree(root, file)) {
          continue;
        }
        final var idl = Files.readAllBytes(file);
        final var address = address(idl);
        assertNotNull(address, file + " declares no address");
        final var previous = digests.put(address, HexFormat.of().formatHex(sha256.digest(idl)));
        assertNull(previous, address.toBase58() + " is the address of two generated trees, the second at " + file);
      }
    }
    assertFalse(digests.isEmpty(), "no gen/idl.json under " + root.toAbsolutePath());
    return digests;
  }

  @Test
  void everyEmbeddedDocumentNamesTheProxyIdlTheSdkCarries() throws IOException, NoSuchAlgorithmException {
    final var carried = carriedIdlDigests();
    for (final var env : GlamEnv.values()) {
      final var documents = EmbeddedMappings.load(env);
      assertFalse(documents.isEmpty(), env + ": no embedded documents");
      for (final var document : documents) {
        final var name = env + " " + document.programId().toBase58();
        final var provenance = document.provenance();
        assertNotNull(provenance, name + ": no provenance");
        assertNotNull(provenance.proxyIdl(), name + ": names no proxy IDL");
        final var matcher = IDL_DIGEST.matcher(provenance.proxyIdl());
        assertTrue(matcher.find(), name + ": no digest in " + provenance.proxyIdl());
        final var named = matcher.group(1);
        final var proxy = document.proxyProgramId().toBase58();
        final var digest = carried.get(document.proxyProgramId());
        assertNotNull(digest, name + ": the sdk carries no client of " + proxy);
        assertEquals(named, digest.substring(0, named.length()),
            name + " was generated from proxy IDL " + named + ", but the sdk's client of " + proxy + " was generated from "
                + digest.substring(0, named.length()) + ": whichever is behind the program's redeploy, regenerate the client "
                + "(idl-src-gen) or wait for the monorepo's public sync to bring the document.");
      }
    }
  }
}

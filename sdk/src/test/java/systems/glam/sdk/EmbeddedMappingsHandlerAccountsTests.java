package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.ix.proxy.Derivation;
import systems.glam.ix.proxy.DestinationAccount;
import systems.glam.ix.proxy.InstructionEntry;
import systems.glam.ix.proxy.MappingDocument;
import systems.glam.ix.proxy.OptionalKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Every mapped entry of every embedded document seats the accounts of a handler the sdk's
/// client of the proxy program declares, in the program's own IDL beside that client
/// (`gen/idl.json`). The document and the client are regenerated separately, the one by the
/// monorepo's public sync and the other by idl-src-gen, so after a redeploy that changes a
/// handler's accounts one of them is behind until both have landed: the jar then builds an
/// instruction the program refuses, from the mapper on the document's side or from the
/// generated builders on the client's. Whichever side is behind, this fails `check` when the
/// disagreement reaches a seat both sides describe: a count the document falls short of, a
/// GLAM or supplied seat meeting another account, a fixed seat meeting an account the handler
/// fixes at another address, a sentinel seat meeting a required account, a flag the handler
/// requires and the seat lacks. It cannot see a handler that dropped its last account, since
/// a document may seat more than the handler's list; an appended account on an entry that
/// already seats past the list, or, on the client's side, any appended account; a fixed seat
/// meeting an account the handler fixes at no address; or a forwarded seat meeting any other
/// account whose flags it covers, since forwarded seats are held to their flags (and a
/// sentinel to an optional account) alone (the `bridge_routes` insertion was caught on both
/// sides because the forwarded seats behind it met accounts of other flags). It stays green
/// through a redeploy that changes only metadata, docs, errors or types.
///
/// What the two sides agree on. A document seats every account the handler declares, in the
/// handler's order: GLAM's programs build without Anchor's `allow-missing-optionals`, so an
/// absent optional still takes its slot, as the program's id, and a seat the handler takes may
/// not forward a native position the client leaves out. The document may seat more (accounts
/// the handler reads from its remaining accounts: the natives ext_phoenix forwards after its
/// own seven, the global configuration the Orca handlers read after theirs). A seat's flags
/// cover what the handler declares, and may add writability a forwarded CPI needs. A GLAM seat
/// is named as the handler names it, a fixed seat has the address the handler fixes, a
/// supplied seat derives as the handler's IDL derives it, a sentinel seat sits at an optional
/// account, and every optional account a client would fill with the native program's id is a
/// sentinel seat. A forwarded native account is named by both sides in their own vocabularies,
/// the native IDL's and the handler's, so its names are not held equal.
final class EmbeddedMappingsHandlerAccountsTests {

  /// The generated program trees, from the module directory the build runs tests in.
  private static final Path GEN_ROOT = Path.of("src/main/java/systems/glam/sdk/idl/programs/glam");

  /// One seed of a PDA as the IDL spells it: constant bytes, an account by its path, or an
  /// argument by its path.
  private record IdlSeed(String kind, byte[] value, String path) {
  }

  /// How the IDL derives an account: its seeds, and the program when it is not the handler's own.
  private record IdlPda(List<IdlSeed> seeds, IdlSeed program) {
  }

  /// One account a handler declares, flattened out of any group the IDL nests it in.
  private record IdlAccount(String name, boolean writable, boolean signer, boolean optional, PublicKey address, IdlPda pda) {
  }

  private record IdlInstruction(String name, String discriminator, List<IdlAccount> accounts) {
  }

  private record Idl(PublicKey address, Map<String, IdlInstruction> instructions) {
  }

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

  /// True for a path below the trees with a `next` element: the `next/gen` tree renders the vcs
  /// channel beside the client and is no client of the program the documents name.
  private static boolean isNextTree(final Path root, final Path file) {
    for (final var element : root.relativize(file)) {
      if (element.toString().equals("next")) {
        return true;
      }
    }
    return false;
  }

  private static String hex(final Discriminator discriminator) {
    final byte[] bytes = new byte[discriminator.length()];
    discriminator.write(bytes, 0);
    return HexFormat.of().formatHex(bytes);
  }

  /// Each program tree's `gen/idl.json`, by the address it declares, one tree per program.
  private static Map<PublicKey, Idl> carriedIdls() throws IOException {
    final var root = genRoot();
    final var idls = new HashMap<PublicKey, Idl>();
    try (final var files = Files.walk(root)) {
      for (final var file : files.filter(f -> f.getFileName().toString().equals("idl.json")).toList()) {
        if (!file.getParent().getFileName().toString().equals("gen") || isNextTree(root, file)) {
          continue;
        }
        final var parser = new IdlParser();
        JsonIterator.parse(Files.readAllBytes(file)).testObject(parser);
        final var idl = parser.idl();
        assertNotNull(idl.address(), file + " declares no address");
        final var previous = idls.put(idl.address(), idl);
        assertNull(previous, idl.address().toBase58() + " is the address of two generated trees, the second at " + file);
      }
    }
    assertFalse(idls.isEmpty(), "no gen/idl.json under " + root.toAbsolutePath());
    return idls;
  }

  @Test
  void everyMappedEntrySeatsTheAccountsOfAHandlerTheSdksClientDeclares() throws IOException {
    final var idls = carriedIdls();
    int checked = 0;
    for (final var env : GlamEnv.values()) {
      for (final var document : EmbeddedMappings.load(env)) {
        final var proxy = document.proxyProgramId().toBase58();
        final var idl = idls.get(document.proxyProgramId());
        assertNotNull(idl, env + " " + document.programId().toBase58() + ": the sdk carries no client of " + proxy);
        for (final var entry : document.instructions()) {
          if (!(entry instanceof InstructionEntry.Mapped mapped)) {
            continue;
          }
          ++checked;
          final var name = env + " " + document.programId().toBase58() + " " + mapped.name();
          final var behind = ": the document or the sdk's client of " + proxy
              + " is behind the program's redeploy; regenerate the client (idl-src-gen) or wait for the monorepo's public sync to bring the document.";
          final var handler = idl.instructions().get(hex(mapped.handler().discriminator()));
          assertNotNull(handler, name + " maps onto " + mapped.handler().name() + " (" + hex(mapped.handler().discriminator())
              + "), which the client declares no handler with" + behind);
          assertEquals(handler.name(), mapped.handler().name(), name + ": the handler's name" + behind);
          final var accounts = handler.accounts();
          final var seats = mapped.destinationAccounts();
          // every declared account, optional ones included: the programs build without Anchor's
          // allow-missing-optionals, so an absent optional still takes its slot as the program's id
          assertTrue(seats.size() >= accounts.size(), name + " seats " + seats.size() + " accounts for " + handler.name()
              + ", which the client declares with " + accounts.size() + behind);
          for (final var seat : seats) {
            if (seat.index() >= accounts.size()) {
              continue; // read from the remaining accounts: the IDL lists no counterpart
            }
            final var account = accounts.get(seat.index());
            final var at = name + " seat " + seat.index() + " (" + handler.name() + "'s " + account.name() + ")";
            if (account.writable()) {
              assertTrue(seat.writable(), at + " is read-only, but the handler writes it" + behind);
            }
            if (account.signer()) {
              assertTrue(seat.signer(), at + " does not sign, but the handler requires a signer" + behind);
            }
            switch (seat) {
              case DestinationAccount.Dynamic dynamic ->
                  assertEquals(account.name(), dynamic.name().jsonName(), at + " seats another GLAM account" + behind);
              case DestinationAccount.Static fixed -> {
                if (account.address() != null) {
                  assertEquals(account.address(), fixed.address(), at + " seats another address" + behind);
                }
              }
              case DestinationAccount.Supplied supplied -> {
                // the role is the caller's label; what the two sides share is the derivation, which
                // the document states exactly when the handler's IDL derives the account
                assertEquals(account.pda() != null, supplied.derivation() != null, at + (account.pda() == null
                    ? " states a derivation for an account the handler's IDL does not derive"
                    : " states no derivation for an account the handler's IDL derives") + behind);
                if (account.pda() != null) {
                  assertDerivesAsDeclared(at, document, accounts, account.pda(), supplied.derivation(), behind);
                }
              }
              case DestinationAccount.Source forwarded -> {
                final var position = mapped.sourceAccounts().get(forwarded.source());
                if (forwarded.sentinel()) {
                  assertTrue(account.optional(), at + " passes the proxy program's id for an absent native account, but the handler's account is not optional" + behind);
                }
                if (account.optional() && position.optional() == OptionalKind.PROGRAM_ID) {
                  assertTrue(forwarded.sentinel(), at + " forwards a native optional the client fills with the native program's id, which the handler would read as present; the seat needs a sentinel" + behind);
                }
                assertNotEquals(OptionalKind.OMITTED, position.optional(), at + " forwards a native position the client may leave out, which would leave the handler short of it" + behind);
              }
            }
          }
        }
      }
    }
    assertTrue(checked > 100, "only " + checked + " mapped entries checked; the embedded sets carry more than that");
  }

  /// The document derives the account as the handler's IDL does: under the handler's own program
  /// when the IDL names none, under the address it spells when it fixes one, under the address
  /// the handler fixes a named account at when it names one (the generator resolves it so, and
  /// refuses a handler that fixes none), and seed by seed, a constant by its bytes, an account by
  /// the name the handler gives the seat the document names, an argument by its path.
  private static void assertDerivesAsDeclared(final String at,
                                              final MappingDocument document,
                                              final List<IdlAccount> accounts,
                                              final IdlPda pda,
                                              final Derivation derivation,
                                              final String behind) {
    final var program = pda.program();
    if (program == null) {
      assertEquals(document.proxyProgramId(), derivation.program(), at + " derives under a program other than the handler's own" + behind);
    } else {
      switch (program.kind()) {
        case "const" ->
            assertEquals(PublicKey.createPubKey(program.value()), derivation.program(), at + " derives under a program other than the one the handler fixes" + behind);
        case "account" -> {
          final var named = accountNamed(accounts, program.path());
          if (named != null && named.address() != null) {
            assertEquals(named.address(), derivation.program(), at + " derives under a program other than the one the handler fixes " + program.path() + " at" + behind);
          }
        }
        default -> fail(at + ": the handler's IDL derives under a program of kind " + program.kind() + ", which this test does not read");
      }
    }
    final var seeds = derivation.seeds();
    assertEquals(pda.seeds().size(), seeds.size(), at + " derives from " + seeds.size() + " seeds, the handler from " + pda.seeds().size() + behind);
    for (int i = 0; i < seeds.size(); ++i) {
      final var declared = pda.seeds().get(i);
      final var seed = seeds.get(i);
      final var seedAt = at + " seed " + i;
      switch (declared.kind()) {
        case "const" -> {
          final var constant = assertInstanceOf(Derivation.Const.class, seed, seedAt + " is not the constant the handler derives from" + behind);
          assertArrayEquals(declared.value(), constant.value(), seedAt + " differs from the handler's constant" + behind);
        }
        case "account" -> {
          final var named = assertInstanceOf(Derivation.Account.class, seed, seedAt + " is not the account the handler derives from" + behind);
          if (!declared.path().contains(".") && named.index() < accounts.size()) {
            assertEquals(declared.path(), accounts.get(named.index()).name(), seedAt + " names another account than the handler's" + behind);
          }
        }
        case "arg" -> {
          final var argument = assertInstanceOf(Derivation.Arg.class, seed, seedAt + " is not the argument the handler derives from" + behind);
          assertEquals(declared.path(), argument.path(), seedAt + " names another argument than the handler's" + behind);
        }
        default -> fail(seedAt + ": the handler's IDL derives from a seed of kind " + declared.kind() + ", which this test does not read");
      }
    }
  }

  /// The handler's account of that name, or null when it declares none; a dotted path reaches into
  /// an account's data, which no handler here derives a program from.
  private static IdlAccount accountNamed(final List<IdlAccount> accounts, final String path) {
    for (final var account : accounts) {
      if (account.name().equals(path)) {
        return account;
      }
    }
    return null;
  }

  /// The IDL's address and instructions; every other field is skipped with its value.
  private static final class IdlParser implements FieldBufferPredicate {

    private PublicKey address;
    private final Map<String, IdlInstruction> instructions = new HashMap<>();

    Idl idl() {
      return new Idl(address, Map.copyOf(instructions));
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("address", buf, offset, len)) {
        address = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("instructions", buf, offset, len)) {
        while (ji.readArray()) {
          final var parser = new InstructionParser();
          ji.testObject(parser);
          final var instruction = parser.instruction();
          final var previous = instructions.put(instruction.discriminator(), instruction);
          assertNull(previous, "two instructions share the discriminator " + instruction.discriminator());
        }
      } else {
        ji.skip();
      }
      return true;
    }
  }

  private static byte[] readBytes(final JsonIterator ji) {
    final var bytes = new ArrayList<Byte>();
    while (ji.readArray()) {
      bytes.add((byte) ji.readInt());
    }
    final var array = new byte[bytes.size()];
    for (int i = 0; i < array.length; ++i) {
      array[i] = bytes.get(i);
    }
    return array;
  }

  private static final class InstructionParser implements FieldBufferPredicate {

    private String name;
    private byte[] discriminator;
    private final List<IdlAccount> accounts = new ArrayList<>();

    IdlInstruction instruction() {
      assertNotNull(name, "an instruction without a name");
      assertNotNull(discriminator, name + " has no discriminator");
      return new IdlInstruction(name, HexFormat.of().formatHex(discriminator), List.copyOf(accounts));
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("name", buf, offset, len)) {
        name = ji.readString();
      } else if (JsonIterator.fieldEquals("discriminator", buf, offset, len)) {
        discriminator = readBytes(ji);
      } else if (JsonIterator.fieldEquals("accounts", buf, offset, len)) {
        readAccounts(ji, accounts);
      } else {
        ji.skip();
      }
      return true;
    }
  }

  /// The accounts of an array in order, a nested group's members in its place.
  private static void readAccounts(final JsonIterator ji, final List<IdlAccount> into) {
    while (ji.readArray()) {
      final var parser = new AccountParser(into);
      ji.testObject(parser);
      parser.finish();
    }
  }

  private static final class AccountParser implements FieldBufferPredicate {

    private final List<IdlAccount> into;
    private String name;
    private boolean writable;
    private boolean signer;
    private boolean optional;
    private PublicKey address;
    private IdlPda pda;
    private boolean group;

    private AccountParser(final List<IdlAccount> into) {
      this.into = into;
    }

    void finish() {
      if (!group) {
        assertNotNull(name, "an account without a name");
        into.add(new IdlAccount(name, writable, signer, optional, address, pda));
      }
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("name", buf, offset, len)) {
        name = ji.readString();
      } else if (JsonIterator.fieldEquals("writable", buf, offset, len)) {
        writable = ji.readBoolean();
      } else if (JsonIterator.fieldEquals("signer", buf, offset, len)) {
        signer = ji.readBoolean();
      } else if (JsonIterator.fieldEquals("optional", buf, offset, len)) {
        optional = ji.readBoolean();
      } else if (JsonIterator.fieldEquals("address", buf, offset, len)) {
        address = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("pda", buf, offset, len)) {
        final var parser = new PdaParser();
        ji.testObject(parser);
        pda = parser.pda();
      } else if (JsonIterator.fieldEquals("accounts", buf, offset, len)) {
        group = true;
        readAccounts(ji, into);
      } else {
        ji.skip();
      }
      return true;
    }
  }

  private static final class PdaParser implements FieldBufferPredicate {

    private final List<IdlSeed> seeds = new ArrayList<>();
    private IdlSeed program;

    IdlPda pda() {
      return new IdlPda(List.copyOf(seeds), program);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("seeds", buf, offset, len)) {
        while (ji.readArray()) {
          final var parser = new SeedParser();
          ji.testObject(parser);
          seeds.add(parser.seed());
        }
      } else if (JsonIterator.fieldEquals("program", buf, offset, len)) {
        final var parser = new SeedParser();
        ji.testObject(parser);
        program = parser.seed();
      } else {
        ji.skip();
      }
      return true;
    }
  }

  private static final class SeedParser implements FieldBufferPredicate {

    private String kind;
    private byte[] value;
    private String path;

    IdlSeed seed() {
      assertNotNull(kind, "a seed without a kind");
      return new IdlSeed(kind, value, path);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("kind", buf, offset, len)) {
        kind = ji.readString();
      } else if (JsonIterator.fieldEquals("value", buf, offset, len)) {
        value = readBytes(ji);
      } else if (JsonIterator.fieldEquals("path", buf, offset, len)) {
        path = ji.readString();
      } else {
        ji.skip();
      }
      return true;
    }
  }
}

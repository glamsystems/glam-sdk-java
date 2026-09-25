package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.ix.proxy.MappingDocument;
import systems.glam.ix.proxy.MappingDocumentParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/// The ix-mapper mapping documents the sdk jar carries: one file per source program under
/// `glam/ix-mappings/production` and `glam/ix-mappings/staging`, the two sets the GLAM
/// monorepo generates from its managed IDL stores and publishes through
/// `glamsystems/ix-mapper-ts`, embedded at build time from the tracked `ix-mapper-ts/` directory
/// the monorepo's sync workflow writes. A consumer reads them through
/// [GlamAccounts#createMapper()] and supplies no directory.
public final class EmbeddedMappings {

  static final String RESOURCE_ROOT = "glam/ix-mappings/";
  static final String INDEX_RESOURCE = RESOURCE_ROOT + "index.json";

  /// What the build wrote beside the documents: where they came from (the pinned
  /// `glamsystems/ix-mapper-ts` commit, or the local root a build was pointed at) and each
  /// environment's file names.
  public record Index(String source, List<String> production, List<String> staging) {

    public List<String> files(final GlamEnv env) {
      return switch (env) {
        case PRODUCTION -> production;
        case STAGING -> staging;
      };
    }
  }

  public static Index index() {
    return parseIndex(readResource(INDEX_RESOURCE));
  }

  static Index parseIndex(final byte[] json) {
    return JsonIterator.parse(json).parseObject(new IndexParser());
  }

  /// The jar directory of an environment's set, as the build writes it: the environment's
  /// mapping name, which is also the environment the documents in it declare.
  static String directory(final GlamEnv env) {
    return RESOURCE_ROOT + environmentName(env) + '/';
  }

  static String environmentName(final GlamEnv env) {
    return env.mappingEnvironment();
  }

  /// Every document embedded for the environment. Each holds to it: a document that declares
  /// another environment, or whose proxy program is not one of the environment's GLAM
  /// programs, is refused by name, so a production set can never be loaded for a staging
  /// vault, or the other way round.
  public static List<MappingDocument> load(final GlamEnv env) {
    return load(env, glamPrograms(env.glamAccounts()));
  }

  /// The environment whose embedded set serves a protocol program. A program the sdk does not
  /// know is refused: the sets carry their environment's proxy ids, so serving staging's set
  /// for it, as reading everything but production as staging would, would send instructions
  /// to programs the caller never named.
  static GlamEnv environmentOf(final PublicKey protocolProgram) {
    return GlamEnv.ofProtocolProgram(protocolProgram).orElseThrow(() -> new IllegalArgumentException(
        "The sdk jar embeds mapping documents for production and staging only; " + protocolProgram.toBase58()
            + " is neither GLAM protocol program. Supply a mappings directory instead."
    ));
  }

  /// The programs a document of these accounts may proxy through: the protocol program and
  /// every integration program the accounts name.
  static Set<PublicKey> glamPrograms(final GlamAccounts glamAccounts) {
    final var programs = new HashSet<>(glamAccounts.integrationAuthorities().keySet());
    programs.add(glamAccounts.protocolProgram());
    return programs;
  }

  static List<MappingDocument> load(final GlamEnv env, final Set<PublicKey> glamPrograms) {
    return load(index(), env, glamPrograms, EmbeddedMappings::readResource);
  }

  /// The loader over any index and resource reader, so a test can hand it a set the jar does
  /// not carry and watch each refusal.
  static List<MappingDocument> load(final Index index,
                                    final GlamEnv env,
                                    final Set<PublicKey> glamPrograms,
                                    final Function<String, byte[]> resources) {
    final var files = index.files(env);
    if (files.isEmpty()) {
      throw new IllegalStateException("The sdk jar embeds no " + env + " mapping documents.");
    }
    final var directory = directory(env);
    final var environment = environmentName(env);
    final var documents = new ArrayList<MappingDocument>(files.size());
    for (final var file : files) {
      final var resource = directory + file;
      final var document = MappingDocumentParser.parse(resources.apply(resource), resource);
      if (!environment.equals(document.environment())) {
        throw new IllegalStateException(String.format(
            "%s declares environment %s, not %s.", resource, document.environment(), environment
        ));
      }
      final var proxy = document.proxyProgramId();
      if (!glamPrograms.contains(proxy)) {
        throw new IllegalStateException(String.format(
            "%s proxies through %s, which is not a %s GLAM program.", resource, proxy.toBase58(), env
        ));
      }
      documents.add(document);
    }
    return List.copyOf(documents);
  }

  static byte[] readResource(final String name) {
    try (final var in = EmbeddedMappings.class.getModule().getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("The sdk jar does not carry " + name + '.');
      }
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// Every field is required and a field the build does not write is refused, so an index
  /// another writer produced cannot load half a set silently.
  private static final class IndexParser implements FieldBufferPredicate, Supplier<Index> {

    private String source;
    private List<String> production;
    private List<String> staging;

    @Override
    public Index get() {
      if (source == null || production == null || staging == null) {
        throw new IllegalStateException(INDEX_RESOURCE + " must name source, production and staging.");
      }
      return new Index(source, List.copyOf(production), List.copyOf(staging));
    }

    private static List<String> fileNames(final JsonIterator ji, final String field) {
      final var names = ji.readList(JsonIterator::readString);
      for (final var name : names) {
        if (name == null || !name.endsWith(".json") || name.contains("/") || name.contains("\\")) {
          throw new IllegalStateException(INDEX_RESOURCE + ' ' + field + " names " + name + ", which is not a document file name.");
        }
      }
      return names;
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("source", buf, offset, len)) {
        source = ji.readString();
      } else if (JsonIterator.fieldEquals("production", buf, offset, len)) {
        production = fileNames(ji, "production");
      } else if (JsonIterator.fieldEquals("staging", buf, offset, len)) {
        staging = fileNames(ji, "staging");
      } else {
        throw new IllegalStateException(INDEX_RESOURCE + " declares an unknown field " + new String(buf, offset, len) + '.');
      }
      return true;
    }
  }

  private EmbeddedMappings() {
  }
}

package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.ix.proxy.IndexedAccountMeta;
import systems.glam.ix.proxy.ProgramMapConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/// The ix-mapper mapping configs the sdk jar carries: one file per source program under
/// `glam/ix-mappings/production` and `glam/ix-mappings/staging`, the two sets glamsystems/glam
/// generates from its managed IDL stores and projects into this repository, embedded at build
/// time beside an index, because a jar cannot be listed. A consumer reads them through
/// [GlamAccounts#createMapper(systems.glam.sdk.proxy.DynamicGlamAccountFactory)] and supplies
/// no directory.
public final class EmbeddedMappings {

  static final String RESOURCE_ROOT = "glam/ix-mappings/";
  static final String INDEX_RESOURCE = RESOURCE_ROOT + "index.json";

  /// What the build wrote beside the configs: the glam commit they were projected from, as the
  /// sync commit recorded it, and each environment's file names.
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

  /// The jar directory of an environment's set, as the build writes it: the lowercase name.
  static String directory(final GlamEnv env) {
    return RESOURCE_ROOT + env.name().toLowerCase(Locale.ROOT) + '/';
  }

  /// Every config embedded for the environment. Each holds to it: a config whose proxy program
  /// is not one of the environment's GLAM programs is refused by name, so a production set can
  /// never be loaded for a staging vault, or the other way round.
  public static List<ProgramMapConfig> load(final GlamEnv env) {
    return load(env, glamPrograms(env.glamAccounts()));
  }

  /// The environment whose embedded set serves a protocol program. A program the sdk does not
  /// know is refused: the sets carry their environment's proxy ids, so serving staging's set
  /// for it, as reading everything but production as staging would, would send instructions
  /// to programs the caller never named.
  static GlamEnv environmentOf(final PublicKey protocolProgram) {
    return GlamEnv.ofProtocolProgram(protocolProgram).orElseThrow(() -> new IllegalArgumentException(
        "The sdk jar embeds mapping sets for production and staging only; " + protocolProgram.toBase58()
            + " is neither GLAM protocol program. Supply a mappings directory instead."
    ));
  }

  /// The programs a config of these accounts may proxy through: the protocol program and every
  /// integration program the accounts name.
  static Set<PublicKey> glamPrograms(final GlamAccounts glamAccounts) {
    final var programs = new HashSet<>(glamAccounts.integrationAuthorities().keySet());
    programs.add(glamAccounts.protocolProgram());
    return programs;
  }

  static List<ProgramMapConfig> load(final GlamEnv env, final Set<PublicKey> glamPrograms) {
    return load(index(), env, glamPrograms, EmbeddedMappings::readResource);
  }

  /// The loader over any index and resource reader, so a test can hand it a set the jar does
  /// not carry and watch each refusal.
  static List<ProgramMapConfig> load(final Index index,
                                     final GlamEnv env,
                                     final Set<PublicKey> glamPrograms,
                                     final Function<String, byte[]> resources) {
    final var files = index.files(env);
    if (files.isEmpty()) {
      throw new IllegalStateException("The sdk jar embeds no " + env + " mapping configs.");
    }
    final var directory = directory(env);
    final var accountMetaCache = new HashMap<AccountMeta, AccountMeta>(256);
    final var indexedAccountMetaCache = new HashMap<IndexedAccountMeta, IndexedAccountMeta>(256);
    final var configs = new ArrayList<ProgramMapConfig>(files.size());
    for (final var file : files) {
      final var resource = directory + file;
      final var config = ProgramMapConfig.parseConfig(
          accountMetaCache, indexedAccountMetaCache, JsonIterator.parse(resources.apply(resource))
      );
      final var proxy = config.invokedProxyProgram();
      if (proxy == null) {
        throw new IllegalStateException(resource + " names no proxy program.");
      }
      if (!glamPrograms.contains(proxy.publicKey())) {
        throw new IllegalStateException(String.format(
            "%s proxies through %s, which is not a %s GLAM program.", resource, proxy.publicKey().toBase58(), env
        ));
      }
      configs.add(config);
    }
    return List.copyOf(configs);
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
          throw new IllegalStateException(INDEX_RESOURCE + ' ' + field + " names " + name + ", which is not a config file name.");
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

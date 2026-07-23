package systems.glam.services.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import software.sava.idl.clients.marginfi.v2.MarginfiAccounts;
import software.sava.idl.clients.meteora.MeteoraAccounts;
import software.sava.idl.clients.orca.OrcaAccounts;
import software.sava.idl.clients.phoenix.PhoenixAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.core.remote.call.Backoff;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.ServiceContextImpl;
import systems.glam.services.integrations.kamino.KaminoCache;
import systems.glam.services.mints.AssetMetaContext;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.mints.StakePoolCache;
import systems.glam.services.mints.StakePoolContext;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.GlobalConfigCache;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class IntegrationServiceContextTests {

  private static final PublicKey SERVICE_KEY = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;

  @SuppressWarnings("unchecked")
  private static <T> T stub(final Class<T> type, final InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
  }

  private static AccountInfo<byte[]> account(final PublicKey key, final PublicKey owner, final byte[] data) {
    return new AccountInfo<>(key, new Context(1L, null), false, 0, owner, BigInteger.ZERO, 0, data);
  }

  @Test
  void everyAccessorAndDelegationRoutesToItsCollaborator(@TempDir final Path tempDir) {
    final var serviceContext = new ServiceContextImpl(
        SERVICE_KEY, BigInteger.ONE, BigInteger.ONE, tempDir,
        Duration.ofSeconds(1), Duration.ofSeconds(2),
        Executors.newVirtualThreadPerTaskExecutor(),
        Backoff.single(TimeUnit.MILLISECONDS, 1),
        SOLANA, GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING,
        null, null, null
    );

    final var mintKey = fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var cachedMint = MintContext.createContext(SOLANA, mintKey, 9, SOLANA.tokenProgram());
    final var setMints = new java.util.ArrayList<MintContext>();
    final var mintCache = stub(MintCache.class, (proxy, method, args) -> switch (method.getName()) {
      case "get" -> {
        assertEquals(mintKey, args[0]);
        yield cachedMint;
      }
      case "setGet" -> {
        setMints.add((MintContext) args[0]);
        yield cachedMint;
      }
      default -> throw new UnsupportedOperationException(method.getName());
    });

    final var stakePoolContext = new StakePoolContext(null, null, null);
    final var stakePoolCache = stub(StakePoolCache.class, (proxy, method, args) -> {
      if (method.getName().equals("get")) {
        assertEquals(mintKey, args[0]);
        return stakePoolContext;
      }
      throw new UnsupportedOperationException(method.getName());
    });

    final var solAssetMeta = stub(AssetMetaContext.class, (proxy, method, args) -> {
      throw new UnsupportedOperationException(method.getName());
    });
    final var mintAssetMeta = stub(AssetMetaContext.class, (proxy, method, args) -> {
      throw new UnsupportedOperationException(method.getName());
    });
    final var globalConfigCache = stub(GlobalConfigCache.class, (proxy, method, args) -> switch (method.getName()) {
      case "solAssetMeta" -> solAssetMeta;
      case "topPriorityForMintChecked" -> {
        assertEquals(mintKey, args[0]);
        yield mintAssetMeta;
      }
      default -> throw new UnsupportedOperationException(method.getName());
    });

    final var integTableCache = stub(IntegLookupTableCache.class, (proxy, method, args) -> {
      throw new UnsupportedOperationException(method.getName());
    });

    final var queued = new java.util.ArrayList<Object>();
    final var accountFetcher = stub(AccountFetcher.class, (proxy, method, args) -> {
      if (method.getName().equals("queueUnique")) {
        queued.add(args[0]);
        queued.add(args[1]);
        return null;
      }
      throw new UnsupportedOperationException(method.getName());
    });

    final var oracleKey = fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");
    final var feedIndexes = new FeedIndexes(null, null, new short[0], BigInteger.ZERO);
    final var kaminoCache = stub(KaminoCache.class, (proxy, method, args) -> {
      if (method.getName().equals("indexes")) {
        assertEquals(mintKey, args[0]);
        assertEquals(oracleKey, args[1]);
        assertEquals(OracleType.SwitchboardOnDemand, args[2]);
        return feedIndexes;
      }
      throw new UnsupportedOperationException(method.getName());
    });

    final var context = new IntegrationServiceContextImpl(
        serviceContext,
        mintCache, stakePoolCache, globalConfigCache, integTableCache, accountFetcher,
        KaminoAccounts.MAIN_NET, kaminoCache,
        LoopscaleAccounts.MAIN_NET, OrcaAccounts.MAIN_NET, PhoenixAccounts.MAIN_NET,
        MarginfiAccounts.MAIN_NET, JupiterAccounts.MAIN_NET, MeteoraAccounts.MAIN_NET
    );

    assertSame(serviceContext, context.serviceContext());
    assertSame(accountFetcher, context.accountFetcher());
    assertSame(globalConfigCache, context.globalConfigCache());
    assertSame(integTableCache, context.integTableCache());
    assertSame(kaminoCache, context.kaminoCache());

    assertSame(stakePoolContext, context.stakePoolContextForMint(mintKey));
    assertSame(cachedMint, context.mintContext(mintKey));
    assertSame(solAssetMeta, context.solAssetMeta());
    assertSame(mintAssetMeta, context.globalConfigAssetMeta(mintKey));
    assertSame(feedIndexes, context.scopeAggregateIndexes(mintKey, oracleKey, OracleType.SwitchboardOnDemand));

    // setMintContext(MintContext) hands the given context to the cache
    assertSame(cachedMint, context.setMintContext(cachedMint));
    assertSame(cachedMint, setMints.getFirst());

    // setMintContext(AccountInfo) parses the mint before caching it
    final byte[] mintData = new byte[software.sava.core.accounts.token.Mint.BYTES];
    assertSame(cachedMint, context.setMintContext(account(mintKey, SOLANA.tokenProgram(), mintData)));
    final var parsed = setMints.get(1);
    assertEquals(mintKey, parsed.mint());
    assertEquals(SOLANA.readTokenProgram(), parsed.readTokenProgram());

    final var accounts = List.of(mintKey, oracleKey);
    context.queueUnique(accounts, null);
    assertSame(accounts, queued.getFirst());
    assertNull(queued.get(1));

    assertSame(KaminoAccounts.MAIN_NET, context.kaminoAccounts());
    assertEquals(KaminoAccounts.MAIN_NET.kLendProgram(), context.kLendProgram());
    assertEquals(KaminoAccounts.MAIN_NET.kVaultsProgram(), context.kVaultsProgram());
    assertSame(LoopscaleAccounts.MAIN_NET, context.loopscaleAccounts());
    assertEquals(LoopscaleAccounts.MAIN_NET.loopscaleProgram(), context.loopscaleProgram());
    assertSame(OrcaAccounts.MAIN_NET, context.orcaAccounts());
    assertEquals(OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram().publicKey(), context.orcaWhirlpoolProgram());
    assertSame(PhoenixAccounts.MAIN_NET, context.phoenixAccounts());
    assertEquals(PhoenixAccounts.MAIN_NET.invokedEternalProgram().publicKey(), context.phoenixEternalProgram());
    assertEquals(PhoenixAccounts.MAIN_NET.invokedEmberProgram().publicKey(), context.phoenixEmberProgram());
    assertSame(MarginfiAccounts.MAIN_NET, context.marginfiAccounts());
    assertSame(JupiterAccounts.MAIN_NET, context.jupiterAccounts());
    assertSame(MeteoraAccounts.MAIN_NET, context.meteoraAccounts());
  }
}

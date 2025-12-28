package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.core.config.RemoteResourceConfig;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.solana.alt.TableCacheConfig;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.epoch.EpochServiceConfig;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.HeliusFeeProvider;
import software.sava.services.solana.transactions.TxMonitorConfig;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

public interface DelegateServiceConfig {

  static DelegateServiceConfig loadConfig(final Class<?> serviceClass,
                                          final ExecutorService taskExecutor,
                                          final HttpClient rpcHttpClient) {

    return ServiceConfigUtil.loadConfig(serviceClass, new BaseDelegateServiceConfig.ConfigParser(taskExecutor, rpcHttpClient));
  }

  PublicKey glamStateKey();

  SigningServiceConfig signingServiceConfig();

  SolanaAccounts solanaAccounts();

  ChainItemFormatter formatter();

  NotifyClient notifyClient();

  TableCacheConfig tableCacheConfig();

  RpcCaller rpcCaller();

  LoadBalancer<SolanaRpcClient> sendClients();

  LoadBalancer<HeliusFeeProvider> feeProviders();

  RemoteResourceConfig websocketConfig();

  EpochServiceConfig epochServiceConfig();

  TxMonitorConfig txMonitorConfig();

  BigDecimal maxLamportPriorityFee();

  BigInteger warnFeePayerBalance();

  BigInteger minFeePayerBalance();

  Duration minCheckStateDelay();

  Duration maxCheckStateDelay();

  Backoff serviceBackoff();
}

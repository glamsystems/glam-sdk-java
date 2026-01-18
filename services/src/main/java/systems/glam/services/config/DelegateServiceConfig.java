package systems.glam.services.config;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.core.config.RemoteResourceConfig;
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
import java.nio.file.Path;
import java.time.Duration;

public interface DelegateServiceConfig {

  PublicKey glamStateKey();

  SigningServiceConfig signingServiceConfig();

  SolanaAccounts solanaAccounts();

  ChainItemFormatter formatter();

  NotifyClient notifyClient();

  Path glamStateAccountCacheDirectory();

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

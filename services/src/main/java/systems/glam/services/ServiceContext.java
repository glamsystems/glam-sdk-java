package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;

import java.nio.file.Path;
import java.util.Map;

public interface ServiceContext {

  Path accountsCacheDirectory();

  PublicKey clockSysVar();

  PublicKey tokenProgram();

  PublicKey token2022Program();

  boolean isTokenMint(final AccountInfo<byte[]> accountInfo);

  boolean isTokenAccount(final AccountInfo<byte[]> accountInfo);

  PublicKey serviceKey();

  boolean feePayerBalanceLow();

  PublicKey glamMintProgram();

  void executeTask(final Runnable task);

  void backoff(final long failureCount) throws InterruptedException;

  long minCheckStateDelayNanos();

  long maxCheckStateDelayNanos();

  SolanaAccounts solanaAccounts();

  GlamAccounts glamAccounts();

  NotifyClient notifyClient();

  RpcCaller rpcCaller();

  Path cacheDirectory();

  Path resolveGlamStateFilePath(final PublicKey glamStateKey);

  Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap);
}

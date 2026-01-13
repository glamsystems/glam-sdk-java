package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;

import java.util.List;

public interface ServiceContext {

  PublicKey clockSysVar();

  PublicKey tokenProgram();

  PublicKey token2022Program();

  boolean isTokenAccount(AccountInfo<byte[]> accountInfo);

  PublicKey glamMintProgram();

  long medianMillisPerSlot();

  void backoff(long failureCount) throws InterruptedException;

  boolean processInstructions(String logContext,
                              List<Instruction> instructions) throws InterruptedException;

  boolean feePayerBalanceLow();

  PublicKey serviceKey();

  long minCheckStateDelayNanos();

  long maxCheckStateDelayNanos();

  SolanaAccounts solanaAccounts();

  GlamAccounts glamAccounts();

  NotifyClient notifyClient();

  RpcCaller rpcCaller();
}

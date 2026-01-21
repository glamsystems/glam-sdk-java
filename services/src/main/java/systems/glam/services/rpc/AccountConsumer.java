package systems.glam.services.rpc;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.List;
import java.util.Map;

public interface AccountConsumer {

  void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap);
}

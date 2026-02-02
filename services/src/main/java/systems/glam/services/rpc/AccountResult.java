package systems.glam.services.rpc;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.List;
import java.util.Map;

public record AccountResult(List<AccountInfo<byte[]>> accounts, Map<PublicKey, AccountInfo<byte[]>> accountMap) {

}

package systems.glam.services.pricing;

import software.sava.rpc.json.http.response.AccountInfo;

import java.math.BigDecimal;

public interface PriceService {


  BigDecimal price(final AccountInfo<byte[]> accountInfo);
}

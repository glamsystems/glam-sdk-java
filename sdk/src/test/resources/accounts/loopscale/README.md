# Loopscale strategy fixture

`13RqwWva17oKpqUwyvrRnaXSth2uzD61nWSYA7rXefEv` is a mainnet Loopscale `Strategy`
account (program `1oopBoJG58DgkUVKkEzKgyG9dvRmpgeEm1AVjoHkF78`), fetched on
2026-09-26 at slot 450712593 via `getAccountInfo` (base64) from
`https://api.mainnet-beta.solana.com`, chosen from a `getProgramAccounts` listing of
strategies with a stored market, and stored as gzipped raw account data (8460 bytes,
discriminator `[174, 110, 39, 119, 82, 106, 169, 102]`). It stores market
`3iT9TYXjv3FFbHu4ba5zujnesRYgzyy8YPq9kQNRZ25p` at offset 268, principal mint JUP
(`JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN`) and lender
`CvUBofNWKvcUc7xwzoWUJiMGiuJt2Ypq43dh2NiiP3EY`.

`LoopscaleStrategyMarketResolverTests` reads the stored market from it, and the
mapping tests supply that market to a mapped `update_strategy` that names none. To
refresh: fetch the same key, verify the length still matches the generated
`Strategy.BYTES`, re-gzip, and re-check the market the tests name.

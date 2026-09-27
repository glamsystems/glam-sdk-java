# Staging Jupiter swap, accepted in a mainnet simulation at slot 451027533

A swap the SDK builds with `GlamJupiterProgramClient.swap(JupiterSwapContext)` for the staging
deployment (`GlamAccounts.MAIN_NET_STAGING`), run against mainnet-beta with `simulateTransaction`
(`sigVerify: false`, `replaceRecentBlockhash: true`): nothing was signed or sent. It covers the two
shapes GLAM-1537 left unvalidated on staging: a wrapped-SOL input, and roles priced by Kamino
reserves, whose `refresh_reserves_batch` leads the transaction.

`StagingSwapSimulationTests` rebuilds `accepted-instructions.json` from the recorded inputs here,
offline. The oracle for that list is the deployed staging program accepting it, not the SDK.

## The run

| | |
| --- | --- |
| Simulated | 2026-09-27T14:44:55Z, slot 451027533 (`simulation.json`, verbatim) |
| Accounts read | slot 451027531, `api.mainnet-beta.solana.com`, confirmed |
| Staging protocol | `gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz`, deployed at slot 446999833, program data sha256 `a04ceae2…c09a4` (as `staging/protocol/gen/sources.json` records) |
| Vault | "GLAM Actions Test": state `HuRA6CuTcLaWB9adGJ9aLG67sC4fqp4SMXYvDWmEhd83`, vault `HeL5m28kD42iRcmxFWCdYqkMbAzFRWyukidwNNZGBEt` |
| Signer | the vault's owner `gLJHKPrZLGBiBZ33hFgZh6YnsEhTVxuRT17UCqNp6ff`, also the fee payer |
| Swap | 0.05 SOL (`50000000`) to USDC, quoted `6096091`, `skip_quote_price_check` false |
| Route | Jupiter `https://api.jup.ag/swap/v1`: `quote` with `slippageBps=30`, `maxAccounts=30`, `restrictIntermediateTokens=true`; `swap-instructions` with `userPublicKey` = the vault, `wrapAndUnwrapSol=false`, `useSharedAccounts=true`, `skipUserAccountsRpcCalls=true` (`quote.json`, `swap-instructions.json`, verbatim). One lookup table, `3Gh6vbi6wKLTnx93XzEb1nNtARB5cpBWZhHCgxY1tmxG`. |
| Oracles | chosen from the GlobalConfig snapshot by `RegisteredOracles`: wSOL and USDC each have a PythPull and a KaminoReserve registration at priority 0, and the tie goes to the reserve. Input and SOL/USD: reserve `d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q`; output: reserve `D6q6wuQSrifJKZYpR1M8R4YawnLDtDsMmWM1NbBmgJ59`. |

The simulated transaction was a v0 message of `SetComputeUnitLimit(1400000)` followed by the six
instructions in `accepted-instructions.json` (1118 bytes). The compute-budget instruction is not
SDK output and is not in the list. The simulation consumed 196902 compute units with `err: null`,
and its logs show each part of the path running:

```
Program log: Instruction: RefreshReservesBatch
Program log: Token: SOL Price: 121.8658
Program log: Token: USDC Price: 0.9999
Program log: Instruction: SystemTransfer
Program log: Instruction: JupiterSwapV2
Program log: Slippage bps: 30, max slippage bps allowed: 50
Program log: Oracle price deviation check: output_scaled=60955110000, min_required=60628215600  (max_deviation_bps=50)
Program log: Instruction: SharedAccountsRoute
Program log: Actual slippage: 0 bps (max allowed: 30 bps)
```

The deviation line is the priced path: an owner swap with the skip flag set would skip it.

## Files

- `accepted-instructions.json`: the six instructions `swap` returned, in Jupiter's instruction JSON
  shape, so `JupiterSwapInstructions.parseInstructionsList` reads it back.
- `quote.json`, `swap-instructions.json`: the Jupiter responses, verbatim.
- `simulation.json`: the `simulateTransaction` response, verbatim.
- `6avract7PxKqoq6hdmpAgGKgJWoJWdiXPPzzFZ62Hck6.dat.gz`: the GlobalConfig at slot 451027531 (30
  asset metas, 17 of them KaminoReserve). This is not the January 2026 snapshot under
  `accounts/glam/`, which the mapping tests pin.
- `d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q.dat.gz`, `D6q6wuQSrifJKZYpR1M8R4YawnLDtDsMmWM1NbBmgJ59.dat.gz`:
  the two Kamino reserves the refresh was built from, at slot 451027531. The July snapshot of the
  first under `accounts/kamino/` is a separate fixture.
- `HuRA6CuTcLaWB9adGJ9aLG67sC4fqp4SMXYvDWmEhd83.dat.gz`: the vault's state account at slot 451027531.

## What this does not show

- Acceptance is for the program deployed at slot 446999833, which predates the `ReserveStale`
  check (`52008`) in the source: the deployed IDL has no such error. A redeploy from current source
  also requires the reserve's `LastUpdate` to be fresh. The refresh leads the transaction for that
  reason, but this run does not exercise that check.
- The SOL/USD role is not read for a wSOL to USDC swap, since both sides are priced in USD. A
  KaminoReserve in that role needs a pair priced in different denominations, such as an LST to USDC.
- `sigVerify: false` does not check signatures. The test checks the signer set instead: the owner
  signs, and the vault is never a signer.
- A simulation is not a landed transaction: fees, account-lock contention and blockhash expiry are
  outside it.

## Re-recording

After a staging redeploy (the scheduled IDL monitor reports a new `lastDeploySlot`), record a new
set in a new directory named for its simulation slot, and point the test at it. In one uninterrupted
run, since the quote and the Kamino prices age within seconds to minutes: read the GlobalConfig,
state and reserves; choose the oracles with `RegisteredOracles`; request the Jupiter quote and
`swap-instructions` as above; build with `swap`; simulate a v0 transaction of
`SetComputeUnitLimit` plus that list with Jupiter's lookup tables, `sigVerify: false` and
`replaceRecentBlockhash: true`; and store the same files. The account reads and the simulation are
read-only; nothing needs a key.

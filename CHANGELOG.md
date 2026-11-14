# Changelog

## [25.26.0](https://github.com/glamsystems/glam-sdk-java/compare/25.25.0...25.26.0) (2026-11-14)


### ⚠ BREAKING CHANGES

* **services:** behaviour only, no signature changes. `createWebSocketPrototype`, and so the manager `createWebSocketManager` answers, no longer batches subscribe requests at an endpoint whose host is not api.mainnet.solana.com or api.mainnet-beta.solana.com: the builder's cap is 0 there, whatever `websocketSubscribeBatchBytes` names. A deployment that batched at another endpoint that reads arrays, a private Agave node for one, sends one request per message, each counted against the endpoint's single-subscribe budget where it has one, until its host is added to `WebSocketBatching.HOSTS_THAT_READ_A_BATCH`.

### Bug Fixes

* **services:** batch websocket subscribes only at an endpoint known to read a batch ([70b1839](https://github.com/glamsystems/glam-sdk-java/commit/70b183903c584d86c4c5e08a1c32e88d1a9544f4))

## [25.25.0](https://github.com/glamsystems/glam-sdk-java/compare/25.24.0...25.25.0) (2026-11-11)


### ⚠ BREAKING CHANGES

* **services:** `BaseDelegateServiceConfig` has two new record components after `websocketConfig`, `websocketSubscribeBatchBytes` and `websocketSingleSubscribeBudget`, so its canonical constructor changes, and `DelegateServiceConfig` has three new abstract methods, the two accessors and `createWebSocketPrototype(WebSocket.Builder)`, which an implementation outside this repository must add.
* **deps:** the sdk and services modules require idl-clients and sava-rpc transitively, so their breaking changes reach this SDK's callers. From idl-clients 25.20.0: the deprecated Jupiter Metis v1 and Ultra REST client is gone (`JupiterSwapApiClient`, `JupiterQuoteRequest`, `JupiterSwapRequest`, `JupiterUltraOrderRequest`, `JupiterUltraOrderRequestRecord`, `PriorityFeeLamports`, `JitoTip`); offerbook's `extendLoan` and `extendLoanKeys` take the collateral accounts, `extendLoanKeys` the invoked program as well, and its `Config` and `User` records gain components; and eight sealed or enum types gain variants, which an exhaustive switch must handle: Jupiter Swap's `Swap` and `CandidateSwap`, offerbook's `OfferbookError`, `OfferbookEvent` and `UpdateConfigAction`, Kamino Scope's `OracleType` and `OracleEntry`, and the neutral-trade `NtbundleEvent`. From sava-rpc 25.13.4: an implementation of `SolanaRpcWebsocket` or of its `Builder` must add `registerAll` and the `subscribeBatchBytes` and `singleSubscribeBudget` pairs.

### Features

* **services:** batch websocket subscribe requests ([48ad43a](https://github.com/glamsystems/glam-sdk-java/commit/48ad43ae6a600ba926dfe84e09b78751a9d9476f))


### Bug Fixes

* **build:** upgrade to Gradle 9.9 and scope plugin repositories ([ff2b0c3](https://github.com/glamsystems/glam-sdk-java/commit/ff2b0c3db7ab9dd8062701aac9c02129af29a63e))
* **services:** register the fulfillment subscriber before the first websocket connection ([ea78461](https://github.com/glamsystems/glam-sdk-java/commit/ea784618ceb6cd27e3ae3a794e5be20949a135ef))


### Build System

* **deps:** adopt solana-version-catalog 25.30.59 ([9608f2d](https://github.com/glamsystems/glam-sdk-java/commit/9608f2dca4710cc2ea026a364f4640582b3dc43d))

## [25.24.0](https://github.com/glamsystems/glam-sdk-java/compare/25.23.0...25.24.0) (2026-11-05)


### ⚠ BREAKING CHANGES

* **sdk:** in the exported staging bridge package `ExtBridgePDAs.bridgeSessionPDA` takes a `PublicKey argsTransferId` where it took `byte[]`. A caller that passed an empty vector or string to `StateModel`, `MintModel`, `MintPolicy` or `JupiterSwapPolicy` to mean `None` must pass `null`, since an empty value is now sent as present; and an empty array given for their sized fixed arrays (`StateModel.name` and `portfolioManagerName`, `MintModel.name`) or for the `clientOrderId` of the staging Phoenix `MultipleOrderPacket` and `MultipleOrderPacketV2` is refused where it encoded `None`. The programs read a present empty list as a list: an empty `MintModel.allowlist` now installs an allowlist that admits no signer, and an empty `StateModel.assets` or `borrowable` replaces the vault's list with an empty one, where each was dropped as `None` before. Through the idl-clients bundle and spl modules the sdk requires transitively, at 25.19.14 or later since an older commons disagrees with the generated code on an empty value, idl-clients 25.19.14's own breaking changes reach callers: its generated builders and records send an empty vector, byte string or string as present (Squads v4 memos, Marginfi V2, Metaplex Token Metadata, Jupiter Offerbook, Loopscale) and refuse an empty array given for a fixed-size optional array (Marginfi V2, Switchboard On-Demand, Phoenix Perpetuals); `denylistAccountPDA`, `tokenPairPDA`, `oraclePDA` and `queuePDA` take a `PublicKey`, and `remoteTokenMessenger1PDA`, `tokenPair1PDA`, `whirlpool1PDA` and `feed1PDA` a `byte[]`; Metaplex's eleven lock, unlock, use and delegate argument variants implement `OptionalSerdeEnum`, so code compiled against an earlier release must be recompiled; and `SIZE_FILTER` is withdrawn from 45 account records whose programs allocate another length (Marginfi `Bank`, Kamino `Obligation`, `FarmState` and `GlobalConfig` among them), which its changelog files as a fix.

### Bug Fixes

* **sdk:** regenerate the clients with idl-src-gen 4659e2e7 and adopt idl-clients 25.19.14 ([db1bfe6](https://github.com/glamsystems/glam-sdk-java/commit/db1bfe65981ec852f06c596ff09189e4b8f11ab4))

## [25.23.0](https://github.com/glamsystems/glam-sdk-java/compare/25.22.0...25.23.0) (2026-11-02)


### ⚠ BREAKING CHANGES

* **services:** InstructionProcessor.processInstructions ends a batch on ravina's sealed PublicationException: a verdict whose mayStillLand() is true, and a stopped monitor's, are thrown as themselves; every other, a send that never left included, drops the batch and returns false. The ravina 25.7.0 verdicts and their accessors are gone.
* **sdk:** retire the staging CCTP integration
* **sdk:** In the exported staging bridge packages (`systems.glam.sdk.idl.programs.glam.staging.bridge.gen` and `.types`) the types `CctpDestination`, `CctpPolicy` and `LayerzeroOftRoute` and the builders `setCctpPolicy`, `addLayerzeroOftRoute`, `updateLayerzeroOftRoute` and `deleteLayerzeroOftRoute` are gone, replaced by `BridgeRoutes` with its route types and the builders `initBridgeRoutes`, `addBridgeRoute`, `updateBridgeRoute`, `removeBridgeRoute`, `pauseBridgeRoutes`, `unpauseBridgeRoutes` and `closeBridgeRoutes`; `cctpDepositForBurn`, `managedCctpDepositForBurn` and `prepareOftTransfer` take a `bridgeRoutesKey`, and `priceManagedTransfers` a `solUsdOracleKey`; `ExtBridgePDAs.bridgeRegistryPDA` derives under the seed `bridge-registry-v2`, beside the new `bridgeRoutesPDA` helpers; `BridgeRegistry` is 3856 bytes holding 14 transfers (was 3760 and 16), `BridgeManagedTransfer` is 272 bytes with a u64 `destinationChain` ahead of `protocol` and a `padding` field (was 232 and u16), `BridgeSession` has a new layout carrying `TransferTerms`, and `ExtBridgeError` gains ten records (6026 to 6035) that an exhaustive switch must add. `priceBridgeManagedTransfers(PublicKey)` on `GlamAccountClient` and its implementations is now `priceBridgeManagedTransfers(PublicKey solUSDOracleKey, PublicKey baseAssetUsdOracleKey)`.

### Features

* **sdk:** retire the staging CCTP integration ([d2beff4](https://github.com/glamsystems/glam-sdk-java/commit/d2beff456ccd9e04c8c9984e138fe286344d1d8e))
* **services:** adopt ravina 25.8.0, end a batch on its one publication verdict, and drop a send that never left ([cb5ff50](https://github.com/glamsystems/glam-sdk-java/commit/cb5ff5039dcac7ea708a90a8152f8fcd9e192518))
* **services:** page every startup refusal, and keep one page policy and one delivery bound for the module ([f6dd1ca](https://github.com/glamsystems/glam-sdk-java/commit/f6dd1ca446148fce76c1f59de718c1c615f2a201))


### Bug Fixes

* **sdk:** regenerate bridge program types from updated staging IDL ([4a578f4](https://github.com/glamsystems/glam-sdk-java/commit/4a578f423e1763ad6b40196143348f3fd401efcf))
* **services:** back off and rebuild a batch no send of which was accepted, and page the cause ([9033cd1](https://github.com/glamsystems/glam-sdk-java/commit/9033cd11be0edf3e054f636cef5b9ac3ad8e5fe1))
* **services:** end the call on a stopped transaction monitor with a page that says whether its transaction may still land ([d3cc8cf](https://github.com/glamsystems/glam-sdk-java/commit/d3cc8cf673ccdb4e41dbaa457f02b896b0527f8f))
* **services:** end the process without waiting on its loops, and page the fulfillment loop's end ([7d4eb62](https://github.com/glamsystems/glam-sdk-java/commit/7d4eb6254ac9dbd5ee3d18659ac0728d06a7c49f))
* **services:** exit the fulfillment process when its worker ends ([28d084b](https://github.com/glamsystems/glam-sdk-java/commit/28d084b4cb58545a68c3cfe0732fe3d66e596727))
* **services:** keep the stopped-monitor exception when its page cannot be posted, and state its contract where callers read it ([1786e3b](https://github.com/glamsystems/glam-sdk-java/commit/1786e3b9ed52c45bf6c60dcc216d9aa303dc4260))
* **services:** page a bounded summary of a refused send, guard every post, and wait best effort ([5bd0376](https://github.com/glamsystems/glam-sdk-java/commit/5bd0376a46add90f5ee2114c8de8cc045cf00bf8))

## [25.22.0](https://github.com/glamsystems/glam-sdk-java/compare/25.21.0...25.22.0) (2026-10-16)


### ⚠ BREAKING CHANGES

* **idl:** the staging mint ObservationState is a plain type; its account readers, filters, discriminator and FACTORY are gone, and its remaining read(byte[], int) and offsets address the plain layout.

### Bug Fixes

* **idl:** sync idls/src ([5a1a46b](https://github.com/glamsystems/glam-sdk-java/commit/5a1a46b05748c20e5d846b8397c282342a53d968))

## [25.21.0](https://github.com/glamsystems/glam-sdk-java/compare/25.20.0...25.21.0) (2026-10-10)


### ⚠ BREAKING CHANGES

* **idl:** the staging GlamProtocolProgram no longer builds stake_authorize, which the deployed staging protocol has removed.

### Bug Fixes

* **idl:** regenerate the staging protocol client from its corrected metadata IDL ([ac6cd24](https://github.com/glamsystems/glam-sdk-java/commit/ac6cd24895cfa98e99bec29e66c3e0a3625935a3))
* **services:** bound every count MinGlamStateAccount walks, on update and in nested sections ([0298e91](https://github.com/glamsystems/glam-sdk-java/commit/0298e91082125a6702e708c1bed76a5aefa34816))
* **services:** hold awaitChange's minimum-delay floor for a wait shorter than the ceiling ([10b74a4](https://github.com/glamsystems/glam-sdk-java/commit/10b74a4cf3d5d43f79f51f2af128b40357099859))

## [25.20.0](https://github.com/glamsystems/glam-sdk-java/compare/25.19.0...25.20.0) (2026-09-29)


### ⚠ BREAKING CHANGES

* **sdk:** on staging, priceLoopscaleLoans(PublicKey, PublicKey), priceLoopscaleStrategies(PublicKey, PublicKey), priceLoopscaleVaultPositions(PublicKey, PublicKey, int), priceOrcaWhirlpoolPositions(PublicKey, PublicKey, int), priceMarginfiAccounts(PublicKey, PublicKey), pricePhoenixTraders(PublicKey, PublicKey) and priceRegisteredPositions() keep their signatures but build the ext program's instruction: no fee-payer signer, no PricedProtocolRecord event, and the vault's integration ACL must hold the ext program or glam_protocol refuses the write (UnauthorizedIntegrationProgram, 48001). Their cpiEmitEvents overloads are removed, as are priceRegisteredPositions(boolean) and priceRegisteredPositions(PublicKey, boolean); priceRegisteredPositions(PublicKey) builds the ext instruction. priceBridgeManagedTransfers takes only the base-asset oracle, and the caller appends one oracle per active managed transfer. GlamAccountClient declares priceNeutralBundleDepositors, priceJupiterEarnPositions, priceJupiterBorrowPositions and priceRegisteredPositions(PublicKey), and the moved pricers are abstract rather than defaults, so an implementation outside this SDK must supply them.
* **sdk:** the generated GlamProtocolProgram clients for the production and staging protocol programs (gen and next) no longer declare JUPITER_SWAP_DISCRIMINATOR, jupiterSwapKeys, jupiterSwap or JupiterSwapIxData; build swaps with jupiterSwapV2, or through GlamJupiterProgramClient.swap(JupiterSwapContext).
* **sdk:** GlamJupiterProgramClient's swapChecked, swapUnchecked, swapUncheckedAndNoWrap, swapWithProgramStateChecked, swapWithProgramStateUnchecked and swapWithProgramStateUncheckedAndNoWrap overloads are removed, since they built the v1 jupiter_swap instruction; build a JupiterSwapContext and call swap(JupiterSwapContext), which builds jupiter_swap_v2.

### Features

* **sdk:** price the integration pricers through their ext programs (GLAM-1305) ([26fe6c1](https://github.com/glamsystems/glam-sdk-java/commit/26fe6c1c47df46ee318f256019cf35ec04021cde))
* **services:** prefer the Kamino reserve among equal-priority oracles ([eb664c9](https://github.com/glamsystems/glam-sdk-java/commit/eb664c973f00c25d6b12342feab7c9690f392f0f))


### Code Refactoring

* **sdk:** remove the v1 Jupiter swap overloads ([1fe5e74](https://github.com/glamsystems/glam-sdk-java/commit/1fe5e74352d3649a5df8c10508822eeb5b10bb19))


### Build System

* **sdk:** leave the v1 jupiter_swap instruction out of the protocol clients ([2ee4991](https://github.com/glamsystems/glam-sdk-java/commit/2ee49911cc43dbe93dd81d3b7d36008435745fbc))

## [25.19.0](https://github.com/glamsystems/glam-sdk-java/compare/25.18.1...25.19.0) (2026-09-27)


### ⚠ BREAKING CHANGES

* **services:** InstructionProcessor.createProcessor and DelegateServiceConfig.createInstructionProcessor take the GlamAccounts of the deployment the processor serves, InstructionProcessorImpl gains a mintProgram component, and InstructionProcessor declares mintProgram().
* **sdk:** GlamJupiterProgramClient.fixCPICallerRights(List) and fixCPICallerRights(Instruction) are removed; use the overloads that take the vault key.
* **sdk:** JupiterSwapContext's canonical constructor takes a trailing List<KaminoReserveRefresh> and refuses a null inputMintKey, inputTokenProgram, outputMintKey, outputTokenProgram or swapInstruction; build it through JupiterSwapContext.build(), whose create() now refuses the same nulls (it refused only a null swapInstruction before). A requested skip now names the global configuration and the caller's oracles in the jupiter_swap_v2 instruction, and a wrap prelude's transfer carries the Token program as its sixth account.
* **services:** InstructionProcessor.processInstructions and ExecutionServiceContext.createContext drop their transaction-factory parameter, ExecutionServiceContext.transactionFactory() is removed, and SingleAssetFulfillmentServiceEntrypoint takes and exposes the TxMonitorService as a record component. Instructions go out as SIMD-0385 v1 transactions and must not invoke the ComputeBudget program, which ravina refuses. A SIZE_LIMIT_EXCEEDED refusal of the whole remaining list now posts to the notify client and returns false; before, the empty remainder went on to ravina, which refused it, so the call paged and threw.
* **sdk:** the systems.glam.sdk.proxy package is gone (DynamicGlamAccountFactory, CachedDynamicGlamAccountFactory, IndexedExtAuthority, IndexedReadState, IndexedReadVault, IndexedWriteState, IndexedWriteVault), as are GlamVaultAccounts.loadMappingConfigs and both GlamVaultAccounts.createMapper statics. GlamAccounts.createMapper(Path, DynamicGlamAccountFactory) is replaced by createMapper(), over the documents the jar embeds, and createMapper(Path) and createMapper(Collection) for a local set, each returning an ix-proxy InstructionMapper; map with GlamVaultAccounts.mappingContext(). GlamAccounts declares exponentIntegrationProgram, invokedExponentIntegrationProgram and readExponentIntegrationAuthority, which any implementation other than GlamAccountsRecord must supply, and GlamAccountsRecord's canonical constructor gains invokedExponentIntegrationProgram; build it through GlamAccountsBuilder. The jar's mapping documents live under glam/ix-mappings/<environment>/ in the schema_version 1 document format, no longer as flat mapping-configs-v1 files.
* **services:** IntegLookupTableCache is removed; IntegrationServiceContext.createContext drops its IntegLookupTableCache parameter and integTableCache() is gone. DelegateServiceConfig drops tableCacheConfig() and createLookupTableCache(), and createTransactionProcessor no longer takes a LookupTableCache; the ravina TransactionProcessor it returns has no lookupTableCache() at all (removed in ravina 25.6.2). InstructionProcessor and FormatUtil.formatInstructionException lose their lookup-table overloads and parameters, and DefensivePollingConfig loses integTables. JSON service configs must drop "tableCache" and "defensivePolling.integTables". Alert payloads lose "t" and "numTables".
* **sdk:** the systems.glam.sdk.lut package is removed (VaultTableBuilder, VaultTableBuilder.Builder, TableTask), and GlamVaultAccounts drops vaultTableFilter, activeVaultTableFilters, deriveVaulKeyTableFilter, deriveVaultActiveTableFilters and vaultTablePrefixKeys.

### Features

* **sdk:** add pricing instructions for Loopscale loans, strategies, vault positions, Neutral Trade bundle depositors, and Registered Positions ([0f9be4f](https://github.com/glamsystems/glam-sdk-java/commit/0f9be4fe4c3cb5d155145c377e08a6d54d0c9189))
* **sdk:** resolve the Orca oracle prefix and the Loopscale market as supplied accounts (GLAM-1447) ([9dc5bf0](https://github.com/glamsystems/glam-sdk-java/commit/9dc5bf053e7f59ce4651a0ad77176ea895df4662))
* **sdk:** serve the ix-mapper mapping documents from the jar ([09e1ae7](https://github.com/glamsystems/glam-sdk-java/commit/09e1ae70bb078b8f0f5e33ebfd79e99941c6c16d))


### Bug Fixes

* **sdk:** build Jupiter swaps the protocol handlers accept (GLAM-1537) ([c65f70a](https://github.com/glamsystems/glam-sdk-java/commit/c65f70a028c6ef0ed3ab03373a13bab5c442a7a4))
* **sdk:** drop the positional fixCPICallerRights overloads (GLAM-1537) ([3e00eaf](https://github.com/glamsystems/glam-sdk-java/commit/3e00eaf469e4e3388d3cfe4eabc61d118a6d520e))
* **sdk:** refuse an update_strategy market change the deployed program cannot make (GLAM-1447) ([8f50659](https://github.com/glamsystems/glam-sdk-java/commit/8f506595ce53fd2a9097950157490c9994a02ae4))
* **sdk:** skip a delegate protocol bit the SDK does not know ([38bf225](https://github.com/glamsystems/glam-sdk-java/commit/38bf225cbec782396209c66d344f73c91a8e13e6))
* **services:** report a batch's instruction count before the batch is cleared ([a936d23](https://github.com/glamsystems/glam-sdk-java/commit/a936d2396b6bb61f048a2f409b183103be11795f))
* **services:** retry a stale price quietly on the deployment's own mint program ([71a5738](https://github.com/glamsystems/glam-sdk-java/commit/71a57384664f3468f3ef048b0b939f22c0cb6609))


### Documentation

* name 25.19.0 for the breaking sdk and services changes, and say how a version is named here ([9ce9e77](https://github.com/glamsystems/glam-sdk-java/commit/9ce9e7772cf51c99391ba5d5fab4b5a92a3c88ca))


### Code Refactoring

* **sdk:** map instructions through ix-proxy's mapping documents ([8cd2936](https://github.com/glamsystems/glam-sdk-java/commit/8cd2936717fa896aad470506f1b794d340e8bea7))
* **sdk:** remove vault lookup-table building ([e873065](https://github.com/glamsystems/glam-sdk-java/commit/e87306580ea3862df7c2bb06129abff4fd0a74d7))
* **services:** drop lookup-table caching and v0 transaction plumbing ([6e55c9f](https://github.com/glamsystems/glam-sdk-java/commit/6e55c9faa047c51ff594e946b90b2f3405f678e8))
* **services:** send instructions on ravina's v1-only path ([a610d1d](https://github.com/glamsystems/glam-sdk-java/commit/a610d1d282fe3545c701251a62f141450df33f0c))

## [25.18.1](https://github.com/glamsystems/glam-sdk-java/compare/25.18.0...25.18.1) (2026-09-16)


### Features

* **services:** tick a LoopHeartbeat once per cycle of every long-running loop ([fc7b0d9](https://github.com/glamsystems/glam-sdk-java/commit/fc7b0d92cf44184bbd622774c4cccef4cec5108f))


### Bug Fixes

* **sdk:** the published jar ships the mapping configs ([8bf8ff3](https://github.com/glamsystems/glam-sdk-java/commit/8bf8ff38d74628a56940a9e333c954e05daac482))
* **services:** reset the batch executor's backoff tier on every committed batch ([bb830ff](https://github.com/glamsystems/glam-sdk-java/commit/bb830ff4060fa2af30fd8d273d4325ce60edaf30))
* **services:** reset the requeue cursor after a failed batch so a second failure cannot requeue it again ([1d2e0c1](https://github.com/glamsystems/glam-sdk-java/commit/1d2e0c1d2e53dc8997f29cb7d8c77db4b30dccff))
* **services:** tick the batch executor heartbeat per committed batch, not per drain ([ae193a3](https://github.com/glamsystems/glam-sdk-java/commit/ae193a318751d8ce742f17e0df71ba0afd2281f9))

## [25.18.0](https://github.com/glamsystems/glam-sdk-java/compare/25.17.1...25.18.0) (2026-09-01)


### ⚠ BREAKING CHANGES

* **services:** GlobalConfigListener.onUnexpectedOracleChange gained an AssetMetaContext[] parameter. Overriders of the 3-arg default compiled against an older release keep compiling but silently stop being called; recompile against this release (the @Override check flags the stale signature) and adopt the 4-arg form.

### Features

* **services:** add hasAssetMetaForOracle; answer invalidated-cache queries as a miss ([cee1a93](https://github.com/glamsystems/glam-sdk-java/commit/cee1a9303080eac01c7345b19f1cb5f85516f683))
* **services:** hand the applied contexts to onUnexpectedOracleChange ([a98bbcc](https://github.com/glamsystems/glam-sdk-java/commit/a98bbccaa3c15477c593a8d895ffb561008cd3f3))


### Miscellaneous Chores

* release 25.18.0 ([5752508](https://github.com/glamsystems/glam-sdk-java/commit/5752508b91ed03e16ff3cfbe5d66cbec6d905672))

## [25.17.1](https://github.com/glamsystems/glam-sdk-java/compare/25.17.0...25.17.1) (2026-08-21)


### ⚠ BREAKING CHANGES

* **kamino:** move the Kamino cache and Scope contexts to vault-stat-service

### Features

* **gen:** generate ext_marinade. ([c864f93](https://github.com/glamsystems/glam-sdk-java/commit/c864f936c6ce5a50971450687695bde07e5c3e7b))
* **idl:** build a staged client from every repo IDL that is ahead of chain ([935a7d1](https://github.com/glamsystems/glam-sdk-java/commit/935a7d14fb3c44dcc5b24fe27a50a66c7d74698b))
* **idl:** generate the three deployed GLAM programs that had no client ([040327b](https://github.com/glamsystems/glam-sdk-java/commit/040327becafb80154314e9488f8a3988771a13a0))
* **idl:** regenerate every GLAM client against idl-clients 25.19.0 ([1fee3f8](https://github.com/glamsystems/glam-sdk-java/commit/1fee3f85c82382692d327905d1fa6fc3efd75196))
* **idl:** stage clients for three more programs and pick up redeployed IDLs ([92318b2](https://github.com/glamsystems/glam-sdk-java/commit/92318b285a8a222f29d92dddc8e7e9ed7dbb0adc))
* **idl:** track every GLAM program against the published repo IDL ([8c9ae50](https://github.com/glamsystems/glam-sdk-java/commit/8c9ae50d626963bc77f3872acc0797ac2a57ed2c))
* **kamino:** relay Scope OraclePrices writes to scope listeners ([498ef6c](https://github.com/glamsystems/glam-sdk-java/commit/498ef6cee94e420dcefe46fb5822fe60c78006d4))
* **kamino:** report raw reserve writes on every ingestion path ([8eef619](https://github.com/glamsystems/glam-sdk-java/commit/8eef6194436421066ec04be85f28f7f9e10f3fda))


### Bug Fixes

* 4,285,597 runs, no findings. ([28f4815](https://github.com/glamsystems/glam-sdk-java/commit/28f4815cc6c4462bb0e998e01de3d4dfce5d435a))
* **idl:** read the Kamino IDLs from idl-clients rather than from chain ([cdcc910](https://github.com/glamsystems/glam-sdk-java/commit/cdcc910efd9e55a986197ea29a4fd70cc42de06d))
* **kamino:** guard each reserve listener against a throwing implementation ([b656457](https://github.com/glamsystems/glam-sdk-java/commit/b6564579ef7f5a3eef59ba781aa0cb92551e7467))
* **rpc:** contain fetch-loop failures and release dropped unique claims ([6aa001e](https://github.com/glamsystems/glam-sdk-java/commit/6aa001e23a336106a6737e54960de9f25cd746fc))
* **services:** make fetch failure cleanup finite ([9278933](https://github.com/glamsystems/glam-sdk-java/commit/92789332d7d51f2a4ff5ea6513228063b1e4a335))
* **state:** reject a base asset absent from a changed assets vector ([b161470](https://github.com/glamsystems/glam-sdk-java/commit/b1614707e96a5b52e3c9ee1eb1b1bd6d2cffdb0f))
* **state:** reject malformed state-account counts, drop an O(n) walk ([1400043](https://github.com/glamsystems/glam-sdk-java/commit/140004341d148c9a03ff1d4459add04e0db29a65))
* **test:** make poll-loop fixture lifecycle unconditional and capture thread-safe ([0759f92](https://github.com/glamsystems/glam-sdk-java/commit/0759f92cc48a20de50a4c8a368b0be9c6ae94e26))


### Performance Improvements

* **kamino:** defer the program sweep while the websocket is audible ([e9b170d](https://github.com/glamsystems/glam-sdk-java/commit/e9b170d236fd4a9195f138876e24e5367980132e))


### Code Refactoring

* **kamino:** move the Kamino cache and Scope contexts to vault-stat-service ([4dee6f1](https://github.com/glamsystems/glam-sdk-java/commit/4dee6f1330ea701a5ef90753a75c7450a999d725))

## [25.17.0](https://github.com/glamsystems/glam-sdk-java/compare/25.16.0...25.17.0) (2026-07-24)


### ⚠ BREAKING CHANGES

* **services:** a fetch delay under one millisecond now throws IllegalArgumentException at construction instead of polling in a tight loop.
* delegateHasPermissions now denies a delegate that holds only part of the requested permission mask, where it previously allowed it, and returns false instead of throwing when the delegate has no grant for a required integration program. Callers relying on the previous result must re-check the permission maps they pass in. GlamAccounts#mintPDA(publicKey, 256) now throws instead of returning share class 0's PDA.

### Bug Fixes

* correct instruction building, permission and cache defects ([a562eea](https://github.com/glamsystems/glam-sdk-java/commit/a562eea69216b5efcfd3a70e35828896b4395015))
* Properly handle &gt;100 account batches. ([45c6dc2](https://github.com/glamsystems/glam-sdk-java/commit/45c6dc2c93f897e93a1d2303ce80fff18d37da1d))
* Properly track sql batch size when multiple sub-items. ([64b9244](https://github.com/glamsystems/glam-sdk-java/commit/64b924484b26e7386a6cc39102294a01ce29e812))
* **services:** reject poll delays that round down to no delay ([5af1af6](https://github.com/glamsystems/glam-sdk-java/commit/5af1af66998aa13974df8483fa42dd162213dd1e))
* **services:** repair three crash/staleness defects in fulfillment and kamino ([56c259c](https://github.com/glamsystems/glam-sdk-java/commit/56c259cef1c781df503c69863276793b246d1c35))
* **srcgen:** Regenerate with fixed string length reporting. ([afd7d4d](https://github.com/glamsystems/glam-sdk-java/commit/afd7d4d8c4dec5a795bcba393495d33457e5b833))


### Miscellaneous Chores

* release 25.17.0 ([8500739](https://github.com/glamsystems/glam-sdk-java/commit/8500739826c45ad8a0417366f357228072929583))

## [25.16.0](https://github.com/glamsystems/glam-sdk-java/compare/25.15.0...25.16.0) (2026-07-17)


### Features

* **sdk:** add guarded mint reference nav helpers ([7d1f6b4](https://github.com/glamsystems/glam-sdk-java/commit/7d1f6b4008a6ba9187e38f5889295874276638d2))
* **sdk:** add optional parameters for improved flexibility and configurability across multiple SDK types ([c1ee546](https://github.com/glamsystems/glam-sdk-java/commit/c1ee5462d41d9208b187135bd2d2d4e77f3008a9))
* **sdk:** add unsigned `u8` parameters for enhanced precision and flexibility across generated types ([a8f0a83](https://github.com/glamsystems/glam-sdk-java/commit/a8f0a83e685e6aecac13fab6346f779631f20521))


### Bug Fixes

* **services:** handle empty global config data and log file read errors ([76c1719](https://github.com/glamsystems/glam-sdk-java/commit/76c1719e17d08cc694d7671a4e562e33ac2efd97))


### Miscellaneous Chores

* release 25.16.0 ([c8eb1a2](https://github.com/glamsystems/glam-sdk-java/commit/c8eb1a2d526f756b4d5dbe93e911c855654bbba4))

## [25.15.0](https://github.com/glamsystems/glam-sdk-java/compare/25.14.3...25.15.0) (2026-06-29)


### Features

* **sdk:** migrate integer fields to use unsigned data types for improved range handling ([4f9c0f3](https://github.com/glamsystems/glam-sdk-java/commit/4f9c0f3e25303d979b5f0201dbe807dfdbed497f))


### Miscellaneous Chores

* release 25.15.0 ([1a1c616](https://github.com/glamsystems/glam-sdk-java/commit/1a1c616da4ef86a1c593a347e59780caeae6009d))

## [25.14.3](https://github.com/glamsystems/glam-sdk-java/compare/25.14.2...25.14.3) (2026-06-27)


### Features

* **sdk:** migrate Epi to Rpi ([224bb25](https://github.com/glamsystems/glam-sdk-java/commit/224bb253cdf4432063919a82ce26ee48152f6c2d))

## [25.14.2](https://github.com/glamsystems/glam-sdk-java/compare/25.14.1...25.14.2) (2026-06-21)


### Features

* **services:** extend `IntegrationServiceContext` with Marginfi and Meteora integration ([def1381](https://github.com/glamsystems/glam-sdk-java/commit/def13815a20083c9a165250b0782ef80580b080b))

## [25.14.1](https://github.com/glamsystems/glam-sdk-java/compare/25.14.0...25.14.1) (2026-06-20)


### Features

* **sdk:** add generated ExtMarginfi program, constants, PDAs, and types ([23e55b4](https://github.com/glamsystems/glam-sdk-java/commit/23e55b43f567c94be0a896e0eecd675d3dd9b376))
* **sdk:** update MintPolicy to support pauseOnOverdue flag and enhance reserved field handling ([7eff92d](https://github.com/glamsystems/glam-sdk-java/commit/7eff92dcb05ca5e1429e04557afb7f8a56c31ce5))

## [25.14.0](https://github.com/glamsystems/glam-sdk-java/compare/25.13.0...25.14.0) (2026-06-17)


### Miscellaneous Chores

* release 25.14.0 ([ba64b1c](https://github.com/glamsystems/glam-sdk-java/commit/ba64b1cf38a6ed0cfb788bf92d2884e0e05bfffe))

## [25.13.0](https://github.com/glamsystems/glam-sdk-java/compare/25.12.9...25.13.0) (2026-06-16)


### Features

* **sdk/services:** enhance SDK and services with Orca and Jupiter integration support ([615c4f9](https://github.com/glamsystems/glam-sdk-java/commit/615c4f9f129cf4408ac5a660d74ea33e69c26a9b))
* **sdk:** Add cctp, marinade, NT and stake pool staging programs ([a99daa5](https://github.com/glamsystems/glam-sdk-java/commit/a99daa51c1598cb1adebd9406766ae2dabe906fe))
* **sdk:** add generated ExtJupiter program, constants, PDAs, and types ([728bfab](https://github.com/glamsystems/glam-sdk-java/commit/728bfab9f7517ed0720ebb2ce1294e51251ba264))
* **sdk:** add generated ExtOrca program, constants, PDAs, and types ([aef42cc](https://github.com/glamsystems/glam-sdk-java/commit/aef42cc832068988045cea5166143d3cb34d2a91))


### Miscellaneous Chores

* release 25.13.0 ([f732061](https://github.com/glamsystems/glam-sdk-java/commit/f732061bcb0a6e48e6c71f94c25ddbd4e1d0149c))

## [25.12.9](https://github.com/glamsystems/glam-sdk-java/compare/25.12.8...25.12.9) (2026-06-14)


### Features

* **sdk:** add support for pricing multiple integration strategies ([3eec4f1](https://github.com/glamsystems/glam-sdk-java/commit/3eec4f14365e1dfec5026ab3d06b2f7453ccd913))

## [25.12.8](https://github.com/glamsystems/glam-sdk-java/compare/25.12.7...25.12.8) (2026-06-14)


### Features

* **services:** add `forceCacheRefresh` method to `GlobalConfigCache` ([b734928](https://github.com/glamsystems/glam-sdk-java/commit/b7349283ed59c682cccd307b8abca8bf4006f034))

## [25.12.7](https://github.com/glamsystems/glam-sdk-java/compare/25.12.6...25.12.7) (2026-06-13)


### Bug Fixes

* **services:** resolve null check and data comparison issues in `GlobalConfigCacheImpl` ([466a362](https://github.com/glamsystems/glam-sdk-java/commit/466a362028a8babc4176d5a908767ba0ba66b7b9))

## [25.12.6](https://github.com/glamsystems/glam-sdk-java/compare/25.12.5...25.12.6) (2026-06-13)


### Bug Fixes

* **services/sdk:** fix integ authority for pricing loopscale strats and vaults. ([1b8a99f](https://github.com/glamsystems/glam-sdk-java/commit/1b8a99f8333058b706564721eebe4cbd89b70646))

## [25.12.5](https://github.com/glamsystems/glam-sdk-java/compare/25.12.4...25.12.5) (2026-06-13)


### Features

* **sdk:** add Loopscale vault and LP token integration types ([868ac44](https://github.com/glamsystems/glam-sdk-java/commit/868ac4483bdbe6f82d8c325236f71f4570013550))

## [25.12.4](https://github.com/glamsystems/glam-sdk-java/compare/25.12.3...25.12.4) (2026-06-12)


### Features

* **services:** refactor state account serialization and add delegate ACL support ([d5e2167](https://github.com/glamsystems/glam-sdk-java/commit/d5e21677d19f4802978ff4ef486141a5414e7da5))

## [25.12.3](https://github.com/glamsystems/glam-sdk-java/compare/25.12.2...25.12.3) (2026-06-08)


### Miscellaneous Chores

* release 25.12.3 ([4765494](https://github.com/glamsystems/glam-sdk-java/commit/47654944ba0126be88353ba4fca51ce90339f869))

## [25.12.2](https://github.com/glamsystems/glam-sdk-java/compare/25.12.1...25.12.2) (2026-06-08)


### Features

* **sdk:** add generated Phoenix SDK types and program methods ([9eb390e](https://github.com/glamsystems/glam-sdk-java/commit/9eb390efa86ad543119ee4aee1967a839992ac6d))

## [25.12.1](https://github.com/glamsystems/glam-sdk-java/compare/25.12.0...25.12.1) (2026-06-08)


### Features

* **sdk:** add Wormhole and Hyperliquid observation config types ([7c379e6](https://github.com/glamsystems/glam-sdk-java/commit/7c379e6da4205a1e5181132b6f456baabd757aa0))
* **sdk:** refactor policy types and update Loopscale program integration ([1d11acb](https://github.com/glamsystems/glam-sdk-java/commit/1d11acb810d58c4b6046b5ff3c952fa0dc66c31c))
* **sdk:** replace refinance ledger params and add new config types ([f4b62b3](https://github.com/glamsystems/glam-sdk-java/commit/f4b62b3d8b0866139c8e7fac97bc611da71c5ad4))
* **services, build:** enhance account fetching and GitHub package publishing ([1d64ade](https://github.com/glamsystems/glam-sdk-java/commit/1d64aded17d86bc08f72fc50dfafb2b139568f20))

## [25.12.0](https://github.com/glamsystems/glam-sdk-java/compare/25.11.0...25.12.0) (2026-06-01)


### Features

* **sdk, services:** update dependencies and replace solana programs ([70cfebd](https://github.com/glamsystems/glam-sdk-java/commit/70cfebd89a66b03cd97ac96d8360112ca8a7adab))


### Bug Fixes

* **ci:** explicitly pass release-please secrets ([71fb2d6](https://github.com/glamsystems/glam-sdk-java/commit/71fb2d6a98b84aa8b54e9b1081b9417c89619e5e))


### Miscellaneous Chores

* release 25.12.0 ([c606ef4](https://github.com/glamsystems/glam-sdk-java/commit/c606ef468bc8c4a6a1725c2d75254da076fb2ece))

## [25.11.0](https://github.com/glamsystems/glam-sdk-java/compare/25.10.1...25.11.0) (2026-05-22)


### ⚠ BREAKING CHANGES

* Kamino Cache now only stores binary data to disk and does not JSON serialize any context for change subscribers.

### Features

* bridge and phoenix staging programs, integ authority PDAs ([804d2e3](https://github.com/glamsystems/glam-sdk-java/commit/804d2e3db99c94374f653513f1f09818001013f4))


### Bug Fixes

* trigger release ([1337577](https://github.com/glamsystems/glam-sdk-java/commit/1337577d580997e2c5a7d55c272b2b771053f1bf))

## [25.10.1](https://github.com/glamsystems/glam-sdk-java/compare/25.10.0...25.10.1) (2026-05-01)


### Features

* **ci:** add release-please and PR checker workflows ([2ecfdf6](https://github.com/glamsystems/glam-sdk-java/commit/2ecfdf61016313412da21dadcea0382a8da5d6fe))

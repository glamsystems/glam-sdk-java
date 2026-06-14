# Changelog

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

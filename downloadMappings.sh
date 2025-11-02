#!/usr/bin/env bash

set -e

rm -rf glam/
git clone -n --depth=1 --filter=tree:0 https://github.com/glamsystems/glam-sdk.git glam
cd glam
git sparse-checkout set --no-cone /remapping_v1
git checkout

cd ..

exit 0

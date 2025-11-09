#!/usr/bin/env bash

set -e

rm -rf glam/
git clone -n --depth=1 --filter=tree:0 https://github.com/glamsystems/ix-mapper-ts.git glam

git -C glam sparse-checkout set --no-cone /mapping-configs-v1
git -C glam checkout

exit 0

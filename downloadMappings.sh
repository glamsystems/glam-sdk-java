#!/usr/bin/env bash
#
# Materializes the mapping documents the sdk jar embeds under glam/ix-mappings: a sparse
# checkout of glamsystems/ix-mapper-ts at the commit pinned below (its
# src/generated/mapping/<environment> directories), into the untracked glam/ directory. The sdk jar task runs this itself, so a fresh clone and CI both build
# the same jar; the pin is what makes that jar a function of this repository's commit
# rather than of whatever ix-mapper-ts main held when the build ran. ./syncMappings.sh
# moves the pin.
#
# Idempotent: a glam/ already at the pinned commit is left alone.

set -euo pipefail

readonly MAPPINGS_REPO="https://github.com/glamsystems/ix-mapper-ts.git"
readonly MAPPINGS_REF="16320bf603b45f251567370172de8a0498bbeac4"

cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ -d glam/src/generated/mapping && "$(git -C glam rev-parse HEAD 2>/dev/null || true)" == "$MAPPINGS_REF" ]]; then
  exit 0
fi

rm -rf glam/
git clone -q -n --depth=1 --filter=tree:0 "$MAPPINGS_REPO" glam
# GitHub serves any reachable commit by full sha, so the pin needs no branch or tag.
git -C glam fetch -q --depth=1 origin "$MAPPINGS_REF"
git -C glam sparse-checkout set --no-cone /src/generated/mapping
git -C glam checkout -q "$MAPPINGS_REF"

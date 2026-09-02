#!/usr/bin/env bash
#
# Moves the mapping-config pin in downloadMappings.sh to ix-mapper-ts's current default
# branch head (or to the commit given as the first argument) and re-materializes glam/.
# The pin change is the reviewable diff; commit it with the jar it produces.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

readonly repo="$(sed -n 's/^readonly MAPPINGS_REPO="\(.*\)"$/\1/p' downloadMappings.sh)"
readonly current="$(sed -n 's/^readonly MAPPINGS_REF="\(.*\)"$/\1/p' downloadMappings.sh)"
if [[ -z "$repo" || -z "$current" ]]; then
  echo "syncMappings: could not read MAPPINGS_REPO/MAPPINGS_REF from downloadMappings.sh" >&2
  exit 1
fi

if [[ $# -ge 1 ]]; then
  target="$1"
else
  target="$(git ls-remote "$repo" HEAD | cut -f1)"
fi
if [[ ! "$target" =~ ^[0-9a-f]{40}$ ]]; then
  echo "syncMappings: '$target' is not a full commit sha" >&2
  exit 1
fi

if [[ "$target" == "$current" ]]; then
  echo "syncMappings: already pinned to $current"
else
  sed -i.bak "s/^readonly MAPPINGS_REF=\"$current\"$/readonly MAPPINGS_REF=\"$target\"/" downloadMappings.sh
  rm -f downloadMappings.sh.bak
  echo "syncMappings: pin moved $current -> $target"
fi
./downloadMappings.sh

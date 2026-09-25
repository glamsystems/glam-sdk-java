#!/usr/bin/env bash
#
# Moves the mapping-document pin in downloadMappings.sh to ix-mapper-ts's current default
# branch head (or to the commit given as the first argument), re-materializes glam/, and
# refreshes the system-program fixtures the sdk's mapper tests read
# (sdk/src/test/resources/mapping) from the newly pinned documents. The pin change and
# any fixture change are the reviewable diff; commit them with the jar they produce.

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

# the checked-in fixtures are copies of the pinned documents; keep them so
readonly system_program="11111111111111111111111111111111.json"
for environment in production staging; do
  cp "glam/src/generated/mapping/$environment/$system_program" "sdk/src/test/resources/mapping/$environment/$system_program"
done

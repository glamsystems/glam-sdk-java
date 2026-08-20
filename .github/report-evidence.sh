#!/usr/bin/env bash
# Every commit that moves a generated channel record must carry that generation's
# movement evidence.
#
# Consumer AGENTS.md files state the invariant: "A change to a generated `sources.json`
# hash without a matching change to the movement report means the generation was run
# without retaining its channel-movement evidence." This is that sentence as a gate.
# Movement is an event: the run that first sees it is the only one that reports it, so
# evidence a commit fails to carry is unrecoverable one generation later. In idl-clients
# the gate had fired on six of the thirty commits before it existed, each discovered a
# generation too late; in glam-sdk-java a ten-record redeploy pickup (92318b2) lost its
# report the same way before the gate arrived there.
#
# CANONICAL COPY — this file lives in idl-src-gen, beside the serializer whose output it
# reads. The KEYS set and the anchored one-key-per-line greps below are contracts with
# `IdlChannels.sourcesJson`, which is why the audit lives here: a serializer change and
# the audit change it forces land in one commit, and ReportEvidenceScriptTests fails the
# build that changes one side without the other. Consumers run a byte-identical vendored
# copy at `.github/report-evidence.sh` — the pre-push hook must work in any clone,
# offline, with no idl-src-gen checkout beside it, and a gate fetched at main would
# change behavior fleet-wide with no consumer-side review. Consumer CI diffs the
# vendored copy against this file on every push-audit run and every scheduled monitor
# run, so drift is caught within hours; `consumer/sync.sh` re-vendors it.
#
# Keyed on the movement-implying *lines* of a modified record, never on the file being
# touched. Three facts force that narrowing:
#
#   * `programDataStateSlot` can be restamped fleet-wide with no movement anywhere
#     (idl-src-gen#4: a fatal between the record read and the per-program restore leaves
#     the corpus recordless, and the next run takes the silent first-generation path).
#     A file-level check would fail every one of those records for nothing.
#   * The report carries no timestamp by design, so two consecutive no-movement runs
#     write it byte-identical. A file-level check would demand a diff that must not exist.
#   * A top-level key can *appear* in every record at once when the record format grows
#     a field (glam-sdk-java 1fee3f8: a first `programDataPayloadSha256` in all 24
#     records, not one `-` line among them, and a generation behind every record).
#     Introduction is the schema moving, not the program, so a top-level key implies
#     movement only when the record also *lost* a value for it. A channel `hash` counts
#     on any sign: a channel appearing or disappearing IS movement
#     (IdlChannels.Movement.Kind), and an appearing one arrives as pure `+` lines.
#
# Modified and renamed records count; a renamed record whose content also moved is
# detected under its destination path. An *added* record is a program's first
# generation — no baseline to have moved against — and a deleted one is a program
# leaving the corpus; neither demands evidence.
#
# Usage:  report-evidence.sh <range>                 # e.g. HEAD~30..HEAD, or $before..$after
#         report-evidence.sh <commit>                # that commit alone
#         report-evidence.sh <sha> --not --remotes   # everything not yet published
#         report-evidence.sh --pre-push              # hook mode: git's ref lines on stdin
#
# Range arguments are handed to `git rev-list` as given, so any commit set it names can
# be audited; the single-commit form is the one convenience on top of that. `--pre-push`
# reads the lines git feeds a pre-push hook and audits exactly what each push would
# publish, at the one moment the fix is still free: a pushed commit is an ancestor of a
# remote ref and must not be rewritten, so CI can only report what is already permanent,
# while here `git commit --amend` is a legal answer.
#
# A commit that legitimately moves a record without a generation behind it — a hand-fixed
# hash, a package repointed at another address — says so in trailers the audit trail then
# carries with it, and the excuse is checked, not just required to exist:
#
#   * `Report-Evidence:` — why no report accompanies the movement, in prose.
#   * `Report-Evidence-Path:` — one per moved record, naming its repo-relative path.
#
# The path set must equal, exactly, the set of records the audit itself detects — a
# superset would let a list be padded until it covers whatever moved, and a subset is an
# omission, which is the error this exists to catch: the first excused commit written in
# idl-clients summarized four redeploys and silently omitted a fifth record's
# channel-hash move. Prose cannot be validated; the paths can be, so the checkable half
# of the claim lives in them. On failure the audit prints the exact set it detected, so
# complying is a copy-paste rather than a guess. The report counts as evidence only when
# its regular-file blob arrives or changes — a mode-only touch or a deletion is not
# providing it.
#
# This is a mistake-catcher, not a security boundary. Anyone who can push can edit this
# script in the same push, so a deliberate adversary is out of scope by construction.
# Shapes deliberately left out rather than detected, because closing them was tried and
# cost a small semantic-diff system that changed no verdict across a full replay of
# idl-clients history: movement laundered through a delete and a re-add (same commit or later —
# the re-add is indistinguishable from a first generation), a record rewritten compact
# (the generator always pretty-prints; the anchored grep assumes it), and merge commits
# (--no-merges; the consumer repositories have no human merges and release-please's
# never touch a record). One legitimate shape is known to trip the evidence check
# rather than escape it: a second consecutive member-less movement (an unmodeled IDL
# section moved, versions unchanged) renders a report byte-identical to the first, so
# the blob cannot change (idl-src-gen#5 is the fix — self-distinguishing entries);
# until then the trailers are the honest answer for that commit.
set -uo pipefail

# Pathspecs below are repo-relative; run from anywhere inside the repository.
cd "$(git rev-parse --show-toplevel)" || exit 2

# The movement report. Named explicitly rather than globbed: the standing-gap dashboard
# beside it — the report's name with "-gap" before the extension, per ChangeReport — is
# NOT evidence of anything, because it re-renders whether or not this run saw movement.
# The overrides exist for a consumer whose layout diverges; neither current consumer
# sets them, and a RECORDS value must read identically as a git pathspec and as a shell
# case pattern (the default does).
REPORT="${REPORT_EVIDENCE_REPORT:-idl-change-report.txt}"
RECORDS="${REPORT_EVIDENCE_RECORDS:-*/gen/sources.json}"
# Discriminated on the basename: a dot in a directory component is not an extension.
case "${REPORT##*/}" in
  *.*) GAP="${REPORT%.*}-gap.${REPORT##*.}" ;;
  *) GAP="${REPORT}-gap" ;;
esac
# Deliberately not overridable: which keys imply movement is the serializer's fact, not
# a consumer's choice, and a consumer that could narrow the set could silently stop
# auditing. `programDataState` is a member because a pure state transition —
# upgradeable to closed, say — rewrites only the state and its slot, and IS movement
# (the generator alerts it and writes a movement entry, so the invariant applies). The
# state's *slot* stays excluded: idl-src-gen#4's baseline-blind restamps moved it
# fleet-wide while leaving every state value untouched, so the value is the honest
# signal and the slot is the noisy one. `program` is a member because it is the one
# field that catches a package repointed at another address — the serializer writes it
# for exactly that reason — and under the introduction rule below only an address
# *change* counts, never the key's first appearance. ReportEvidenceScriptTests holds
# this line to IdlChannels.sourcesJson.
KEYS='"(hash|lastDeploySlot|program|programDataState|programDataPayloadSha256)"'

# Every git invocation that prints diff or file-name output runs through this: color
# codes prefix each line and blind the anchored ^[+-] grep — a clone with
# color.ui=always would pass every movement silently — and quotePath escaping would
# make a non-ASCII record path match no pathspec.
GIT=(git -c color.ui=false -c core.quotePath=false)

# Audits every commit `git rev-list --no-merges <args>` names. Prints one line per
# verdict and a per-call summary; returns 1 when a commit lacks evidence, 2 when the
# arguments name nothing resolvable — an unresolvable range must fail loudly, never
# read as an empty one that passed.
audit() {
  local commits
  commits=$(git rev-list --no-merges "$@") || {
    echo "report-evidence: git rev-list could not resolve: $*" >&2
    return 2
  }

  local violations=0 checked=0
  local commit name_status status p1 p2 record hunks lines moved moving moved_paths sample
  local report_now report_before detected excuse declared record_count missing extra
  while read -r commit; do
    [ -n "$commit" ] || continue
    checked=$((checked + 1))

    # Movement is detected per record, not per commit, because the path set is part of
    # the contract: a record restamped only at `programDataStateSlot` is modified but
    # did not move, and must be neither counted nor demanded in the trailers. Statuses
    # M and R both count — a rename that also moved content is the "package repointed"
    # case itself — and an R record is keyed to its destination. -M pins rename
    # detection on rather than inheriting it.
    #
    # Materialized before the loop, not process-substituted into it: a substitution's
    # exit status is discarded, so a git that could not enumerate the commit's records
    # (a missing blob in a partial clone) would certify zero movement instead of
    # failing the audit.
    name_status=$("${GIT[@]}" show --format='' --name-status -M "$commit" -- "$RECORDS") || {
      echo "report-evidence: git show --name-status failed for $commit" >&2
      return 2
    }
    moving=0
    moved_paths=''
    sample=''
    while IFS=$'\t' read -r status p1 p2; do
      [ -n "$status" ] || continue
      case "$status" in
        M*) record="$p1" ;;
        R*) record="$p2" ;;
        *) continue ;;
      esac
      case "$record" in $RECORDS) ;; *) continue ;; esac
      # --text --no-textconv: a .gitattributes rule (`*.json -diff`, a textconv driver)
      # collapses the diff to "Binary files differ", which greps as no movement — one
      # committed attributes line would blind every consumer silently. The records are
      # text by construction, so forcing a textual diff is always the honest reading.
      hunks=$("${GIT[@]}" show --format='' --unified=0 -M --text --no-textconv "$commit" -- "$p1" ${p2:+"$p2"}) || {
        echo "report-evidence: git show failed for $commit -- $record" >&2
        return 2
      }
      lines=$(printf '%s\n' "$hunks" | grep -E "^[+-][[:space:]]*${KEYS}") || true
      [ -n "$lines" ] || continue
      # The introduction rule from the header: a channel `hash` line counts on any
      # sign; any other key's lines count only when this record also has a `-` line
      # for that same key. Scoped per record, so one record's loss never vouches for
      # another record's introduction.
      moved=$(printf '%s\n' "$lines" | awk '
        {
          all[NR] = $0
          match($0, /"[^"]+"/)
          key = substr($0, RSTART + 1, RLENGTH - 2)
          keyof[NR] = key
          if (key == "hash") keep[NR] = 1
          else if ($0 ~ /^-/) lost[key] = 1
        }
        END { for (i = 1; i <= NR; i++) if (keep[i] || lost[keyof[i]]) print all[i] }
      ')
      [ -n "$moved" ] || continue
      moving=$((moving + $(printf '%s\n' "$moved" | grep -c .)))
      moved_paths="${moved_paths}${record}"$'\n'
      sample="${sample}${moved}"$'\n'
    done <<< "$name_status"
    [ "$moving" -gt 0 ] || continue

    # Evidence is the report's *content* arriving or changing in this commit — a status
    # letter is not enough, since a mode-only change is status M with an untouched blob,
    # and a deleted report is the opposite of evidence. The object must be a regular
    # file: a symlink or tree at the report path is not a report.
    report_now=$(git ls-tree "$commit" -- "$REPORT" | awk '$1 ~ /^100/ {print $3}')
    report_before=$(git ls-tree "$commit^" -- "$REPORT" | awk '$1 ~ /^100/ {print $3}')
    if [ -n "$report_now" ] && [ "$report_now" != "$report_before" ]; then
      continue
    fi

    detected=$(printf '%s' "$moved_paths" | sort -u)
    excuse=$(git show -s --format='%(trailers:key=Report-Evidence,valueonly,unfold)' "$commit" | tr -d '\n')
    # sort -u: the contract is a set, so a duplicated trailer is harmless rather than a
    # phantom "declared but did not move".
    declared=$(git show -s --format='%(trailers:key=Report-Evidence-Path,valueonly,unfold)' "$commit" \
      | grep . | sort -u) || true

    if printf '%s' "$excuse" | grep -q '[^[:space:]]' && [ "$declared" = "$detected" ]; then
      echo "note  $(git show -s --format='%h %s' "$commit")"
      echo "      record moved with no report; excused by Report-Evidence: ${excuse}"
      printf '%s\n' "$detected" | sed 's/^/        /'
      continue
    fi

    violations=$((violations + 1))
    record_count=$(printf '%s' "$detected" | grep -c .)
    echo "FAIL  $(git show -s --format='%h %s' "$commit")"
    echo "      $moving movement-implying line(s) in $record_count record(s), and no change to $REPORT"
    # The sample is the detection pass's own saved lines, so an exempt record (a first
    # generation, a restamp) can never occupy it while being absent from the demanded set.
    printf '%s' "$sample" | grep . | sed 's/^/        /' | head -4
    if ! printf '%s' "$excuse" | grep -q '[^[:space:]]'; then
      echo "      no Report-Evidence trailer. A record moved by hand needs one, plus exactly"
      echo "      these Report-Evidence-Path trailers — the records the audit detected:"
      printf '%s\n' "$detected" | grep . | sed 's/^/        Report-Evidence-Path: /'
    else
      echo "      Report-Evidence is present, but its Report-Evidence-Path set does not match"
      echo "      the records that moved. The audit detected:"
      printf '%s\n' "$detected" | grep . | sed 's/^/        Report-Evidence-Path: /'
      missing=$(comm -23 <(printf '%s\n' "$detected" | grep .) <(printf '%s\n' "$declared" | grep .))
      extra=$(comm -13 <(printf '%s\n' "$detected" | grep .) <(printf '%s\n' "$declared" | grep .))
      [ -n "$missing" ] && { echo "      missing from the trailers:"; printf '%s\n' "$missing" | sed 's/^/        /'; }
      [ -n "$extra" ] && { echo "      declared but did not move:"; printf '%s\n' "$extra" | sed 's/^/        /'; }
    fi
  done <<< "$commits"

  echo
  echo "checked $checked commit(s); $violations without movement evidence"
  [ "$violations" -eq 0 ]
}

# Hook mode's misadoption guard: the pushed tip's tree, never this checkout's index — a
# push can carry refs the checkout has never had (`git push origin otherbranch:main`),
# and the index says nothing about them. A tip with no records is not a consumer state
# and audits trivially (a docs branch is fine to push); a tip carrying records without
# a tracked report is the swallowed-report trap on a branch, and pushing it publishes
# commits whose evidence can never be committed.
tip_guard() {
  local tip="$1"
  # Enumerated and matched with the same case pattern the audit loop uses, because
  # `git ls-tree` does not wildmatch its pathspecs the way `git show` does — handing
  # it the glob directly matches nothing and would wave every tip through.
  local path has_records=''
  while IFS= read -r path; do
    case "$path" in $RECORDS) has_records=1; break ;; *) ;; esac
  done < <("${GIT[@]}" ls-tree -r --name-only "$tip")
  if [ -z "$has_records" ]; then
    return 0
  fi
  if [ -z "$(git ls-tree "$tip" -- "$REPORT" | awk '$1 ~ /^100/ {print $3}')" ]; then
    echo "report-evidence: pushed tip $tip carries channel records but no tracked $REPORT," >&2
    echo "                 so no commit on it can carry movement evidence. Re-include" >&2
    echo "                 $REPORT and $GAP on that branch before pushing it." >&2
    return 2
  fi
  return 0
}

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <range|commit|rev-list arguments...> | --pre-push" >&2
  exit 2
fi

if [ "$1" = "--pre-push" ]; then
  # Hook mode: the lines git feeds a pre-push hook, one per ref being pushed. An audit
  # failure and an infrastructure failure both refuse the push — a gate that waves a
  # push through because it could not look is the failure this script exists to catch,
  # one level up — but they refuse with different words. A range that would not resolve
  # is usually the remote holding commits this clone has not fetched (git itself is one
  # step from saying "fetch first"), and telling that operator to amend a report in
  # would be advice for a violation nobody committed.
  status=0
  infra=0
  badcfg=0
  audited=0
  while read -r _local_ref local_sha _remote_ref remote_sha; do
    # A deletion pushes no commits.
    if [[ "$local_sha" =~ ^0+$ ]]; then
      continue
    fi
    audited=1
    if ! tip_guard "$local_sha"; then
      badcfg=1
      continue
    fi
    if [[ "$remote_sha" =~ ^0+$ ]]; then
      # The remote has no such branch yet, so there is no far end to subtract.
      # Everything this push would publish is everything not already on some remote.
      audit "$local_sha" --not --remotes
    else
      audit "$remote_sha..$local_sha"
    fi
    case "$?" in
      0) ;;
      1) status=1 ;;
      *) infra=1 ;;
    esac
  done

  if [ "$status" -ne 0 ]; then
    cat >&2 <<MSG

pre-push: refusing to push — a commit above moves a generated channel record with no
change to $REPORT beside it, so the generation's movement evidence is not
in the commit. Movement is an event: the next run reports nothing, and then what this
one saw cannot be recovered from anything.

  * the report is probably still in your working tree — \`git add $REPORT
    $GAP\` and \`git commit --amend\`
  * a record moved with no generation behind it (a hand-fixed hash, a package repointed
    at another address) says so on that commit: a \`Report-Evidence:\` trailer carrying
    the why, plus one \`Report-Evidence-Path:\` trailer per moved record — the audit
    output above prints the exact set it expects, ready to paste
  * \`git push --no-verify\` overrides this, and CI will still say so

MSG
  fi
  if [ "$infra" -ne 0 ]; then
    echo >&2
    echo "pre-push: could not audit this push — a range above did not resolve. Usually the" >&2
    echo "          remote has commits this clone has not fetched; fetch/rebase and retry." >&2
    echo "          Refusing rather than passing unaudited; --no-verify overrides." >&2
    echo >&2
  fi
  if [ "$badcfg" -ne 0 ]; then
    echo >&2
    echo "pre-push: refusing to push — a pushed tip above carries channel records without a" >&2
    echo "          tracked movement report (details above). --no-verify overrides." >&2
    echo >&2
  fi
  [ "$audited" -eq 1 ] || exit 0
  if [ "$status" -eq 0 ] && { [ "$infra" -ne 0 ] || [ "$badcfg" -ne 0 ]; }; then
    exit 2
  fi
  exit "$status"
fi

# Range mode's misadoption guard is the current checkout's index — CI checks out the
# very ref it audits, and a local range audit runs where the corpus lives — so a hard
# error here, never a quiet pass. A repository with no tracked records has nothing for
# this audit to protect, and an untracked report cannot carry evidence into any
# commit: glam-sdk-java's whitelist .gitignore swallowed both report files at its
# 2026-08-20 record commit (the three earlier record commits predate the generator
# writing a report by default and carried none to lose), and the movement that cost is
# what this guard names. Hook mode guards each pushed tip's tree instead (tip_guard
# above), because a push can carry refs this checkout does not have.
if [ -z "$(git ls-files -- "$RECORDS" | head -n 1)" ]; then
  echo "report-evidence: no tracked records match '$RECORDS'." >&2
  echo "                 Wrong repository, or REPORT_EVIDENCE_RECORDS does not fit its layout." >&2
  exit 2
fi
if ! git ls-files --error-unmatch -- "$REPORT" >/dev/null 2>&1; then
  echo "report-evidence: $REPORT is not tracked, so no commit can carry movement evidence." >&2
  echo "                 A whitelist .gitignore swallows it silently; re-include $REPORT and" >&2
  echo "                 $GAP, commit both, and re-run." >&2
  exit 2
fi

# One bare commit means that commit alone. Without this it would read as "everything
# reachable from it", which on a mature branch audits the entire history every time.
if [ "$#" -eq 1 ]; then
  case "$1" in
    *..*|-*) ;;
    *) set -- "$1^!" ;;
  esac
fi

audit "$@"

# Capture runbook (P1-F1 to P1-F4)

A capture is a recording of what the radios and GPS reported, made by a **debug build** (ADR-0016). Raw captures hold bystander data, so they're scrubbed (ADR-0017) before they're kept. Only scrubbed parts are stored, in the private repo `rlhooks13/spectre-corpus`. Raw files are deleted.

## 1. Record (on the dedicated phone, debug build)
```
adb -s $SERIAL shell run-as io.github.rlhooks13.spectre.debug mkdir -p files/capture
adb -s $SERIAL shell run-as io.github.rlhooks13.spectre.debug touch files/capture/ENABLED    # start
#   ... scan: walk, drive, or leave reference tags beside the phone ...
adb -s $SERIAL shell run-as io.github.rlhooks13.spectre.debug rm files/capture/ENABLED       # stop
```
The recorder sees the stop within 3 s and flushes every second. **Wait 10 s** before pulling. Parts are `files/capture/capture-<wallMs>-<part>.ndjson`, up to 8 MB each and 64 MB in all.

## 2. Pull into a new empty folder outside both repos, and verify it
```
RAW=$HOME/captures/raw-$(date +%s); mkdir -p $RAW
adb -s $SERIAL exec-out "run-as io.github.rlhooks13.spectre.debug tar cf - -C files/capture --exclude ENABLED ." | tar xf - -C $RAW
adb -s $SERIAL shell "run-as io.github.rlhooks13.spectre.debug sh -c 'cd files/capture && sha256sum capture-*.ndjson'"
(cd $RAW && sha256sum capture-*.ndjson)    # must match line for line
```
- If the sums differ, pull again.
- Don't delete anything on the phone yet.

## 3. Scrub, leak-check and store
```
git clone https://github.com/rlhooks13/spectre-corpus ~/spectre-corpus   # once; mkdir -p ~/spectre-corpus/captures
cd ~/spectre
./gradlew testDebugUnitTest --tests '*CaptureScrubTool*' -PscrubIn=$RAW -PscrubOut=$HOME/spectre-corpus/captures
```
- **What the tool checks first:** both paths must be absolute. The output must already exist inside the corpus clone. Neither folder may be inside the Spectre checkout or inside the other. The raw folder may hold only capture parts.
- **Leak check:** each part is scrubbed with its own secret and leak-checked against the identifiers of every part.
- **If any part leaks,** the tool fails and writes nothing.
  - The test report gives counts only ("part 2: 1 name").
  - The values go to `$RAW/LEAKS.txt`, beside the raw files. Read it only to fix the scrubber; never paste it anywhere.
  - Fix the scrubber (a P1-F2 follow-up) and run again. Don't hand-edit a raw capture.
  - A raw folder waiting on a fix is deleted within 7 days, fixed or not.
- **If every part passes,** the tool writes them to a temporary `.tmp-<id>` folder, renames it to `captures/<random id>/part-NNNN.ndjson` (in time order, no timestamps in names), and deletes the raw files.
  - A `.tmp-*` folder left by a crash holds incomplete data: delete it and don't commit it.

## 4. Commit to the corpus
```
cd ~/spectre-corpus && git status --short    # only captures/<id>/ is new; no .tmp-*
git add captures/<id>
GIT_AUTHOR_DATE="$(date -u +%Y-%m-01)T00:00:00Z" GIT_COMMITTER_DATE="$(date -u +%Y-%m-01)T00:00:00Z" \
  git commit -m "Add a scrubbed capture." && git push
```
- **Commit dates** are coarsened to the first of the month, so a commit doesn't reveal when the capture was made.
- **Scenario notes** go next to the parts as `captures/<id>/notes.md`. Examples: "no-tail commute", or "AirTag, SmartTag and Tile beside the phone for 24 h".
  - Ground truth for field-capture issues is written by the owner, never inferred.
  - Never note a place, a street, a time of day or a person.

## 5. Delete the raw capture everywhere (after the push)
```
adb -s $SERIAL shell run-as io.github.rlhooks13.spectre.debug rm -rf files/capture
rmdir $RAW
```

## Who does what
- **Recording and ground truth:** the owner.
- **Scrub and store:** the agent may run steps 2–5 for its own device checks. They use no shell redirection; step 2 streams through `tar`. If a permission rule blocks a step, the agent asks for it in a `needs-human` issue, as CLAUDE.md says.

## Rules
- **Raw only on the phone or in `~/captures/raw-*`:** a raw capture never goes into either git repo, into a PR or issue, or into a chat.
- **The corpus repo stays private.** Even scrubbed, a route's shape can be map-matched (ADR-0017, Residual risk).
- **The agent doesn't read capture contents.** It counts records by type, and reads tool reports, which hold counts only.

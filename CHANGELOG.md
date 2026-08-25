# Changelog

## 0.2.5 · seamless updates and phase formulas

- Adds an Android signing-certificate lineage so existing debug-signed installations can migrate to the stable release certificate without uninstalling or losing local data.
- Shows the exact recorded turn sequence beside each C / F1 / F2 / F3 / F4 / OLL / PLL stage in deep review, while clearly marking inferred recovery moves and zero-move stage boundaries.

## 0.2.4 · stable level, richer review and custom practice

- Shows recognition-matched OLL / PLL recommendations directly in deep review with the formula, current-state diagram and smooth embedded 3D playback.
- Separates CTSS-1.2 long-term reproducible level from short-term form, replaces ambiguous phase bars with interval rulers and evidence milestones, and adds clearly labelled offline WCA average-rank comparison bands.
- Displays current and best ao5 / ao12 together, keeping the latest 12-result timeline equally spaced.
- Adds validated custom scrambles to Timer while preserving the connected smart-cube scramble, inspection and automatic timing workflow.
- Keeps ranking comparison and all personal analysis fully offline; no solve or identity is sent to WCA.

## 0.2.3 · steadier guidance and repeatable practice

- Draws the latest valid results at equal horizontal intervals while retaining real date/time labels on the records timeline.
- Accepts state-equivalent same-face smart scrambles across packets, including two inverse quarter turns for a half turn and three inverse quarter turns for a clockwise turn, without accepting a different face.
- Reduces CTSS-1.1 state noise and outlier influence, and delays recent-form conclusions until 12 reliable smart solves.
- Adds “重新练习这次打乱” to solve review, lets the user explicitly save or discard a pending smart result, and starts the repeated scramble as a fresh solve with a unique record id while retaining current smart-cube settings.

## 0.2.2 · resilient replay and actionable review

- Reconstructs a single dropped V10 face turn only when the device counter identifies one missing step and exactly one of the 12 quarter turns reproduces the saved final checkpoint; the raw recording remains unchanged, inferred timing is excluded from CTSS/TPS/pause evidence, and confidence is reduced.
- Adds recognition-backed OLL / PLL formula references, evidence-based start groups and clearly labelled formula-practice finger cues to personal solve coaching.
- Rebuilds the recent-results chart with a real date/time axis, actual temporal spacing, fastest/latest markers and accessible summary text.
- Refines the pause threshold to 25 ms increments, including an exact 250 ms setting, and persists it only after the user finishes dragging.

## 0.2.1 · explainable feedback and records performance

- Added structured single-solve coaching with one primary and up to two secondary observations, personal baselines, evidence ranges, confidence and suggested drills.
- Added a plain-language current CFOP assessment and next-step recommendation to the records card and level details.
- Froze ao5 / ao12 PB targets when the smart scramble matches and kept them visible through manual or automatic inspection until the first solve move.
- Added the user setting for showing record-chase hints.
- Moved CTSS and solve-review analysis off the Compose main thread, shared one cached dashboard result, changed the history list to lazy composition, and replaced per-solve move queries with bounded batch reads.
- Expanded phase details with time share, practical/active TPS and longest pause.
- Preserved the final elapsed time in the stopped summary and prevented reference-frame changes from resetting an active smart solve.

### Included replay corrections

- Added ReplayValidation 2.0.3 with explicit failure codes and first-evidence C / F1–F4 / O / P boundaries; all six cross faces are evaluated and the strongest independently observed F2L-to-OLL chain selects the solve face.
- Accepted multi-move A5 bursts by validating counter distance against physical move count, while assigning an individual reconstructed counter to each newly recorded burst move.
- Added explicit per-solve CFOP details, including detected bottom color, phase time, move count, TPS and pause rate, to the history review dialog.
- Added additive database migration for solve checkpoints, sequence evidence and device/receive timing quality.
- Recorded accepted V10 A5 packets directly from the protocol layer, including burst moves and explicit gaps; Compose snapshots no longer define solve facts.
- Incomplete or unavailable phase metrics now render as unavailable instead of synthetic zeroes and are excluded from CTSS-1.
- Kept the solve wall-clock anchor after clearing the previous recording, and applied one reliability weight to phase time, moves and pauses.
- Kept the first observed boundary when a later turn changes a previously seen slot or stage, without showing a misleading regression warning or lowering confidence.

## 0.2.0 · offline analysis iteration

- Smart-cube solves now retain normalized move events for replay and analysis.
- Added post-solve HTM / practical TPS / pause-rate summary and deep-analysis entry.
- Added C / F1 / F2 / F3 / F4 / O / P phase metrics with incomplete-data downgrade.
- Added pre-solve ao5 / ao12 PB targets and a CTSS-1 current reproducible-level card.

## 0.1.0 · development baseline

- Created the offline Android Compose app shell.
- Added Formula / Training / Timer / Records navigation.
- Added executable move parser, cube facelet state, scramble generator and generated case catalog.
- Added local SQLite persistence, DataStore settings, statistics and `.cubetrace.zip` export.
- Added V10 AI protocol boundary with AES packet wrapper, decoder, quaternion continuity and safe BluetoothGatt command queue.
- Marked reviewed CFOP content and real-device V10 validation as pending.

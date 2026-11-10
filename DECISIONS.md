# DECISIONS — Flexi-Rate Usage Calculator (v3, revised 2026-11-09)

The spec says it is "realistic, which means imperfect". Every ambiguity below was decided
deliberately and is covered by a test. Each decision carries a **Status**:

- **Confirmed** — stated in the task text or confirmed in writing by Velislava / Viktor (source and date given).
- **Assumption** — our documented rule for a point the employer has not confirmed. Never presented as an employer requirement.
- **Recommended** — our own engineering choice where the employer left the design to us.

We do not wait for pending answers (replies are promised "within one working day"; the deadline
is 2026-11-10 08:00 EST). Each Assumption is isolated in one place in the code so it can be
flipped quickly if an answer contradicts it. Pending answers at the time of writing:
(1) billing-precision/rounding-layer question to Velislava; (2) overlapping-events question to Viktor.

| # | Topic | Decision | Status | Basis / source |
|---|-------|----------|--------|----------------|
| 1 | Timezone & cycle attribution | ALL logic is UTC-based. Peak is UTC [09:00, 17:00); cycle attribution in UTC; the user's timezone is used only to convert offset-less timestamps to UTC | **Confirmed** | Velislava, doc comments 2026-11-09 08:46: "All the logic uses limits based on the UTC time…" (This reversed our original assumption of cycle attribution in the user's timezone — see "AI mistakes caught") |
| 2 | Billing unit model & peak split | Events are pre-aggregated records made of consecutive 15-minute units; each unit is evaluated independently against grace / tier progression / peak; an event spanning 17:00 prices its units on each side at their own rate | **Confirmed** | Viktor, email 2026-11-09 11:54: units are pre-aggregated; "15 minutes is charged at base rate, 15 at peak usage rate" |
| 3 | Alignment & duration validity | Start must align to a UTC quarter hour (checked after UTC conversion) and duration must be a positive multiple of 15 minutes; violating either = invalid event (ERROR) | **Confirmed** | Velislava 08:49 (quarter-hour starts; non-aligned = invalid) + Viktor 11:54 (duration not a multiple of 15 = invalid; sub-15-minute usage is "not qualified" and such cases are counted in the output header) |
| 4 | Tier pricing shape | Marginal pricing: usage is priced per tier range (0–100h @ $1, 100–500h @ $0.80, 500h+ @ $0.50), not flat at the highest reached rate | Assumption | Reading of the task's tier table (ranges given, no explicit "marginal" wording); the industry-standard meaning of such tables |
| 5 | Enterprise grace | Grace hours are billed $0.00 but DO advance tier progression for enterprise users (they consume tier_1 capacity: only 90 further hours at $1.00) | **Confirmed** | Task text rule 3: for enterprise, grace hours "MUST count toward tier progression, but should still be billed at $0.00" |
| 6 | Grace + peak interaction | Grace units never incur the peak surcharge (a $0.00 unit stays $0.00) | Assumption | Surcharge is defined as 20% on top of the base tier rate; the grace rate is $0.00 |
| 7 | Error count location & skipped-item log | Top-level `header.error_count` next to `results`, plus every skipped item logged with its reason (`header.skipped_items`, and one stderr line per item in the CLI) | **Confirmed** | Velislava 08:56, confirming the exact envelope `{ "header": { "error_count": n }, "results": [ … ] }` and adding the per-item reason log; Viktor (Q2) likewise: mark the count of invalid cases in the header |
| 8 | Output shape for tier_breakdown / applied_surcharges | Fixed objects (hours / rate / cost per tier incl. a grace line; peak_hours / peak_rate / peak_surcharge_total for surcharges), documented in the README; the numbers must add up to total_cost | **Confirmed** (shape is ours to choose) | Velislava 08:51: "Any clear structure is fine, as long as the numbers add up… Document the shape in the README" |
| 9 | Money & rounding layer | BigDecimal; unit costs are exact ($0.25 / $0.20 / $0.125, ×1.2 peak); each output line is rounded HALF_UP to cents and total_cost = sum of the rounded lines + rounded surcharge (so the breakdown always adds up) | Assumption | Precision question to Velislava is still pending; this policy satisfies her confirmed "numbers must add up" requirement and is isolated in one method |
| 10 | Duplicate event_id | Per (user_id, event_id), in input order, after ERROR checks: identical payload → billed once, extra copies WARNING (same record delivered twice must not double-bill); different payload → both billed, later one WARNING | Assumption | Ruled by Sam 2026-11-09 after Muse vs ChatGPT positions were compared (ChatGPT: warn, never discard; Muse: first-wins skip; the refined rule splits on payload identity). Not an employer-confirmed rule |
| 11 | Missing / blank event_id | Invalid event (ERROR): skipped, counted, logged with reason `missing event_id`; skipped_items records event_id as null | Assumption | event_id is a required field in the task's input definition and is needed for duplicate detection and per-item logging |
| 12 | Events straddling a cycle boundary | Attribution is per 15-minute unit: units on each side of the boundary belong to their own cycle's bill | Assumption | Built on two Confirmed rules (all-UTC #1, unit model #2); the employer has not addressed cross-month events explicitly |
| 13 | DST gap / overlap for offset-less timestamps | Strictly reject: a local time with 0 valid offsets (nonexistent) or 2 valid offsets (ambiguous) is an ERROR with reason `nonexistent local time` / `ambiguous local time` — we never guess an instant | Assumption | Adopted from the Sam + ChatGPT discussion 2026-11-09, replacing Muse's earlier atZone-defaults proposal (see "AI mistakes caught") |
| 14 | Invalid user records | A user with missing/blank user_id, missing/invalid timezone, or missing/non-boolean is_enterprise is rejected (no result row); their events fail as unknown-user ERRORs. Production refinement noted: a user with an invalid timezone could still have offset-bearing events billed, since those do not depend on the user's timezone; the current all-or-nothing rejection is a conservative simplification. | Assumption | Timezone and is_enterprise both directly determine money; guessing defaults would misbill silently |
| 15 | Overlapping events (same user) | Overlapping events for the same user are ordinary concurrent usage (API usage is parallel by nature): every valid event is billed in full and independently; overlaps raise no warning and intervals are never merged. Warnings are reserved for data-quality anomalies (duplicate event_id). Raised with Viktor by email 2026-11-09; no reply as of this writing — assumption stands. | Assumption | API calls and batch workers run concurrently; merging or warning would penalize legitimate parallel workloads. |
| 16 | Events outside the requested cycle | Ignored silently (per unit, #12); they are valid records, just not this cycle's — but an event that is *invalid* is counted as an ERROR regardless of cycle, because validation precedes cycle attribution in the pipeline | Assumption | Pipeline order defined in SPEC §3.1 |
| 17 | Users with no (valid) events in the cycle | Still returned with total_cost 0.00 | Assumption | Task text: "For each user, return a JSON object…" |
| 18 | peak_hours & tier hours definitions | peak_hours = hours that actually incurred the surcharge (grace peak units excluded); tier_breakdown hours = hours actually billed at that tier, grace hours shown only on the grace line (enterprise progression is an internal counter, never double-counted) | Assumption | Definitions within our confirmed-free output shape (#8); stated in SPEC §5 and the README so a reviewer can audit the arithmetic |
| 19 | Unknown JSON fields | Ignored at every level (mapper configured accordingly — Jackson fails on unknown fields by default) | **Recommended** | Forward compatibility of a production input contract |
| 20 | Framework & shape of the program | Plain Java 21 + Jackson + JUnit 5, Maven build with the Maven Wrapper committed; pure calculator core + thin CLI | **Recommended** | The deliverable says "a function or service"; this is the smallest production-honest, walkthrough-able shape |
| 21 | Session log format | FULL, uncropped transcripts of all AI interaction for this task, self-packaged by each AI (see SESSION_LOG.md), with SESSION_LOG.md as the index | **Confirmed** (requirement) | Velislava 08:57: "I want to see how you work with AI. The more you show, the better I can judge. If you crop it, I will decide you are hiding something." Roles and packaging arrangement set by Sam 2026-11-09 |

## AI mistakes caught during the work
_(This section is part of the interview defence: the errors, who caught them, and the fix.)_
- **Muse (manager)**: DECISIONS v1 attributed the billing cycle in the *user's timezone*. Velislava's answer (2026-11-09 08:46) reversed it — all logic is UTC. Corrected in v2 before any code was written.
- **Muse (manager)**: DECISIONS v2 claimed quarter-hour-aligned events "never trigger peak-boundary splitting" — wrong: alignment constrains the *start*, a 60-minute event starting 16:30 still spans 17:00 (SPEC Example F). Caught by ChatGPT's engineering review (2026-11-09); resolved by Viktor's unit model: units never straddle, events may span, units are priced independently.
- **Muse (manager)**: SPEC v1/v2 claimed "with 15-minute chunks all values are exact cents anyway" while also allowing durations that are not multiples of 15 — internally contradictory, and still imprecise after Viktor's ruling because a tier_3 unit is $0.125. Caught by ChatGPT's review; fixed by the explicit rounding policy in #9.
- **Muse (manager)**: proposed atZone DST defaults (silently shifting nonexistent times / picking the earlier offset) and whole-event cycle attribution by start instant. Both were superseded in the Sam + ChatGPT discussion by stricter, more defensible rules (#13 strict rejection, #12 per-unit attribution) and Muse adopted theirs.
- **Process note**: Sam's instruction "proposal first, no file edits until I confirm" was followed for this v3 revision — the proposal, Viktor's confirmations, and Sam's rulings (incl. the duplicate policy) all predate these edits.
- **Muse (manager)**: SPEC §6 Example B (enterprise, 110h) stated a total of $90.00. That figure silently dropped the 10 billed hours that spill into tier_2 once grace consumes 10h of tier_1 progression (90h × $1.00 + 10h × $0.80 = $98.00), and it violated SPEC §5's tier-hours invariant (10 + 90 + 0 ≠ 110). Present since SPEC v1; ChatGPT's engineering review did not catch it either. Exposed on 2026-11-09 when Antigravity's implementation produced $98.00 and exactly the two Example B tests failed (57 run, 2 failures) — while Example D, the same ladder at 510h, already agreed with the corrected arithmetic ($415) and passed. Fixed by errata E6; the rule (#4, #5) is unchanged.


## Test errata (2026-11-09)

Suite counts progression:
- Before errata: 57 tests run / 23 failures (T2 state, pricing stubbed).
- T3 initial draft run: 57 tests run / 14 failures (all 14 attributable to T1 test encoding contradictions and outdated assertions).
- After E1–E5 errata, with the T3 implementation: 57 tests run / 2 failures (both Example B — see E6).
- After E6: 57 tests run / 0 failures.


| Errata | Test(s) | Problem | Fix | Business expectation changed? |
|---|---|---|---|---|
| E1 | `exampleA`, `exampleB`, `enterpriseVsNonEnterprise` (both), `invariant_tierHoursSum_exampleA` (110h pack); `tierBoundary_111h`; `staysInTier1_99h`; `exampleC` (all peak) | Continuous single events crossing weekday 09:00–17:00 UTC windows incurred peak surcharge, contradicting "all off-peak" or "all peak" premises. | Re-encoded off-peak tests as nightly 17:00–09:00Z blocks (sum preserved: 6600, 6660, 5940 min). Re-encoded Example C as three daily peak blocks (09:00Z ×480, ×480, ×240; sum = 1200 min). | No |
| E2 | `exampleD`, `exampleE`, `exampleD_breakdownInvariant`, `invariant_tierHoursSum_exampleD`, `tierBoundary_511h`, `staysInTier2_499h`, `exampleI`, `exampleI_breakdownInvariant` | Off-peak hours needed exceed single-month off-peak capacity (480h). | Base stream (480h) plus parallel off-peak streams (concurrent API usage per #15). Setup sum for D/E: 30600 min (510h); 511h: 30660 min; 499h: 29940 min; Example I: 30615 min. | No |
| E3 | `exampleC`, `exampleF_peakBoundarySplit` | `tier_1.cost` asserted as $12.00 (C) and $1.10 (F), including peak surcharge, contradicting SPEC §5 invariant (`sum(tier costs) + surcharge = total_cost`). | Updated assertions so tier line carries base cost only: `tier_1.cost` 12.00 → 10.00 (C) and 1.10 → 1.00 (F). | No |
| E4 | `graceBoundaryPlusOneUnit` | Event started at 00:00:00Z with duration 615 min, pushing the 41st unit to 10:00Z (inside peak window). | Changed timestamp to `2026-11-02T17:00:00Z` so all units remain off-peak. | No |
| E5 | `warning_overlappingEvents_bothBilled_warningOnLater` | Test asserted overlap warning on later event. | Renamed to `overlappingEvents_billedAsConcurrentUsage_noWarning`; asserted `warning_count = 0` per revised DECISIONS #15. | Yes — policy revision, see #15 |
| E6 | `exampleB_enterprise_110h_offPeak_total90` (renamed `…total98`), `enterpriseVsNonEnterprise_110h_contrast` | SPEC §6 Example B's expected total ($90.00) omitted the 10 billed hours that spill into tier_2 once the 10 grace hours consume tier_1 progression — the example's own hours did not add up (grace 10h + tier_1 90h + tier_2 0h = 100h ≠ 110h of usage), violating the SPEC §5 tier-hours invariant | Corrected the expected total to $98.00 (tier_1 90h × $1.00 + tier_2 10h × $0.80), asserted tier_2 = 10h / $8.00, and corrected the SPEC §6 Example B text to match | No — the pricing rule is unchanged; the example's arithmetic now matches it (cf. Example D, $415, which was already consistent) |

## Robustness fixes (2026-11-10)

After the first full build, a critical re-review of the code (no pricing errors
found — the per-unit core, rounding and invariants all held) produced four
input-robustness fixes and one efficiency fix, each landed green and locked by
the new RobustnessTest suite (13 tests; 74 total):

- A malformed billing_cycle now fails as a clean FATAL
  ("Invalid billing_cycle: ...") instead of an uncaught parse exception.
- A duration_minutes of the wrong type (fractional, non-numeric string,
  out-of-range) now skips only its own event (reason
  "missing or invalid duration_minutes") instead of failing the whole
  document at binding time — the same treatment is_enterprise already had.
- Null elements in the users/events arrays can no longer crash the run
  (a null event counts as a skipped item with reason "missing event_id").
- Duplicate user_id entries: the first valid entry wins consistently for
  both timestamp conversion and pricing (previously the map and the bill
  could read different entries).
- Events are clipped to the billed cycle before unit expansion, so work is
  proportional to in-cycle units. (An earlier automated attempt at this
  change inverted the clipping arithmetic; it was caught in review before
  ever being committed — see SESSION_LOG.md.)

Complexity and memory: parsing currently materialises the full JSON tree plus
the bound model (Jackson databind), so memory grows with document size;
pricing expands in-cycle units per user (at most 2,976 quarter-hours per
event stream per month, multiplied by concurrency). The production path
addresses both axes: stream the input with Jackson's streaming JsonParser —
already a dependency, the Java analogue of Python's ijson — making parse
memory O(1) in document size, and replace unit materialisation with segment
arithmetic counting so pricing memory scales with event count rather than
unit count.

# SPEC — Flexi-Rate Usage Calculator (v3, revised 2026-11-09 after employer clarifications + engineering review)

## 1. Goal
Java 21+ program that reads a JSON document (users, events, optional billing_cycle "YYYY-MM")
and returns, for each user, the total billing cost for that cycle under the Flexi-Rate rules.
Treat as production code: deterministic, money-safe (BigDecimal), tested, documented.

Deadline in the task document: **10.11.2026, 8:00 AM EST** (Tue 2026-11-10 08:00 America/New_York).
Expected effort 3–4 hours. AI tools allowed; prompts/session log must be submitted (see SESSION_LOG.md).
Rule provenance is tracked in DECISIONS.md with a Status per decision:
**Confirmed** (task text or a written employer answer) vs **Assumption** (our documented rule,
employer has not confirmed it) vs **Recommended** (our engineering choice).

## 2. Input
```json
{
  "users":  [ { "user_id": "u1", "is_enterprise": false, "timezone": "America/New_York" } ],
  "events": [ { "event_id": "e1", "user_id": "u1",
                "timestamp": "2026-11-03T08:45:00Z", "duration_minutes": 30 } ],
  "billing_cycle": "2026-11"
}
```
- **Unknown JSON fields are ignored** at every level (forward compatibility). Note: Jackson's
  default is to FAIL on unknown properties — the mapper must be configured otherwise (see T2).
- `timestamp`: ISO-8601. If it carries an offset/`Z`, parse to that Instant. If it is a local
  date-time with no offset, interpret it in the user's `timezone` and convert to UTC
  (Confirmed, Velislava 2026-11-09: "When the user sends a timestamp in their time zone, you
  need to convert it to UTC and then apply the logic.").
  - **DST strictness (Assumption)**: an offset-less local time that is *nonexistent* (spring-forward
    gap) or *ambiguous* (fall-back overlap) is INVALID — we never guess an instant.
    Detect via `ZoneRules.getValidOffsets()`: exactly 1 valid offset = OK, 0 or 2 = invalid.
- A **user record is rejected** (Assumption) if `user_id` is missing/blank, `timezone` is missing
  or not a valid zone id, or `is_enterprise` is missing or not a boolean. A rejected user produces
  no result row; that user's events then fail as unknown-user ERRORs (see §4).
- `billing_cycle` omitted: bill the *current* cycle, i.e. the current YearMonth in UTC.
- **One invocation bills exactly one cycle and produces exactly one output document.**
  Events spanning several months do not produce several outputs: only the requested
  cycle's units are billed in that run (see §3.2). Billing N cycles = N invocations,
  matching how a monthly billing job actually runs.

## 3. Calculation rules
**Unit model (Confirmed, Viktor 2026-11-09)**: events are *pre-aggregated* usage records, not raw
API sessions. Each valid event consists of consecutive **15-minute billing units**; every unit is
evaluated independently against the grace, tier-progression and peak rules. The calculator does
not reconstruct or prorate raw usage.

1. **Pipeline order**: parse (FATAL on failure, §4) → validate users → validate events
   (incl. duplicate policy) → per user, sort events chronologically (timestamp, then event_id)
   → expand events into 15-minute units → attribute each unit to a billing cycle → price the
   units that fall in the requested cycle, in chronological order.
2. **Cycle attribution is per unit and all-UTC (Confirmed basis, Velislava: "All the logic uses
   limits based on the UTC time")**: a unit belongs to the cycle of its start instant in UTC.
   An event straddling a month boundary contributes its units to two different cycles;
   units outside the requested cycle are ignored (not errors). Out-of-cycle units also do
   **not** consume grace and do **not** advance tier progression: grace and progression are
   computed per run, over the requested cycle's units only — so the same input billed for
   2026-10 and for 2026-11 gives each month its own fresh 10 grace hours and its own tier
   ladder, and the straddling event's units are billed exactly once across the two runs.
   *(Per-unit attribution itself is our Assumption built on the confirmed UTC + unit
   rules — DECISIONS #12.)*
3. **Grace period**: the first 10 hours (= 40 units) per user per cycle are billed $0.00.
   - Non-enterprise: grace units do **not** advance tier progression.
   - Enterprise: grace units **do** advance tier progression, still billed $0.00, no surcharge
     (Confirmed, task text: grace hours "MUST count toward tier progression" for enterprise).
4. **Tiers (marginal, on tier-progression units)**: progression 0–100h @ $1.00/h,
   100–500h @ $0.80/h, 500+h @ $0.50/h. Grace and tier boundaries (10h / 100h / 500h) all fall
   on unit edges, so no unit ever needs splitting.
5. **Peak surcharge**: a unit whose UTC start is in [09:00, 17:00) UTC is a peak unit and costs
   base tier rate × 1.20 (Confirmed split behaviour, Viktor: "15 minutes is charged at base
   rate, 15 at peak usage rate" — an event spanning 17:00 prices its units on each side
   at their own rate). The surcharge portion (20% of the unit's base cost) is accumulated separately.
6. **Per-unit pricing**: for each unit in order — if grace remains, the unit is a grace unit
   (cost 0; enterprise also advances progression). Otherwise the unit's tier is read from the
   progression counter, `unit cost = 0.25h × tier rate × (peak ? 1.20 : 1.00)`, and progression
   advances by one unit.
7. **Money and rounding (Assumption — precision question to Velislava still pending)**:
   BigDecimal throughout; with 15-minute units every unit cost is exact
   ($0.25 / $0.20 / $0.125, ×1.2 at peak), so internal sums are exact and unrounded.
   At output, each tier line's cost and the surcharge total are rounded HALF_UP to 2 decimals,
   and **`total_cost` = the sum of the rounded lines + the rounded surcharge**, so the
   breakdown always adds up exactly to the total (Velislava's confirmed requirement).
   The only value that can ever carry a half-cent is a tier_3 total (odd number of tier_3
   units × $0.125); rounding is isolated in one place so a different employer ruling can be
   applied by changing one method.

## 4. Validation and error levels
Three severity levels. `error_count` counts **ERROR items only** (this is the count the task
and Viktor refer to: invalid items skipped, and "mark in the header how many such cases").

### ERROR — item invalid, skipped, counted in `error_count`, logged in `header.skipped_items` with a reason
An event is an ERROR if any of:
- missing/blank `event_id` (Assumption; the skipped_items entry then records `event_id: null`
  and the CLI stderr line carries the item's input index);
- missing/blank `user_id`, or `user_id` not among the *valid* users (incl. users rejected in §2);
- missing/unparseable `timestamp`;
- offset-less timestamp in a DST gap or overlap (§2);
- missing `duration_minutes`, or `duration_minutes <= 0`;
- **start not aligned to a UTC quarter hour** — after conversion to UTC, minute not in
  {00, 15, 30, 45} or non-zero seconds (Confirmed, Velislava + Viktor);
- **`duration_minutes` not a multiple of 15** (Confirmed, Viktor: "treat any event invalid
  whose duration is not a multiple of 15"). Since start and duration are both quarter-aligned,
  the end is quarter-aligned too.

### WARNING — data billed (or deduplicated) but anomalous; counted in `warning_count`, logged in `header.warnings` with a reason; never in `error_count`
- **Duplicate `event_id` (Assumption, refined policy ruled by Sam 2026-11-09)**, scoped per
  (user_id, event_id), evaluated in input order after an event passes all ERROR checks:
  - same id, **identical** payload (timestamp + duration): the record is billed **once**;
    extra copies are not billed and each logs a WARNING `duplicate event_id, identical payload`
    (the same record delivered twice must not double-bill);
  - same id, **different** payload: **both** records are billed; the later one logs a WARNING
    `duplicate event_id, different payload` (two usages that happen to share an id).
- **Overlapping events (Assumption — interim default; question sent to Viktor, reply pending)**:
  for the same user, an event whose interval intersects an earlier billed event's interval is
  billed **in full, independently**, and logs a WARNING `overlaps event <id>`.

### FATAL — the document itself cannot be processed
Malformed top-level JSON, or `users`/`events` missing or not arrays: no output JSON is produced;
the CLI exits non-zero with a clear stderr message. A FATAL is never disguised as item errors.

The CLI prints one stderr line per skipped item and per warning, each with its reason.

## 5. Output
```json
{
  "header":  { "error_count": 1, "warning_count": 1, "billing_cycle": "2026-11",
               "skipped_items": [ { "event_id": "e7", "reason": "start not on a quarter hour" } ],
               "warnings":      [ { "event_id": "e9", "reason": "duplicate event_id, identical payload" } ] },
  "results": [
    {
      "user_id": "u1",
      "total_cost": 123.45,
      "tier_breakdown": {
        "grace":  { "hours": 10.00, "rate": 0.00, "cost": 0.00 },
        "tier_1": { "hours": 90.00, "rate": 1.00, "cost": 90.00 },
        "tier_2": { "hours": 0.00,  "rate": 0.80, "cost": 0.00 },
        "tier_3": { "hours": 0.00,  "rate": 0.50, "cost": 0.00 }
      },
      "applied_surcharges": { "peak_hours": 0.00, "peak_rate": 0.20, "peak_surcharge_total": 0.00 }
    }
  ]
}
```
Definitions and invariants (shape is ours to define — Confirmed, Velislava; document it in the README):
- `header.billing_cycle` echoes the cycle that was actually billed: the requested
  `billing_cycle` from the input, or — when the input omitted it — the resolved current
  YearMonth in UTC. The per-user `results` rows do not repeat it; the cycle is a
  document-level value and lives in the header, next to the counts.
- One result per **valid** user, in input order, including users with zero billable usage (0.00).
- `tier_breakdown.<tier>.hours` = hours **actually billed at that tier**. The `grace` line's
  hours = free grace hours consumed. Enterprise grace advances the internal progression
  counter only — it never also appears in `tier_1.hours` (no double counting).
- Invariant: grace.hours + tier_1.hours + tier_2.hours + tier_3.hours = total valid usage
  hours attributed to this cycle for this user.
- `applied_surcharges.peak_hours` = hours that **actually incurred** the surcharge (peak units
  billed at a tier; grace peak units excluded — they incurred nothing).
- tier line cost = hours × rate (base only; peak surcharge is reported separately in applied_surcharges).
- Invariant: sum of tier line costs + peak_surcharge_total = total_cost (guaranteed by §3.7).

## 6. Worked sanity examples (must be reproduced by tests)
- **A. Non-enterprise, 110h, all off-peak**: grace 10h free (no tier progress) + tier_1 100h × $1 = **$100.00**.
- **B. Enterprise, 110h, all off-peak**: grace 10h free but counts toward progression → tier_1 90h × $1 = $90 + tier_2 10h × $0.80 = $8 → $98.00 (Errata E6)**$98.00**
  (tier_1.hours = 90, grace.hours = 10 — the grace hours are not in tier_1.hours).
- **C. Non-enterprise, 20h all at peak**: grace 10h free, tier_1 10h × $1 × 1.2 = **$12.00**,
  peak_surcharge_total = $2.00, peak_hours = 10.
- **D. Enterprise, 510h off-peak**: grace 10 (counts) + tier_1 90×$1 + tier_2 400×$0.8 + tier_3 10×$0.5 = **$415.00**.
- **E. Non-enterprise, 510h off-peak**: grace 10 (not counted) + tier_1 100×$1 + tier_2 400×$0.8 = **$420.00**, tier_3 = 0h.
- **F. Unit split at the peak boundary**: one event 16:30Z, 60 minutes, after grace is used up,
  at tier_1: units 16:30/16:45 are peak (30 min × $1 × 1.2 = $0.60), units 17:00/17:15 are
  off-peak (30 min × $1 = $0.50) → **$1.10**. (Viktor's "15 at base, 15 at peak", per unit.)
- **G. UTC conversion + cycle**: event `2026-10-31T23:30` from a user in America/New_York
  (= 2026-11-01T03:30Z) belongs to cycle **2026-11**, not 2026-10.
- **H. Alignment/duration validity**: an event starting `10:07Z`, or with duration 10 or 20
  minutes, is an ERROR — skipped, error_count +1, listed in skipped_items with its reason.
- **I. Rounding anchor (policy of §3.7)**: a single tier_3 off-peak unit costs $0.125 → its
  line rounds HALF_UP to **$0.13**, and the total is built from the rounded lines.
- **J. Cross-month unit attribution**: one event 2026-10-31T23:30Z, 60 minutes → 2 units in
  October, 2 units in November; billing cycle 2026-11 counts only the 2 November units.
- **K. Duplicates**: same (user, event_id) sent twice with identical payload → billed once,
  warning_count 1, error_count 0. Same id with a different duration → both billed, warning_count 1.
- **L. DST rejection**: user in America/New_York, offset-less event at `2026-11-01T01:30`
  (ambiguous, fall-back) or `2026-03-08T02:30` (nonexistent, spring-forward) → ERROR, skipped
  with reason `ambiguous local time` / `nonexistent local time`.

## 7. Structure (plain Java, no framework — the task is a calculator, not a web service)
```
pom.xml                     Maven, Java 21, Jackson (JSON), JUnit 5; Maven Wrapper committed
src/main/java/com/samwang/flexirate/
  Main.java                 CLI: args = input.json [output.json]; no output arg -> stdout;
                            FATAL input -> non-zero exit + stderr message
  model/  User, UsageEvent, BillingInput, BillingOutput, UserBill, TierLine,
          SurchargeInfo, SkippedItem, WarningItem
  BillingCalculator.java    pure core: BillingInput -> BillingOutput (clock-injectable for "current cycle")
  EventValidation.java      ERROR/WARNING classification per §4 (or a section of the calculator)
src/test/java/com/samwang/flexirate/   frozen tests (see TASKS.md T1)
README.md  DECISIONS.md  SESSION_LOG.md  ai-transcripts/
```

## 8. Explicit non-goals
No Spring Boot, no database, no server. No reconstructing raw API sessions. No refactoring of
the spec into a different problem. Keep everything in this shape so the code can be walked
through file-by-file in a review.

# Flexi-Rate Usage Calculator

Computes per-user charges for one billing cycle from pre-aggregated usage
events, applying a free grace allowance, marginal volume tiers and a peak-hour
surcharge. Input and output are JSON. Invalid items are never silently
dropped: recoverable problems skip the item and are reported in the output
header; a malformed document fails the whole run.

## Requirements

- JDK 21 or later.
- The Maven Wrapper is included (`./mvnw`); no separate Maven install needed.

## Build and test

```bash
./mvnw test      # 74 tests: 48 acceptance, 9 parsing/validation, 4 CLI, 13 robustness
./mvnw package   # builds the self-contained target/flexirate.jar
```

## Run

```bash
java -jar target/flexirate.jar input.json            # JSON to stdout
java -jar target/flexirate.jar input.json output.json
cat input.json | java -jar target/flexirate.jar > output.json
java -jar target/flexirate.jar sample-input.json     # bundled example
```

Every skipped item and every warning is also printed to stderr, one line per
item, e.g. `SKIPPED event a4: start not on a quarter hour` /
`WARNING event a2: duplicate event_id, identical payload`.

Exit codes: **0** success; **1** malformed input document (FATAL — no output
produced); **2** usage or I/O error (e.g. unreadable input file).

The same core is available as a library:
`FlexiRateCalculatorApi.calculate(String json)` returns the output JSON and
throws `FatalBillingException` for a malformed document.

## Input

```json
{
  "billing_cycle": "2026-11",
  "users":  [ { "user_id": "u1", "is_enterprise": false, "timezone": "UTC" } ],
  "events": [ { "event_id": "a1", "user_id": "u1",
                "timestamp": "2026-11-02T00:00:00Z", "duration_minutes": 600 } ]
}
```

- `billing_cycle` is `"YYYY-MM"`; if omitted, the current month in UTC is
  billed. One run bills exactly one cycle.
- `timezone` is an IANA zone id. Timestamps may carry an offset or `Z`, or be
  offset-less (then interpreted in the user's timezone).
- Unknown JSON fields are ignored at every level.

## Output

```json
{
  "header": {
    "error_count": 1,
    "warning_count": 1,
    "billing_cycle": "2026-11",
    "skipped_items": [ { "event_id": "a4", "reason": "start not on a quarter hour" } ],
    "warnings":      [ { "event_id": "a2", "reason": "duplicate event_id, identical payload" } ]
  },
  "results": [ {
    "user_id": "u1",
    "total_cost": 1.10,
    "tier_breakdown": {
      "grace":  { "hours": 10.00, "rate": 0.00, "cost": 0.00 },
      "tier_1": { "hours": 1.00,  "rate": 1.00, "cost": 1.00 },
      "tier_2": { "hours": 0.00,  "rate": 0.80, "cost": 0.00 },
      "tier_3": { "hours": 0.00,  "rate": 0.50, "cost": 0.00 }
    },
    "applied_surcharges": { "peak_hours": 0.50, "peak_rate": 0.20,
                            "peak_surcharge_total": 0.10 }
  } ]
}
```

- One result per valid user, in input order — including users with no usage
  in the cycle (all zeros).
- Each tier line's `cost` is that line's hours × rate (base only); surcharges
  are reported only in `applied_surcharges`. Invariant:
  sum of tier line costs + `peak_surcharge_total` = `total_cost`, and
  grace + tier_1 + tier_2 + tier_3 hours = the user's valid in-cycle usage.

## Billing rules

1. Every valid event is expanded into consecutive 15-minute units; each unit
   is evaluated independently for grace, tier and peak.
2. Grace: the first 10 hours per user per cycle are free. For non-enterprise
   users grace does not advance tier progression; for enterprise users it
   does (grace consumes tier_1 capacity) while still costing $0.00.
3. Marginal tiers on progression hours: 0–100h at $1.00/h, 100–500h at
   $0.80/h, above 500h at $0.50/h.
4. Peak: a unit whose UTC start falls in [09:00, 17:00) costs its tier rate
   × 1.20. The extra 20% accumulates into `peak_surcharge_total`. Grace units
   are never surcharged; `peak_hours` counts only surcharged hours.
5. All limits are UTC-based. A unit belongs to the cycle of its UTC start, so
   an event straddling a month boundary is split between cycles; units
   outside the billed cycle are ignored (not an error).
6. A user's events are processed in timestamp order (ties by event_id).
7. Offset-less timestamps are converted through the user's timezone. A local
   time that does not exist (spring-forward gap) or is ambiguous (fall-back
   overlap) is rejected as an error — no instant is guessed.
8. Duplicate (user_id, event_id): identical payload → billed once, with a
   warning; different payload → both billed, with a warning.
9. Overlapping events are billed in full and independently — concurrent
   usage is the normal shape of API usage, not an anomaly; intervals are
   never merged.
10. Money is BigDecimal throughout. Each tier line cost and the surcharge
    total are rounded HALF_UP to cents; `total_cost` is the sum of the
    rounded lines, so the output always reconciles to the cent.

## Error model

| Level | Condition | Effect |
|---|---|---|
| FATAL | Malformed JSON, `users`/`events` missing or not arrays, or `billing_cycle` present but not a valid YYYY-MM | No output; CLI exits 1 |
| User rejected | `user_id` missing/blank, `timezone` missing/invalid, or `is_enterprise` missing/not a boolean | No result row; the user's events fail as unknown-user errors |
| ERROR (skip) | Unknown user; unparseable timestamp; DST gap/ambiguity; duration missing or of an invalid type, ≤ 0, or not a multiple of 15; start not aligned to a UTC quarter hour | Item skipped; counted in `error_count`, listed in `skipped_items` with its reason, logged to stderr |
| WARNING | Duplicate event_id (identical or different payload) | Billed per rule 8; counted in `warning_count`, listed in `warnings`, logged to stderr |

## Project layout

- `Main` — CLI: file/stdin input, file/stdout output, stderr item log, exit codes.
- `FlexiRateCalculatorApi` — library entry point (String in, String out) and
  the document-level FATAL checks.
- `BillingCalculator` — the pure pricing core (no I/O, no JSON): user/event
  validation, duplicate policy, per-unit pricing.
- `model/` — input/output records, plus two tolerant deserializers
  (`StrictBooleanDeserializer`, `TolerantDurationDeserializer`) that turn
  bad-typed fields into item-level errors instead of document failures.
- Tests: `FlexiRateAcceptanceTest` (48, frozen before implementation),
  `ParsingAndValidationFatalTest` (9), `MainCliTest` (4), `RobustnessTest`
  (13, added after a post-build critical review).

## Decisions and assumptions

`DECISIONS.md` records all 21 numbered decisions with their status
(employer-confirmed vs. assumption), the test-errata log and the AI-mistakes
log. Flagged assumptions include: marginal (not flat) tiers; the rounding
point in rule 10 (a confirmation question to the employer was still
unanswered at submission); duplicate event_id semantics; and rule 9 — the
overlap question was raised with the employer by email and unanswered at
submission, so concurrent usage is billed in full.

## Production notes

- CI (GitHub Actions, JDK 21) runs the full suite on every push.
- Code style is kept consistent manually; wiring Checkstyle/SpotBugs into CI
  is the stated next step when this joins a team repository.
- Known simplification (DECISIONS #14): a user whose timezone is invalid is
  rejected wholesale, even though their offset-bearing events would not need
  the timezone. Scoping the rejection is a production refinement.
- Complexity and memory: parsing materialises the JSON tree plus the bound
  model, and pricing expands the billed cycle's units per user (at most
  2,976 quarter-hours per event stream per month, multiplied by
  concurrency). The production path on both axes: stream the input with
  Jackson's streaming JsonParser (already a dependency — the Java analogue
  of Python's ijson) for O(1) parse memory, and segment arithmetic counting
  instead of unit materialisation, so pricing memory scales with event
  count rather than unit count.

## AI collaboration

This was built by a small AI team with human direction: Muse as secretary,
manager and tester (and programmer for the final stages), ChatGPT as
independent reviewer/judge, Antigravity and Gemini CLI as programmers. Full,
uncropped transcripts of every AI session are in `ai-transcripts/`, indexed
by `SESSION_LOG.md`.

# TASKS — Flexi-Rate Usage Calculator (v3)

Rules (same discipline as the rehearsal): frozen tests are the contract — never modify them
to make code pass; one task per commit, commit only with the full suite green;
commit format `type(scope): summary`; push after each accepted task.

**CI gate between tasks (decided by Sam 2026-11-09 15:14)**: every push triggers GitHub
Actions (`./mvnw -B test` on a clean Ubuntu + JDK 21 machine, which runs in UTC — a deliberate
second environment for this project's UTC/DST logic). The next Antigravity conversation does
not start until CI matches the step's expected state, stated per task below; the manager
checks the Actions result at acceptance. Infrastructure failures (not test failures) get a
15-minute debugging cap; the submitted repo must not carry a red CI — at T5 it is either
green or the workflow is removed with the reason recorded in DECISIONS.md.

- [ ] **T0 Skeleton + plan.** Maven project, Java 21, Jackson + JUnit 5 deps, package
      `com.samwang.flexirate`, empty `Main`, **Maven Wrapper generated and committed**
      (so `./mvnw` works everywhere incl. CI). Copy SPEC.md / DECISIONS.md / TASKS.md /
      SESSION_LOG.md (v3) into the repo. Place the provided `ci.yml` at
      `.github/workflows/ci.yml`. Create the GitHub repo and push. `./mvnw test` green
      (no tests yet is fine).
      **CI gate: green** (clean-machine build succeeds).
      Commit: `chore(build): project skeleton and plan files`
- [ ] **T1 Frozen tests ONLY — no implementation.** Write the test suite from SPEC §3–§6.
      Every numbered example in SPEC §6 (A–L) is at least one test. Required coverage —
      each SPEC §4 rule maps to a named test:
      *Pricing*: A–F example tests; tier boundaries just below/above the line —
      non-enterprise 111h off-peak = $100.80 (100h tier_1 + 1h tier_2) and 511h = $420.50
      (first tier_3 hour); 99h and 499h progression stay in-tier; grace boundary: exactly
      10h total = $0.00, 10h + one unit = one billed unit; enterprise vs non-enterprise pair.
      *Time*: event starting exactly 09:00Z is peak, exactly 17:00Z is off-peak; shuffled
      input order gives identical results (chronological processing); G (UTC conversion);
      J (cross-month per-unit attribution); L (DST ambiguous + nonexistent rejected);
      offset-bearing timestamps unaffected by DST rules.
      *Validation (ERROR)*: unknown user; event of a rejected user; missing/blank event_id;
      bad timestamp; duration 0 / negative; duration 10 and 20 (not multiples of 15);
      start at :07 (not quarter-aligned); each asserts error_count, the skip, and a
      skipped_items entry with a reason.
      *Validation (users)*: invalid timezone rejected; missing is_enterprise rejected;
      unknown JSON fields in users/events/top level are ignored and change nothing.
      *Warnings*: K duplicates — identical payload billed once + warning, different payload
      both billed + warning, neither touches error_count; overlapping events both billed +
      warning on the later event.
      *Envelope*: header carries error_count + warning_count + skipped_items + warnings;
      out-of-cycle valid events are neither errors nor warnings; billing_cycle omitted
      path (fixed injected clock); zero-usage user returned at 0.00; breakdown invariant
      (line costs + surcharge = total_cost, incl. the Example I half-cent case).
      Tests must fail (no implementation yet) — that red is expected.
      **CI gate: red by design** — CI must run, the tests must compile, and every failure
      must be an assertion failure; any compile or configuration error stops the line.
      Commit, then FREEZE: `test: frozen acceptance tests`
- [ ] **T2 Models + JSON parsing + validation.** Jackson models for input/output exactly as
      SPEC §2/§5 (incl. SkippedItem / WarningItem); mapper configured to ignore unknown
      properties; strict user validation per SPEC §2 (rejected users + cascade); event
      validation and the duplicate policy per SPEC §4.
      **CI gate: validation test classes green in CI; the failure count only decreases
      versus T1, and every remaining failure is a core-calculation test.**
      Commit: `feat(io): input models, parsing and validation`
- [ ] **T3 Core calculator.** Chronological sort, 15-minute unit expansion, per-unit cycle
      attribution, grace/tier/peak unit pricing per SPEC §3, overlap detection (WARNING),
      accumulators for tier_breakdown / applied_surcharges, output rounding per DECISIONS #9.
      All frozen tests green. **CI gate: full suite green — strict, no next task until green.**
      Commit: `feat(core): flexi-rate calculation with grace, tiers and peak split`
- [ ] **T4 CLI.** `Main`: read input path arg (or stdin), write output path arg (or stdout),
      pretty-printed JSON; one stderr line per skipped item and per warning with its reason;
      FATAL input (malformed top-level JSON / missing arrays) -> non-zero exit + clear stderr
      message, no stack-trace dump. Add 1–2 end-to-end tests using a sample input file under
      `src/test/resources` (also shipped as `sample-input.json` in repo root).
      **CI gate: full suite green — strict.**
      Commit: `feat(cli): file/stdin entry point`
- [ ] **T5 Close-out.** README (run instructions, rules summary, output-shape documentation
      as Velislava required, assumptions table pointing to DECISIONS.md with each decision's
      Status, production notes); DECISIONS.md updated incl. AI mistakes caught;
      SESSION_LOG.md finalised as the index PLUS `ai-transcripts/` completed under the
      self-packaging arrangement (each AI packages its own conversation — see SESSION_LOG.md);
      manual test: run the CLI on sample-input.json and eyeball the A–L numbers by hand.
      Final full suite green locally AND in CI (or the workflow is removed with the reason
      recorded in DECISIONS.md — no red CI in the submission); push.
      Commit: `docs: readme, decisions and session log`

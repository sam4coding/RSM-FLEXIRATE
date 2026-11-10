
# Session Log — Flexi-Rate Usage Calculator

The employer asked for full, uncropped AI transcripts ("If you crop it, I
will decide you are hiding something"). This file indexes every AI session
that touched the project and summarises how the work ran. Nothing in
`ai-transcripts/` is edited or excerpted; where a record could not be
recovered verbatim, the file itself says so.

## Roles

| Role | Who |
|---|---|
| Direction, decisions, final audit | Sam Wang (human) |
| Secretary, manager, tester; programmer from T4 | Muse |
| Independent reviewer / judge | ChatGPT |
| Programmer, T0–T1 | Antigravity — Claude Sonnet 4.6 (Thinking) |
| Programmer, T2 (first attempt) | Gemini CLI — gemini-3-flash-preview |
| Programmer, T2 (completion) and T3 | Antigravity — Gemini (Flash tier) |
| T5 support (transcript reconstruction, A–L verification) | Antigravity — third account |

## Transcript index (`ai-transcripts/`)

| File | Covers | Notes |
|---|---|---|
| `01-claude-secretary-manager-tester.md` | The whole project, 2026-11-09/10 | Planning, SPEC v1→v3.3, instructions to all programmers, test/rule reviews, errata analysis, T4 code, close-out |
| `02-antigravity-T0.md` | T0 skeleton | |
| `02-antigravity-T1.md` | T1 frozen tests | Committed by Sam by hand after the session's quota died mid-commit |
| `02-antigravity-T2.md` | T2 completion | Includes the file-by-file audit of Gemini CLI's uncommitted work |
| `02-antigravity-T3.md` | T3 pricing + errata | Session ended by quota exhaustion mid-diagnosis |
| `02-antigravity-T5.md` | T5 support tasks | Third Antigravity account; includes the full A–L verification table |
| `03-chatgpt-reviewer.md` | All review rounds | 12-point adversarial review of SPEC v2; errata/overlap rulings of 2026-11-09 21:18 |
| `04-gemini-cli-T2.md` | Gemini CLI's T2 attempt | **Reconstruction**: the CLI's own session records could not be located under `~/.gemini`; rebuilt from project records and disclosed as such in the file |

## Process rules

- Acceptance tests were written and frozen (T1) before the implementation;
  they were never weakened to make code pass. Encoding errors found in the
  tests themselves were fixed through a documented errata procedure
  (DECISIONS.md, E1–E6) in dedicated commits touching no production code.
- Per-step CI gates: T0 green; T1 red by assertion failures only; T2
  failures only decreasing; T3 and later strictly green.
- One task per commit, format `type(scope): summary`, pushed after
  acceptance; the manager verified git history, diffs and CI independently.

## Timeline (2026-11-09, EST)

- 07:30 — Task received from Velislava Gergelcheva (Google Doc).
- 08:46–08:57 — Velislava answers all five clarification comments: all
  limits UTC-based (user timezone only converts offset-less timestamps);
  starts must align to a quarter hour; output shape free if it reconciles;
  header envelope confirmed; full uncropped transcripts required.
- Morning — SPEC v2 → v3 after ChatGPT's 12-point adversarial review
  (15-minute unit model, examples extended to A–L, 21 numbered decisions).
- 11:54 — Viktor's email: events are pre-aggregated; duration must be a
  positive multiple of 15; per-15-minute-unit peak split confirmed.
- ~16:30 — T0 done (skeleton, Maven Wrapper, CI). GitHub Actions was
  initially blocked by a $0 budget on the account; a payment method was
  added and the budget raised to $10.
- T1 — 48 acceptance tests frozen against stubs; CI red by design,
  assertion failures only.
- Programmer quota saga — Antigravity's Claude pool was exhausted on the
  first account (mid-T1) and on a second account (after T1). Gemini CLI
  started T2, lost its quota, auto-switched to a model with no quota and
  retried 10,142 times with 0 tokens before Sam killed it (session
  822271c1-8077-4ace-b3a2-0415bedf552a). Antigravity's Gemini audited the
  CLI's uncommitted tree file-by-file and completed T2: 57 tests run,
  23 failures — all pricing, as designed.
- T3 — Antigravity stopped on three contradictions between the frozen tests
  and the SPEC instead of forcing green. Manager review + ChatGPT's ruling
  produced test errata E1–E5 (the tests had encoded "all off-peak / all
  peak" premises as single continuous events, which cannot exist under the
  confirmed unit model) and the overlap-policy revision (concurrent usage
  billed in full, no warning). Antigravity executed the errata and the
  pricing core, then its quota was exhausted at 21:46, mid-diagnosis.
  Sam pushed the errata commit (c8a2a0f) and a wip checkpoint (0447ba1).
- E6 — The 2 remaining failures exposed an arithmetic error in the SPEC
  itself: Example B's expected $90 (present since SPEC v1, missed by two
  AI reviewers) makes 10 billed hours vanish; the correct total is $98.
  Approved by Sam; history rebuilt as 941fc7e (errata E6) + cdade16
  (feat(core)) and force-pushed with lease. T3 accepted: 57/57 green,
  CI run #9 success.
- 22:38 — A third Antigravity account was logged in, both quota pools at
  100%; reserved for T5 support tasks.
- T4 — The manager wrote the CLI (Main, CLI tests, sample input, shade
  packaging); Sam applied it. 61/61 green. The jar smoke test on
  sample-input.json matched the manager's independent precomputation
  exactly (u1 $1.10, u2 $6.00, error_count 1, warning_count 1, both stderr
  lines). A generated output.json was briefly committed and removed
  (49b7082).
- T5 (2026-11-10, early hours) — Antigravity (third account) reconstructed
  the Gemini CLI record (04) and independently re-ran every SPEC §6
  example A–L through the built jar: 15/15 rows match, including the
  sub-cases (full table in `02-antigravity-T5.md`). Sam exported the
  ChatGPT transcript (03). README, DECISIONS and this log finalised;
  repository link sent to Viktor (cc Velislava) ahead of the 08:00
  deadline.

## Verification summary

- 61 automated tests, all green locally and in CI.
- Independent end-to-end re-run of all worked examples through the shipped
  jar (above).
- Two employer questions were still unanswered at submission — billing
  rounding confirmation (Velislava) and the overlap note (Viktor); the
  assumptions taken are flagged in DECISIONS.md (#9, #15).

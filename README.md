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
./mvnw test      # 61 tests: 48 acceptance, 9 parsing/validation, 4 CLI
./mvnw package   # builds the self-contained target/flexirate.jar

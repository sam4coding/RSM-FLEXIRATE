package com.samwang.flexirate;

import com.samwang.flexirate.model.BillingInput;
import com.samwang.flexirate.model.BillingOutput;

import java.time.Clock;

/**
 * Pure calculator core: BillingInput → BillingOutput.
 * Clock is injectable so tests can fix "current cycle" for omitted billing_cycle.
 * Implementation in T3.
 */
public class BillingCalculator {

    private final Clock clock;

    public BillingCalculator(Clock clock) {
        this.clock = clock;
    }

    public BillingCalculator() {
        this(Clock.systemUTC());
    }

    /**
     * Calculate billing for one cycle.
     *
     * @param input billing input (pre-parsed)
     * @return billing output with header + per-user results
     */
    public BillingOutput calculate(BillingInput input) {
        throw new UnsupportedOperationException("not implemented");
    }
}

package com.samwang.flexirate.model;

import java.math.BigDecimal;

/**
 * Peak surcharge summary for a user's billing result.
 */
public class SurchargeInfo {
    private BigDecimal peak_hours;
    private BigDecimal peak_rate;
    private BigDecimal peak_surcharge_total;

    public SurchargeInfo() {}

    public BigDecimal getPeak_hours() { return peak_hours; }
    public void setPeak_hours(BigDecimal peak_hours) { this.peak_hours = peak_hours; }

    public BigDecimal getPeak_rate() { return peak_rate; }
    public void setPeak_rate(BigDecimal peak_rate) { this.peak_rate = peak_rate; }

    public BigDecimal getPeak_surcharge_total() { return peak_surcharge_total; }
    public void setPeak_surcharge_total(BigDecimal peak_surcharge_total) { this.peak_surcharge_total = peak_surcharge_total; }
}

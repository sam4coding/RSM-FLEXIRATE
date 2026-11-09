package com.samwang.flexirate.model;

import java.math.BigDecimal;

/**
 * A single tier's billing line: hours consumed, rate, and cost.
 */
public class TierLine {
    private BigDecimal hours;
    private BigDecimal rate;
    private BigDecimal cost;

    public TierLine() {}

    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
}

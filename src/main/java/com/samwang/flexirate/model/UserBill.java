package com.samwang.flexirate.model;

import java.math.BigDecimal;

/**
 * Per-user billing result.
 */
public class UserBill {
    private String user_id;
    private BigDecimal total_cost;
    private TierBreakdown tier_breakdown;
    private SurchargeInfo applied_surcharges;

    public UserBill() {}

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public BigDecimal getTotal_cost() { return total_cost; }
    public void setTotal_cost(BigDecimal total_cost) { this.total_cost = total_cost; }

    public TierBreakdown getTier_breakdown() { return tier_breakdown; }
    public void setTier_breakdown(TierBreakdown tier_breakdown) { this.tier_breakdown = tier_breakdown; }

    public SurchargeInfo getApplied_surcharges() { return applied_surcharges; }
    public void setApplied_surcharges(SurchargeInfo applied_surcharges) { this.applied_surcharges = applied_surcharges; }
}

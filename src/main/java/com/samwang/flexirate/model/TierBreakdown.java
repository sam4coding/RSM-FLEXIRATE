package com.samwang.flexirate.model;

/**
 * Breakdown of costs across the four billing tiers (grace, tier_1, tier_2, tier_3).
 */
public class TierBreakdown {
    private TierLine grace;
    private TierLine tier_1;
    private TierLine tier_2;
    private TierLine tier_3;

    public TierBreakdown() {}

    public TierLine getGrace() { return grace; }
    public void setGrace(TierLine grace) { this.grace = grace; }

    public TierLine getTier_1() { return tier_1; }
    public void setTier_1(TierLine tier_1) { this.tier_1 = tier_1; }

    public TierLine getTier_2() { return tier_2; }
    public void setTier_2(TierLine tier_2) { this.tier_2 = tier_2; }

    public TierLine getTier_3() { return tier_3; }
    public void setTier_3(TierLine tier_3) { this.tier_3 = tier_3; }
}

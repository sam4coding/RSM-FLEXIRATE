package com.samwang.flexirate.model;

import java.util.List;

/**
 * Output header containing summary counts, skipped items, and warnings.
 */
public class BillingHeader {
    private int error_count;
    private int warning_count;
    private String billing_cycle;
    private List<SkippedItem> skipped_items;
    private List<WarningItem> warnings;

    public BillingHeader() {}

    public int getError_count() { return error_count; }
    public void setError_count(int error_count) { this.error_count = error_count; }

    public int getWarning_count() { return warning_count; }
    public void setWarning_count(int warning_count) { this.warning_count = warning_count; }

    public String getBilling_cycle() { return billing_cycle; }
    public void setBilling_cycle(String billing_cycle) { this.billing_cycle = billing_cycle; }

    public List<SkippedItem> getSkipped_items() { return skipped_items; }
    public void setSkipped_items(List<SkippedItem> skipped_items) { this.skipped_items = skipped_items; }

    public List<WarningItem> getWarnings() { return warnings; }
    public void setWarnings(List<WarningItem> warnings) { this.warnings = warnings; }
}

package com.samwang.flexirate.model;

import java.util.List;

/**
 * Top-level billing output document.
 */
public class BillingOutput {
    private BillingHeader header;
    private List<UserBill> results;

    public BillingOutput() {}

    public BillingHeader getHeader() { return header; }
    public void setHeader(BillingHeader header) { this.header = header; }

    public List<UserBill> getResults() { return results; }
    public void setResults(List<UserBill> results) { this.results = results; }
}

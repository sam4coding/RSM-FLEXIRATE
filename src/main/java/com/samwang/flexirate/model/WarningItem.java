package com.samwang.flexirate.model;

/**
 * Records a warning (anomalous but billed) event in the output header.
 */
public class WarningItem {
    private String event_id;
    private String reason;

    public WarningItem() {}

    public String getEvent_id() { return event_id; }
    public void setEvent_id(String event_id) { this.event_id = event_id; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

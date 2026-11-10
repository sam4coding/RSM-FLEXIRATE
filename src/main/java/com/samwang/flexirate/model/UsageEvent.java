package com.samwang.flexirate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents a pre-aggregated usage event in the billing input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageEvent {
    private String event_id;
    private String user_id;
    private String timestamp;
    @JsonDeserialize(using = TolerantDurationDeserializer.class)
    private Integer duration_minutes;

    public UsageEvent() {}

    public String getEvent_id() { return event_id; }
    public void setEvent_id(String event_id) { this.event_id = event_id; }

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Integer getDuration_minutes() { return duration_minutes; }
    public void setDuration_minutes(Integer duration_minutes) { this.duration_minutes = duration_minutes; }
}

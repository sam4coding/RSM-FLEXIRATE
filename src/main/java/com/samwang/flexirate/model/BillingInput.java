package com.samwang.flexirate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Top-level billing input document.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingInput {
    private List<User> users;
    private List<UsageEvent> events;
    private String billing_cycle;

    public BillingInput() {}

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }

    public List<UsageEvent> getEvents() { return events; }
    public void setEvents(List<UsageEvent> events) { this.events = events; }

    public String getBilling_cycle() { return billing_cycle; }
    public void setBilling_cycle(String billing_cycle) { this.billing_cycle = billing_cycle; }
}

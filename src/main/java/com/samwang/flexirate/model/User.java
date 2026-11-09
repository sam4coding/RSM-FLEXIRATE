package com.samwang.flexirate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents a user in the billing input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    private String user_id;
    @JsonDeserialize(using = StrictBooleanDeserializer.class)
    private Boolean is_enterprise;
    private String timezone;

    public User() {}

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public Boolean getIs_enterprise() { return is_enterprise; }
    public void setIs_enterprise(Boolean is_enterprise) { this.is_enterprise = is_enterprise; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}

package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents header entries
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Header {
    private String key;
    private String value;
    private Boolean disabled;

    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }
}
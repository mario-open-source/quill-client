package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents query parameters in the URL
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Query {
    private String key;
    private String value;

    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

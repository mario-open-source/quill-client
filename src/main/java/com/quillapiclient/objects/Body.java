package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents the request body
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Body {
    public String mode;
    public String raw;
    public Options options;

    // Getters and Setters
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }
    public Options getOptions() { return options; }
    public void setOptions(Options options) { this.options = options; }
}

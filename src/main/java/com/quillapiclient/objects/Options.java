package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents additional options for the body (e.g., language)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Options {
    private RawOptions raw;

    // Getters and Setters
    public RawOptions getRaw() { 
        return raw; 
    }
    
    public void setRaw(RawOptions raw) { 
        this.raw = raw; 
    }
}


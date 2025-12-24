package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the raw options inside body options
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawOptions {
    private String language;

    // Getters and Setters
    public String getLanguage() { 
        return language; 
    }
    
    public void setLanguage(String language) { 
        this.language = language; 
    }
}


package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents variables (if any) defined in the collection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Variable {
    private String key;
    private String value;
    private String type;

    // Getters and Setters
    public String getKey() { 
        return key; 
    }
    
    public void setKey(String key) { 
        this.key = key; 
    }
    
    public String getValue() { 
        return value; 
    }
    
    public void setValue(String value) { 
        this.value = value; 
    }
    
    public String getType() { 
        return type; 
    }
    
    public void setType(String type) { 
        this.type = type; 
    }
}


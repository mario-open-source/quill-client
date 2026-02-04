package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an event in the Postman collection (e.g., pre-request or test scripts)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {
    private String id;
    private String listen;
    private Script script;
    private Boolean disabled;

    // Getters and Setters
    public String getListen() { 
        return listen; 
    }
    
    public void setListen(String listen) { 
        this.listen = listen; 
    }
    
    public Script getScript() { 
        return script; 
    }
    
    public void setScript(Script script) { 
        this.script = script; 
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Boolean getDisabled() {
        return disabled;
    }
    
    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }
}


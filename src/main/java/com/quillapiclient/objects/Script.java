package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Represents a script in the Postman collection (e.g., pre-request or test scripts)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Script {
    private String type;
    private List<String> exec;

    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public List<String> getExec() {
        return exec;
    }
    
    public void setExec(List<String> exec) {
        this.exec = exec;
    }
}


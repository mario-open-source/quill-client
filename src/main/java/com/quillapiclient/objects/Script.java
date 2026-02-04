package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
/**
 * Represents a script in the Postman collection (e.g., pre-request or test scripts)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Script {
    private String id;
    private String type;
    private List<String> exec; 
    private Url src;
    private String name;
    private Object packages; // Package Library dependencies (structure varies)

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
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Url getSrc() {
        return src;
    }
    
    public void setSrc(Url src) {
        this.src = src;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public Object getPackages() {
        return packages;
    }

    public void setPackages(Object packages) {
        this.packages = packages;
    }
}


package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents variables (if any) defined in the collection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Variable {
    private String id;
    private String key;
    private String value;
    private String type;
    private String name;
    private Object description; // Can be String or DescriptionObject
    private Boolean system;
    private Boolean disabled;

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
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Object getDescription() {
        return description;
    }
    
    public void setDescription(Object description) {
        this.description = description;
    }
    
    public Boolean getSystem() {
        return system;
    }
    
    public void setSystem(Boolean system) {
        this.system = system;
    }
    
    public Boolean getDisabled() {
        return disabled;
    }
    
    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }
}


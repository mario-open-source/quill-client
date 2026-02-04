package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Root object representing the whole Postman collection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostmanCollection {
    private Info info;
    private List<Item> item;
    private List<Event> event;
    private List<Variable> variable;
    private Auth auth;
    private ProtocolProfileBehavior protocolProfileBehavior;

    // Getters and Setters
    public Info getInfo() { 
        return info; 
    }
    
    public void setInfo(Info info) { 
        this.info = info; 
    }
    
    public List<Item> getItem() { 
        return item; 
    }
    
    public void setItem(List<Item> item) { 
        this.item = item; 
    }
    
    public List<Event> getEvent() { 
        return event; 
    }
    
    public void setEvent(List<Event> event) { 
        this.event = event; 
    }
    
    public List<Variable> getVariable() { 
        return variable; 
    }
    
    public void setVariable(List<Variable> variable) {
        this.variable = variable;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public ProtocolProfileBehavior getProtocolProfileBehavior() {
        return protocolProfileBehavior;
    }

    public void setProtocolProfileBehavior(ProtocolProfileBehavior protocolProfileBehavior) {
        this.protocolProfileBehavior = protocolProfileBehavior;
    }
}
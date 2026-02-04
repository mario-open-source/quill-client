package com.quillapiclient.objects;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents each item in the "item" array.
// An item can either be a folder (with nested items) or a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {
    private String id;
    private String name;
    private Object description; // Can be String or DescriptionObject
    private Request request;
    private List<Object> response; // You can customize the response type if needed.
    private List<Item> item;       // For nested folders/items.
    private List<Variable> variable; // For any variables defined at this level
    private List<Event> event;     // For pre-request and test scripts
    private Auth auth;
    private ProtocolProfileBehavior protocolProfileBehavior;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Object getDescription() { return description; }
    public void setDescription(Object description) { this.description = description; }
    public Request getRequest() { return request; }
    public void setRequest(Request request) { this.request = request; }
    public List<Object> getResponse() { return response; }
    public void setResponse(List<Object> response) { this.response = response; }
    public List<Item> getItem() { return item; }
    public void setItem(List<Item> item) { this.item = item; }
    public List<Variable> getVariable() { return variable; }
    public void setVariable(List<Variable> variable) { this.variable = variable; }
    public List<Event> getEvent() { return event; }
    public void setEvent(List<Event> event) { this.event = event; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public ProtocolProfileBehavior getProtocolProfileBehavior() { return protocolProfileBehavior; }
    public void setProtocolProfileBehavior(ProtocolProfileBehavior protocolProfileBehavior) { this.protocolProfileBehavior = protocolProfileBehavior; }
}
package com.quillapiclient.objects;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents each item in the "item" array.
// An item can either be a folder (with nested items) or a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {
    private String name;
    private Request request;
    private List<Object> response; // You can customize the response type if needed.
    private List<Item> item;       // For nested folders/items.
    private List<Variable> variable; // For any variables defined at this level

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Request getRequest() { return request; }
    public void setRequest(Request request) { this.request = request; }
    public List<Object> getResponse() { return response; }
    public void setResponse(List<Object> response) { this.response = response; }
    public List<Item> getItem() { return item; }
    public void setItem(List<Item> item) { this.item = item; }
    public List<Variable> getVariable() { return variable; }
    public void setVariable(List<Variable> variable) { this.variable = variable; }
}
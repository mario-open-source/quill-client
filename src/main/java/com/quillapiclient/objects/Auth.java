package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// Represents authentication information
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Auth {
    public String type;
    // For basic auth credentials
    public List<Credential> basic;
    // For bearer auth credentials
    public List<Credential> bearer;

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<Credential> getBasic() { return basic; }
    public void setBasic(List<Credential> basic) { this.basic = basic; }
    public List<Credential> getBearer() { return bearer; }
    public void setBearer(List<Credential> bearer) { this.bearer = bearer; }
}
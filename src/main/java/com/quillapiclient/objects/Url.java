package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

// Represents the URL details
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url {
    public String raw;
    public String protocol;
    public List<String> host;
    public List<String> path;
    public List<Query> query;
    public String port;
    public String hash;
    public List<Variable> variable; // Path variables

    // Getters and Setters
    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public List<String> getHost() { return host; }
    public void setHost(List<String> host) { this.host = host; }
    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; }
    public List<Query> getQuery() { return query; }
    public void setQuery(List<Query> query) { this.query = query; }
    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public List<Variable> getVariable() { return variable; }
    public void setVariable(List<Variable> variable) { this.variable = variable; }
}
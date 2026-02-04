package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents proxy configuration for a request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyConfig {
    private String match;
    private String host;
    private Integer port;
    private Boolean tunnel;
    private Boolean disabled;

    // Getters and Setters
    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getTunnel() {
        return tunnel;
    }

    public void setTunnel(Boolean tunnel) {
        this.tunnel = tunnel;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }
}

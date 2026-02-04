package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// Represents the request details
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Request {
    public String method;
    public Auth auth;
    public List<Header> header;
    public Body body;
    public Url url;
    public Object description; // Can be String or DescriptionObject
    public ProxyConfig proxy;
    public Certificate certificate;

    // Getters and Setters
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public List<Header> getHeader() { return header; }
    public void setHeader(List<Header> header) { this.header = header; }
    public Body getBody() { return body; }
    public void setBody(Body body) { this.body = body; }
    public Url getUrl() { return url; }
    public void setUrl(Url url) { this.url = url; }
    public Object getDescription() { return description; }
    public void setDescription(Object description) { this.description = description; }
    public ProxyConfig getProxy() { return proxy; }
    public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }
    public Certificate getCertificate() { return certificate; }
    public void setCertificate(Certificate certificate) { this.certificate = certificate; }
}
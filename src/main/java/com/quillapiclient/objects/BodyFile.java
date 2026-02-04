package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a file in the request body
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BodyFile {
    private Object src; // Can be String or null
    private String content;

    // Getters and Setters
    public Object getSrc() {
        return src;
    }

    public void setSrc(Object src) {
        this.src = src;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a version that can be either a string or an object with major, minor, patch, and identifier
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Version {
    private Integer major;
    private Integer minor;
    private Integer patch;
    private String identifier;
    private Object meta;

    // Getters and Setters
    public Integer getMajor() {
        return major;
    }

    public void setMajor(Integer major) {
        this.major = major;
    }

    public Integer getMinor() {
        return minor;
    }

    public void setMinor(Integer minor) {
        this.minor = minor;
    }

    public Integer getPatch() {
        return patch;
    }

    public void setPatch(Integer patch) {
        this.patch = patch;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Object getMeta() {
        return meta;
    }

    public void setMeta(Object meta) {
        this.meta = meta;
    }
}

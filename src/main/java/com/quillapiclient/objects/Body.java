package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

// Represents the request body
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Body {
    public String mode;
    public String raw;
    public Options options;
    public java.util.List<UrlEncodedParameter> urlencoded;
    public java.util.List<FormParameter> formdata;
    public BodyFile file;
    public Object graphql; // GraphQL query object
    public Boolean disabled;

    // Getters and Setters
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }
    public Options getOptions() { return options; }
    public void setOptions(Options options) { this.options = options; }
    public java.util.List<UrlEncodedParameter> getUrlencoded() { return urlencoded; }
    public void setUrlencoded(java.util.List<UrlEncodedParameter> urlencoded) { this.urlencoded = urlencoded; }
    public java.util.List<FormParameter> getFormdata() { return formdata; }
    public void setFormdata(java.util.List<FormParameter> formdata) { this.formdata = formdata; }
    public BodyFile getFile() { return file; }
    public void setFile(BodyFile file) { this.file = file; }
    public Object getGraphql() { return graphql; }
    public void setGraphql(Object graphql) { this.graphql = graphql; }
    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }
}

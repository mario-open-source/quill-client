package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostmanEnvironment {
    private String id;
    private String name;
    private List<PostmanEnvironmentValue> values;

    @JsonProperty("_postman_variable_scope")
    private String variableScope;

    @JsonProperty("_postman_exported_at")
    private String exportedAt;

    @JsonProperty("_postman_exported_using")
    private String exportedUsing;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PostmanEnvironmentValue> getValues() {
        return values;
    }

    public void setValues(List<PostmanEnvironmentValue> values) {
        this.values = values;
    }

    public String getVariableScope() {
        return variableScope;
    }

    public void setVariableScope(String variableScope) {
        this.variableScope = variableScope;
    }

    public String getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(String exportedAt) {
        this.exportedAt = exportedAt;
    }

    public String getExportedUsing() {
        return exportedUsing;
    }

    public void setExportedUsing(String exportedUsing) {
        this.exportedUsing = exportedUsing;
    }
}

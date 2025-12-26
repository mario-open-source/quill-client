package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


// Represents the "info" object in the JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {
    @JsonProperty("_postman_id")
    private String postmanId;
    private String name;
    private String schema;
    @JsonProperty("_exporter_id")
    private String exporterId;
    private String description;

    // Getters and Setters
    public String getPostmanId() { return postmanId; }
    public void setPostmanId(String postmanId) { this.postmanId = postmanId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getExporterId() { return exporterId; }
    public void setExporterId(String exporterId) { this.exporterId = exporterId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
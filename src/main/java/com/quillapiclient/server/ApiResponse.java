package com.quillapiclient.server;

import java.util.Map;

public class ApiResponse {
    private int statusCode;
    private String body;
    private Map<String, java.util.List<String>> headers;
    private long duration; // Duration in milliseconds
    
    public ApiResponse() {
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public Map<String, java.util.List<String>> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, java.util.List<String>> headers) {
        this.headers = headers;
    }
    
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    public boolean isError() {
        return statusCode >= 400;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status Code: ").append(statusCode).append("\n");
        
        if (headers != null && !headers.isEmpty()) {
            sb.append("\nHeaders:\n");
            for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                sb.append(entry.getKey()).append(": ");
                sb.append(String.join(", ", entry.getValue())).append("\n");
            }
        }
        
        if (body != null) {
            sb.append("\nBody:\n").append(body);
        }
        
        return sb.toString();
    }
}


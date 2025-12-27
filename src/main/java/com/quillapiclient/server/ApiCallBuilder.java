package com.quillapiclient.server;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.utility.VariableReplacer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public class ApiCallBuilder {
    private String url;
    private String method;
    private Map<String, String> headers;
    private String body;
    private String authType;
    private String username;
    private String password;
    private String token;
    private Map<String, String> queryParams;
    private Map<String, String> variables; // Variables for replacement
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();
    
    public ApiCallBuilder() {
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
    }
    
    // Builder pattern methods
    public ApiCallBuilder url(String url) {
        this.url = url;
        return this;
    }
    
    public ApiCallBuilder method(String method) {
        this.method = method != null ? method.toUpperCase() : "GET";
        return this;
    }
    
    public ApiCallBuilder header(String key, String value) {
        if (key != null && value != null) {
            this.headers.put(key, value);
        }
        return this;
    }
    
    public ApiCallBuilder headers(String headersText) {
        if (headersText != null && !headersText.trim().isEmpty()) {
            parseHeaders(headersText);
        }
        return this;
    }
    
    public ApiCallBuilder body(String body) {
        this.body = body;
        return this;
    }
    
    public ApiCallBuilder authType(String authType) {
        this.authType = authType;
        return this;
    }
    
    public ApiCallBuilder basicAuth(String username, String password) {
        this.username = username;
        this.password = password;
        this.authType = "Basic auth";
        return this;
    }
    
    public ApiCallBuilder bearerToken(String token) {
        this.token = token;
        this.authType = "Bearer token";
        return this;
    }
    
    public ApiCallBuilder jwtToken(String token) {
        this.token = token;
        this.authType = "Jwt bearer";
        return this;
    }
    
    public ApiCallBuilder queryParam(String key, String value) {
        if (key != null && value != null) {
            this.queryParams.put(key, value);
        }
        return this;
    }
    
    public ApiCallBuilder queryParams(String paramsText) {
        if (paramsText != null && !paramsText.trim().isEmpty()) {
            parseQueryParams(paramsText);
        }
        return this;
    }
    
    /**
     * Sets variables for replacement in URLs and other fields.
     * Variables are replaced in the format {{variableName}}.
     * 
     * @param variables Map of variable names to values
     * @return This builder for method chaining
     */
    public ApiCallBuilder variables(Map<String, String> variables) {
        this.variables = variables;
        return this;
    }
    
    // Parse headers from text (format: "Key: Value\nKey2: Value2")
    private void parseHeaders(String headersText) {
        String[] lines = headersText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    headers.put(key, value);
                }
            }
        }
    }
    
    // Parse query parameters from text (format: "key=value&key2=value2" or "key: value\nkey2: value2")
    private void parseQueryParams(String paramsText) {
        // Try URL format first (key=value&key2=value2)
        if (paramsText.contains("=") && paramsText.contains("&")) {
            String[] pairs = paramsText.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        } else {
            // Try line-based format (key: value\nkey2: value2)
            String[] lines = paramsText.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    if (!key.isEmpty() && !value.isEmpty()) {
                        queryParams.put(key, value);
                    }
                }
            }
        }
    }
    
    // Build the full URL with query parameters and variable replacement
    private String buildUrl() {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        // Replace variables in the URL first
        String fullUrl = url.trim();
        if (variables != null && !variables.isEmpty()) {
            fullUrl = VariableReplacer.replaceVariables(fullUrl, variables);
        }
        
        try {
        if (!queryParams.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (first) {
                    queryString.append("?");
                    first = false;
                } else {
                    queryString.append("&");
                }
                
                // Replace variables in query parameter keys and values
                String paramKey = entry.getKey();
                String paramValue = entry.getValue();
                if (variables != null && !variables.isEmpty()) {
                    paramKey = VariableReplacer.replaceVariables(paramKey, variables);
                    paramValue = VariableReplacer.replaceVariables(paramValue, variables);
                }
                
                queryString.append(URLEncoder.encode(paramKey, StandardCharsets.UTF_8))
                          .append("=")
                          .append(URLEncoder.encode(paramValue, StandardCharsets.UTF_8));
            }

            fullUrl += queryString.toString();
        }
        } catch (Exception e) {
            return null;
        }
        System.out.println(fullUrl);
        return fullUrl;
    }
    
    // Add authentication headers
    private void addAuthHeaders() {
        if (authType == null || authType.equals("No auth")) {
            return;
        }
        
        switch (authType) {
            case "Basic auth":
                if (username != null && password != null) {
                    String credentials = username + ":" + password;
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    headers.put("Authorization", "Basic " + encoded);
                }
                break;
                
            case "Bearer token":
            case "Jwt bearer":
                if (token != null && !token.trim().isEmpty()) {
                    headers.put("Authorization", "Bearer " + token.trim());
                }
                break;
        }
    }
    
    // Execute the HTTP request
    public ApiResponse execute() {
        if (url == null || url.trim().isEmpty()) {
            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setBody("{\"error\": \"URL cannot be null or empty\"}");
            return errorResponse;
        }
        
        try {
            // Build URL with query parameters
            String fullUrl = buildUrl();
            URI uri = new URI(fullUrl);
            
            // Add authentication headers
            addAuthHeaders();
            
            // Build request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30));
            
            // Add headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            
            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (body != null && !body.trim().isEmpty() && 
                (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
                // Set Content-Type if not already set
                if (!headers.containsKey("Content-Type")) {
                    requestBuilder.header("Content-Type", "application/json");
                }
            }
            
            requestBuilder.method(method, bodyPublisher);
            
            HttpRequest request = requestBuilder.build();
            
            // Execute request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Build response object
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setStatusCode(response.statusCode());
            apiResponse.setBody(response.body());
            apiResponse.setHeaders(response.headers().map());
            
            return apiResponse;
            
        } catch (HttpTimeoutException e) {
            // Handle timeout - return 408 Request Timeout
            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setStatusCode(408);
            errorResponse.setBody("{\"error\": \"Request Timeout\", \"message\": \"The request timed out after 30 seconds\"}");
            return errorResponse;
            
        } catch (ConnectException e) {
            // Handle connection refused - return 503 Service Unavailable
            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setStatusCode(503);
            errorResponse.setBody("{\"error\": \"Service Unavailable\", \"message\": \"Connection refused. The server may be down or unreachable.\"}");
            return errorResponse;
            
        } catch (IOException e) {
            // Handle other network/IO errors - return 0 (connection error) or 502 Bad Gateway
            ApiResponse errorResponse = new ApiResponse();
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("Connection refused") || 
                                         errorMessage.contains("Network is unreachable"))) {
                errorResponse.setStatusCode(503);
                errorResponse.setBody("{\"error\": \"Service Unavailable\", \"message\": \"" + 
                                     errorMessage.replace("\"", "\\\"") + "\"}");
            } else {
                errorResponse.setStatusCode(502);
                errorResponse.setBody("{\"error\": \"Bad Gateway\", \"message\": \"" + 
                                     (errorMessage != null ? errorMessage.replace("\"", "\\\"") : "Network error") + "\"}");
            }
            return errorResponse;
            
        } catch (InterruptedException e) {
            // Handle thread interruption
            Thread.currentThread().interrupt();
            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setStatusCode(499);
            errorResponse.setBody("{\"error\": \"Client Closed Request\", \"message\": \"Request was interrupted\"}");
            return errorResponse;
            
        } catch (URISyntaxException e) {
            // Handle invalid URL syntax
            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setBody("{\"error\": \"Bad Request\", \"message\": \"Invalid URL: " + 
                                 (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Malformed URL") + "\"}");
            return errorResponse;
        }
    }
    
    // Static factory method for quick creation
    public static ApiCallBuilder create() {
        return new ApiCallBuilder();
    }
    
    /**
     * Convenience method to build a request from UI values
     * 
     * Example usage:
     * <pre>
     * ApiResponse response = ApiCallBuilder.fromUI(
     *     urlField.getText(),
     *     methodDropdown.getSelectedItem().toString(),
     *     headersTextArea.getText(),
     *     bodyTextArea.getText(),
     *     authTypeComboBox.getSelectedItem().toString(),
     *     userField.getText(),
     *     passField.getText(),
     *     tokenField.getText(),
     *     paramsTextArea.getText(),
     *     itemId
     * ).execute();
     * </pre>
     * 
     * @param itemId The item ID to retrieve variables for (use -1 if not available)
     */
    public static ApiCallBuilder fromUI(String url, String method, String headersText, 
                                        String bodyText, String authType, 
                                        String username, String password, String token,
                                        String queryParamsText, int itemId) {
        ApiCallBuilder builder = new ApiCallBuilder()
                .url(url)
                .method(method)
                .headers(headersText)
                .body(bodyText)
                .queryParams(queryParamsText);
        
        // Load variables from database if itemId is provided
        if (itemId > 0) {
            int collectionId = CollectionDao.getCollectionIdByItemId(itemId);
            Map<String, String> variables = CollectionDao.getAllVariablesForRequest(collectionId, itemId);
            if (!variables.isEmpty()) {
                builder.variables(variables);
            }
        }
        
        // Set authentication
        if (authType != null) {
            switch (authType) {
                case "Basic auth":
                    builder.basicAuth(username, password);
                    break;
                case "Bearer token":
                case "Jwt bearer":
                    builder.bearerToken(token);
                    break;
                default:
                    builder.authType(authType);
            }
        }
        
        return builder;
    }
}


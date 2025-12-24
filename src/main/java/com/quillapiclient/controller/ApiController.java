package com.quillapiclient.controller;

import com.quillapiclient.server.ApiCallBuilder;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.components.ResponsePanel;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiController {
    private ResponsePanel responsePanel;
    private static final int NUMBER_OF_THREADS = 5;
    private static final ExecutorService executorService = 
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public ApiController(ResponsePanel responsePanel) {
        this.responsePanel = responsePanel;
    }
    
    public void executeApiCall(String url, String method, String headersText, 
                              String bodyText, String authType, String username, 
                              String password, String token, String paramsText) {
        if (url.isEmpty()) {
            responsePanel.setResponse("Error: URL cannot be empty");
            return;
        }
        
        // Show loading message
        String loadingMessage = createLoadingMessage(url, method, headersText, bodyText, authType);
        responsePanel.setResponse(loadingMessage);
        
        // Submit the API call to the executor service
        executorService.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                ApiResponse response = ApiCallBuilder.fromUI(
                    url, method, headersText, bodyText, authType,
                    username, password, token, paramsText
                ).execute();

                // Calculate duration and set it on the response
                long duration = System.currentTimeMillis() - startTime;
                response.setDuration(duration);

                SwingUtilities.invokeLater(() ->
                    displayResponse(response)
                );
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    displayError(e)
                );
            }
        });
    }
    
    private String createLoadingMessage(String url, String method, String headersText, 
                                       String bodyText, String authType) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        sb.append("[").append(timestamp).append("] Sending request...\n");
        sb.append("═".repeat(60)).append("\n");
        sb.append("URL: ").append(url).append("\n");
        sb.append("Method: ").append(method).append("\n");
        sb.append("Auth: ").append(authType).append("\n");
        
        if (headersText != null && !headersText.trim().isEmpty()) {
            sb.append("Headers: ").append(headersText.split("\n").length)
              .append(" header(s) configured\n");
        }
        
        if (bodyText != null && !bodyText.trim().isEmpty()) {
            sb.append("Body: ").append(bodyText.length())
              .append(" character(s)\n");
        }
        
        sb.append("═".repeat(60)).append("\n");
        sb.append("Waiting for response...\n");
        
        return sb.toString();
    }
    
    private void displayResponse(ApiResponse response) {
        StringBuilder responseText = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        // Response header
        responseText.append("[").append(timestamp).append("] Response received\n");
        responseText.append("═".repeat(60)).append("\n");
        
        // Status code with visual indicator
        responseText.append("STATUS: ");
        int statusCode = response.getStatusCode();
        if (response.isSuccess()) {
            responseText.append("SUCCESS");
        } else if (statusCode >= 400 && statusCode < 500) {
            responseText.append("WARNING");
        } else {
            responseText.append("ERROR");
        }
        responseText.append(statusCode).append(" ").append(getStatusText(statusCode));
        responseText.append(" (").append(response.getDuration()).append(" ms)\n\n");
        
        // Headers section
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            responseText.append("HEADERS (").append(response.getHeaders().size()).append("):\n");
            responseText.append("-".repeat(40)).append("\n");
            
            for (Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                String key = entry.getKey();
                String values = String.join(", ", entry.getValue());
                
                // Color code common headers
                if (key.toLowerCase().contains("content-type")) {
                    responseText.append("CONTENT-TYPE: ");
                } else if (key.toLowerCase().contains("authorization") || 
                          key.toLowerCase().contains("token")) {
                    responseText.append("AUTHORIZATION: ");
                } else if (key.toLowerCase().contains("cache")) {
                    responseText.append("CACHE: ");
                } else if (key.toLowerCase().contains("cookie")) {
                    responseText.append("COOKIE: ");
                } else {
                    responseText.append("OTHER: ");
                }
                
                responseText.append(key).append(": ").append(values).append("\n");
            }
            responseText.append("\n");
        }
        
        // Body section
        if (response.getBody() != null && !response.getBody().isEmpty()) {
            String body = response.getBody();
            responseText.append("BODY (").append(body.length()).append(" chars):\n");
            responseText.append("-".repeat(40)).append("\n");
            
            // Try to format JSON if possible
            if (isJson(body)) {
                responseText.append(formatJson(body));
            } else if (isXml(body)) {
                responseText.append(formatXml(body));
            } else if (isHtml(body)) {
                responseText.append("<!DOCTYPE html>\n<!-- HTML Content (truncated) -->\n");
                responseText.append(truncateText(body, 2000)); // Truncate long HTML
            } else {
                responseText.append(truncateText(body, 5000)); // Truncate other content
            }
            
            // Add download/export option for large responses
            if (body.length() > 10000) {
                responseText.append("\n\nNote: Response is large (")
                           .append(body.length())
                           .append(" chars). Consider exporting to a file.");
            }
        } else {
            responseText.append("BODY: (empty)\n");
        }
        
        // Add request summary at the end
        responseText.append("\n").append("═".repeat(60)).append("\n");
        responseText.append("SUMMARY:\n");
        responseText.append("  • Status: ").append(statusCode)
                   .append(" (").append(response.isSuccess() ? "Success" : "Error").append(")\n");
        responseText.append("  • Time: ").append(response.getDuration()).append(" ms\n");
        
        if (response.getBody() != null) {
            responseText.append("  • Size: ").append(formatSize(response.getBody().length())).append("\n");
        }
        
        // Update the response panel
        responsePanel.setResponse(responseText.toString());
        
        // If there's an error status, show it more prominently
        if (!response.isSuccess()) {
            responsePanel.setErrorState(true);
        } else {
            responsePanel.setErrorState(false);
        }
    }
    
    private void displayError(Exception e) {
        StringBuilder errorBuilder = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        errorBuilder.append("[").append(timestamp).append("] ERROR: Request failed\n");
        errorBuilder.append("═".repeat(60)).append("\n");
        
        errorBuilder.append("EXCEPTION: ").append(e.getClass().getSimpleName()).append("\n\n");
        
        // Error message
        errorBuilder.append("MESSAGE:\n");
        errorBuilder.append("-".repeat(40)).append("\n");
        errorBuilder.append(e.getMessage() != null ? e.getMessage() : "No error message available");
        errorBuilder.append("\n\n");
        
        // Root cause if available
        if (e.getCause() != null) {
            errorBuilder.append("ROOT CAUSE:\n");
            errorBuilder.append("-".repeat(40)).append("\n");
            errorBuilder.append(e.getCause().getMessage());
            errorBuilder.append("\n\n");
        }
        
        // Stack trace (limited)
        errorBuilder.append("STACK TRACE (first 5 lines):\n");
        errorBuilder.append("-".repeat(40)).append("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        int lines = Math.min(5, stackTrace.length);
        for (int i = 0; i < lines; i++) {
            errorBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        
        if (stackTrace.length > 5) {
            errorBuilder.append("  ... and ").append(stackTrace.length - 5)
                       .append(" more lines\n");
        }
        
        errorBuilder.append("\n").append("═".repeat(60)).append("\n");
        errorBuilder.append("TROUBLESHOOTING:\n");
        
        // Add helpful troubleshooting tips based on exception type
        if (e instanceof java.net.UnknownHostException) {
            errorBuilder.append("  • Check your internet connection\n");
            errorBuilder.append("  • Verify the domain name is correct\n");
            errorBuilder.append("  • Try pinging the hostname\n");
        } else if (e instanceof java.net.ConnectException) {
            errorBuilder.append("  • The server may be down or unreachable\n");
            errorBuilder.append("  • Check firewall settings\n");
            errorBuilder.append("  • Verify the port number\n");
        } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
            errorBuilder.append("  • SSL certificate issue\n");
            errorBuilder.append("  • Try disabling SSL verification (for testing)\n");
            errorBuilder.append("  • Check certificate validity\n");
        } else if (e instanceof java.net.SocketTimeoutException) {
            errorBuilder.append("  • Request timed out\n");
            errorBuilder.append("  • Server might be busy\n");
            errorBuilder.append("  • Increase timeout settings\n");
        } else {
            errorBuilder.append("  • Check your request parameters\n");
            errorBuilder.append("  • Verify the API endpoint\n");
            errorBuilder.append("  • Ensure all required fields are filled\n");
        }
        
        // Update the response panel
        responsePanel.setResponse(errorBuilder.toString());
        responsePanel.setErrorState(true);
    }
    
    // Helper methods
    private String getStatusText(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "";
        }
    }
    
    private boolean isJson(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    private boolean isXml(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        return trimmed.startsWith("<?xml") || 
               (trimmed.startsWith("<") && trimmed.endsWith(">"));
    }
    
    private boolean isHtml(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim().toLowerCase();
        return trimmed.startsWith("<!doctype html") ||
               trimmed.contains("<html") ||
               trimmed.contains("<body");
    }
    
    private String formatJson(String json) {
        try {
            // Simple indentation for JSON
            int indentLevel = 0;
            StringBuilder formatted = new StringBuilder();
            boolean inQuotes = false;
            
            for (char c : json.toCharArray()) {
                if (c == '\"' && (formatted.length() == 0 || formatted.charAt(formatted.length() - 1) != '\\')) {
                    inQuotes = !inQuotes;
                }
                
                if (!inQuotes) {
                    if (c == '{' || c == '[') {
                        formatted.append(c).append("\n");
                        indentLevel++;
                        formatted.append(getIndent(indentLevel));
                    } else if (c == '}' || c == ']') {
                        formatted.append("\n");
                        indentLevel--;
                        formatted.append(getIndent(indentLevel));
                        formatted.append(c);
                    } else if (c == ',') {
                        formatted.append(c).append("\n");
                        formatted.append(getIndent(indentLevel));
                    } else if (c == ':') {
                        formatted.append(c).append(" ");
                    } else {
                        formatted.append(c);
                    }
                } else {
                    formatted.append(c);
                }
            }
            return formatted.toString();
        } catch (Exception e) {
            return json; // Return original if formatting fails
        }
    }
    
    private String formatXml(String xml) {
        try {
            // Simple XML formatting
            StringBuilder formatted = new StringBuilder();
            int indentLevel = 0;
            
            String[] lines = xml.replaceAll("><", ">\n<").split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                
                if (trimmed.startsWith("</")) {
                    indentLevel--;
                }
                
                formatted.append(getIndent(indentLevel)).append(trimmed).append("\n");
                
                if (trimmed.startsWith("<") && !trimmed.startsWith("</") && 
                    !trimmed.endsWith("/>") && !trimmed.contains("?>")) {
                    indentLevel++;
                }
            }
            return formatted.toString();
        } catch (Exception e) {
            return xml; // Return original if formatting fails
        }
    }
    
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n... [Content truncated. " + 
               (text.length() - maxLength) + " more characters]";
    }
    
    private String getIndent(int level) {
        return "  ".repeat(Math.max(0, level));
    }
    
    private String formatSize(int charCount) {
        if (charCount < 1024) {
            return charCount + " bytes";
        } else if (charCount < 1024 * 1024) {
            return String.format("%.1f KB", charCount / 1024.0);
        } else {
            return String.format("%.1f MB", charCount / (1024.0 * 1024.0));
        }
    }
}
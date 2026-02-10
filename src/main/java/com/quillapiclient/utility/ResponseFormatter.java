package com.quillapiclient.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.server.ApiResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Utility class for formatting ApiResponse objects for display.
 * Provides consistent formatting across the application.
 */
public class ResponseFormatter {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    // Common constants to reduce duplication
    public static final String NO_RESPONSE_MESSAGE = "There is no response for this request";
    public static final String ERROR_URL_EMPTY = "Error: URL cannot be empty";
    public static final String TIMESTAMP_FORMAT = "HH:mm:ss";
    public static final String SEPARATOR_LONG = "═".repeat(60);
    public static final String SEPARATOR_SHORT = "-".repeat(40);
    
    /**
     * Formats an ApiResponse for display in the ResponsePanel.
     * 
     * @param response The ApiResponse to format
     * @param timestampMessage Optional custom message for the timestamp line (e.g., "Response received" or "Response")
     * @return Formatted string representation of the response
     */
    public static String formatResponse(ApiResponse response, String timestampMessage) {
        if (response == null) {
            return NO_RESPONSE_MESSAGE;
        }
        
        StringBuilder responseText = new StringBuilder();
        
        // Headers section
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            responseText.append("HEADERS (").append(response.getHeaders().size()).append("):\n");
            responseText.append(SEPARATOR_SHORT).append("\n");
            
            for (Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                String key = entry.getKey();
                String values = String.join(", ", entry.getValue());
                
                // Categorize common headers for better readability
                String category = getHeaderCategory(key);
                if (!category.isEmpty()) {
                    responseText.append(category).append(" ");
                }
                
                responseText.append(key).append(": ").append(values).append("\n");
            }
            responseText.append("\n");
        }
        
        // Body section
        if (response.getBody() != null && !response.getBody().isEmpty()) {
            String body = response.getBody();
            responseText.append("BODY (").append(body.length()).append(" chars):\n");
            responseText.append(SEPARATOR_SHORT).append("\n");
            
            // Try to format JSON, XML, or HTML if possible
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
        
        return responseText.toString();
    }
    
    /**
     * Formats an ApiResponse with default timestamp message.
     * 
     * @param response The ApiResponse to format
     * @return Formatted string representation of the response
     */
    public static String formatResponse(ApiResponse response) {
        return formatResponse(response, "Response");
    }
    
    /**
     * Gets the category label for a header key.
     * 
     * @param key The header key
     * @return Category label (e.g., "CONTENT-TYPE:", "AUTHORIZATION:", etc.) or empty string
     */
    private static String getHeaderCategory(String key) {
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("content-type")) {
            return "CONTENT-TYPE:";
        } else if (lowerKey.contains("authorization") || lowerKey.contains("token")) {
            return "AUTHORIZATION:";
        } else if (lowerKey.contains("cache")) {
            return "CACHE:";
        } else if (lowerKey.contains("cookie")) {
            return "COOKIE:";
        }
        return "";
    }
    
    /**
     * Gets status text from status code.
     * 
     * @param statusCode HTTP status code
     * @return Status text description
     */
    private static String getStatusText(int statusCode) {
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
    
    /**
     * Checks if a string is JSON.
     * 
     * @param text The text to check
     * @return true if the text appears to be JSON
     */
    private static boolean isJson(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    /**
     * Checks if a string is XML.
     * 
     * @param text The text to check
     * @return true if the text appears to be XML
     */
    private static boolean isXml(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        return trimmed.startsWith("<?xml") || 
               (trimmed.startsWith("<") && trimmed.endsWith(">"));
    }
    
    /**
     * Checks if a string is HTML.
     * 
     * @param text The text to check
     * @return true if the text appears to be HTML
     */
    private static boolean isHtml(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim().toLowerCase();
        return trimmed.startsWith("<!doctype html") ||
               trimmed.contains("<html") ||
               trimmed.contains("<body");
    }
    
    /**
     * Formats JSON with simple indentation.
     * 
     * @param json The JSON string to format
     * @return Formatted JSON string
     */
    private static String formatJson(String json) {
        try {
            JsonNode jsonNode = JSON_MAPPER.readTree(json);
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            return json; // Return original if formatting fails
        }
    }
    
    /**
     * Formats XML with simple indentation.
     * 
     * @param xml The XML string to format
     * @return Formatted XML string
     */
    private static String formatXml(String xml) {
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
    
    /**
     * Truncates text to a maximum length.
     * 
     * @param text The text to truncate
     * @param maxLength Maximum length
     * @return Truncated text with indicator
     */
    private static String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n... [Content truncated. " + 
               (text.length() - maxLength) + " more characters]";
    }
    
    /**
     * Gets indentation string for a given level.
     * 
     * @param level Indentation level
     * @return Indentation string
     */
    private static String getIndent(int level) {
        return "  ".repeat(Math.max(0, level));
    }
    
    /**
     * Formats size in bytes/KB/MB.
     * 
     * @param charCount Character count
     * @return Formatted size string
     */
    public static String formatSize(int charCount) {
        if (charCount < 1024) {
            return charCount + " bytes";
        } else if (charCount < 1024 * 1024) {
            return String.format("%.1f KB", charCount / 1024.0);
        } else {
            return String.format("%.1f MB", charCount / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Formats an exception into a user-friendly error message with troubleshooting tips.
     * 
     * @param e The exception to format
     * @return Formatted error message string
     */
    public static String formatException(Exception e) {
        StringBuilder errorBuilder = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT);
        String timestamp = LocalDateTime.now().format(formatter);
        
        errorBuilder.append("[").append(timestamp).append("] ERROR: Request failed\n");
        errorBuilder.append(SEPARATOR_LONG).append("\n");
        
        errorBuilder.append("EXCEPTION: ").append(e.getClass().getSimpleName()).append("\n\n");
        
        // Error message
        errorBuilder.append("MESSAGE:\n");
        errorBuilder.append(SEPARATOR_SHORT).append("\n");
        errorBuilder.append(e.getMessage() != null ? e.getMessage() : "No error message available");
        errorBuilder.append("\n\n");
        
        // Root cause if available
        if (e.getCause() != null) {
            errorBuilder.append("ROOT CAUSE:\n");
            errorBuilder.append(SEPARATOR_SHORT).append("\n");
            errorBuilder.append(e.getCause().getMessage());
            errorBuilder.append("\n\n");
        }
        
        // Stack trace (limited)
        errorBuilder.append("STACK TRACE (first 5 lines):\n");
        errorBuilder.append(SEPARATOR_SHORT).append("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        int lines = Math.min(5, stackTrace.length);
        for (int i = 0; i < lines; i++) {
            errorBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        
        if (stackTrace.length > 5) {
            errorBuilder.append("  ... and ").append(stackTrace.length - 5)
                       .append(" more lines\n");
        }
        
        errorBuilder.append("\n").append(SEPARATOR_LONG).append("\n");
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
        
        return errorBuilder.toString();
    }
}

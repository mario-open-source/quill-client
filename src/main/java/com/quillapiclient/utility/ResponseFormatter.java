package com.quillapiclient.utility;

import com.quillapiclient.server.ApiResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Utility class for formatting ApiResponse objects for display.
 * Provides consistent formatting across the application.
 */
public class ResponseFormatter {
    
    /**
     * Formats an ApiResponse for display in the ResponsePanel.
     * 
     * @param response The ApiResponse to format
     * @param timestampMessage Optional custom message for the timestamp line (e.g., "Response received" or "Response")
     * @return Formatted string representation of the response
     */
    public static String formatResponse(ApiResponse response, String timestampMessage) {
        if (response == null) {
            return "There is no response for this request";
        }
        
        StringBuilder responseText = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        // Response header
        String message = timestampMessage != null ? timestampMessage : "Response";
        responseText.append("[").append(timestamp).append("] ").append(message).append("\n");
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
        responseText.append(" ").append(statusCode).append(" ").append(getStatusText(statusCode));
        responseText.append(" (").append(response.getDuration()).append(" ms)\n\n");
        
        // Headers section
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            responseText.append("HEADERS (").append(response.getHeaders().size()).append("):\n");
            responseText.append("-".repeat(40)).append("\n");
            
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
            responseText.append("-".repeat(40)).append("\n");
            
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
        
        // Add request summary at the end
        responseText.append("\n").append("═".repeat(60)).append("\n");
        responseText.append("SUMMARY:\n");
        responseText.append("  • Status: ").append(statusCode)
                   .append(" (").append(response.isSuccess() ? "Success" : "Error").append(")\n");
        responseText.append("  • Time: ").append(response.getDuration()).append(" ms\n");
        
        if (response.getBody() != null) {
            responseText.append("  • Size: ").append(formatSize(response.getBody().length())).append("\n");
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
    private static String formatSize(int charCount) {
        if (charCount < 1024) {
            return charCount + " bytes";
        } else if (charCount < 1024 * 1024) {
            return String.format("%.1f KB", charCount / 1024.0);
        } else {
            return String.format("%.1f MB", charCount / (1024.0 * 1024.0));
        }
    }
}


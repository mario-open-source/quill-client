package com.quillapiclient.utility;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for replacing Postman collection variables in strings.
 * Variables are enclosed in double curly braces: {{variableName}}
 */
public class VariableReplacer {
    
    // Pattern to match {{variableName}} - non-greedy to handle multiple variables
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    /**
     * Replaces all variable placeholders in the given string with their values.
     * Variables are in the format {{variableName}}.
     * 
     * @param text The text containing variable placeholders
     * @param variables Map of variable names to their values (key = variable name, value = variable value)
     * @return The text with all variables replaced, or original text if no variables found or variables map is null/empty
     */
    public static String replaceVariables(String text, Map<String, String> variables) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        if (variables == null || variables.isEmpty()) {
            return text;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String variableValue = variables.get(variableName);
            
            if (variableValue != null) {
                // Replace the entire {{variableName}} with the value
                matcher.appendReplacement(result, Matcher.quoteReplacement(variableValue));
            } else {
                // Variable not found, keep the original placeholder
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Checks if a string contains any variable placeholders.
     * 
     * @param text The text to check
     * @return true if the text contains {{variableName}} patterns, false otherwise
     */
    public static boolean containsVariables(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(text).find();
    }
    
    /**
     * Extracts all variable names from a string.
     * 
     * @param text The text containing variable placeholders
     * @return List of variable names found in the text (without the {{}} brackets)
     */
    public static java.util.List<String> extractVariableNames(String text) {
        java.util.List<String> variableNames = new java.util.ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return variableNames;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            if (!variableName.isEmpty() && !variableNames.contains(variableName)) {
                variableNames.add(variableName);
            }
        }
        
        return variableNames;
    }
}


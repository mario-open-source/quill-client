package com.quillapiclient.utility;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves {{variable}} placeholders across request fields.
 * Supports merging multiple variable sources where later maps override earlier maps.
 */
public final class RequestVariableResolver {
    private final Map<String, String> variables;

    public RequestVariableResolver(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            this.variables = new HashMap<>();
        } else {
            this.variables = new HashMap<>(variables);
        }
    }

    public static Map<String, String> mergeVariables(Map<String, String> baseVariables,
                                                     Map<String, String> overridingVariables) {
        Map<String, String> merged = new HashMap<>();
        if (baseVariables != null && !baseVariables.isEmpty()) {
            merged.putAll(baseVariables);
        }
        if (overridingVariables != null && !overridingVariables.isEmpty()) {
            merged.putAll(overridingVariables);
        }
        return merged;
    }

    public String resolve(String text) {
        if (text == null || text.isEmpty() || variables.isEmpty() || !VariableReplacer.containsVariables(text)) {
            return text;
        }
        return VariableReplacer.replaceVariables(text, variables);
    }

    public Map<String, String> resolveMap(Map<String, String> originalValues) {
        Map<String, String> resolvedValues = new HashMap<>();
        if (originalValues == null || originalValues.isEmpty()) {
            return resolvedValues;
        }

        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            String resolvedKey = resolve(entry.getKey());
            String resolvedValue = resolve(entry.getValue());
            if (resolvedKey == null || resolvedKey.trim().isEmpty()) {
                continue;
            }
            resolvedValues.put(resolvedKey, resolvedValue != null ? resolvedValue : "");
        }

        return resolvedValues;
    }
}

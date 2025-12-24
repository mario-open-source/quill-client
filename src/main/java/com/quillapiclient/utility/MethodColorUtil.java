package com.quillapiclient.utility;

import java.awt.Color;

/**
 * Utility class for getting consistent colors for HTTP methods
 */
public class MethodColorUtil {
    
    /**
     * Gets the color for a given HTTP method
     * @param method The HTTP method (case-insensitive)
     * @return The color for the method
     */
    public static Color getMethodColor(String method) {
        if (method == null) {
            return new Color(158, 158, 158); // Medium Gray for null
        }
        
        switch (method.toUpperCase()) {
            case "GET":
                return new Color(0, 173, 87);
            case "POST":
                return new Color(252, 161, 48);
            case "PUT":
                return new Color(21, 124, 225);
            case "DELETE":
                return new Color(249, 62, 62);
            case "OPTIONS":
                return new Color(97, 175, 254);
            case "PATCH":
                return new Color(80, 227, 194);
            case "HEAD":
                return new Color(144, 18, 254);
            default:
                return new Color(158, 158, 158);    // Medium Gray for unknown methods
        }
    }
}


package com.quillapiclient;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.quillapiclient.db.LiteConnection;
import com.quillapiclient.utility.AppColorTheme;

public class Main {
    public static void main(String[] args) {
        // Set the FlatLaf look and feel with darker customizations
        try {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
            
            // Apply the dark theme colors
            AppColorTheme.applyDarkTheme();
            
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatLaf");
            ex.printStackTrace();
        }
        
        // Initialize database schema if it doesn't exist
        try {
            if (!com.quillapiclient.db.DatabaseSchema.schemaExists()) {
                com.quillapiclient.db.DatabaseSchema.initializeSchema();
            }
        } catch (Exception ex) {
            System.err.println("Failed to initialize database schema");
            ex.printStackTrace();
        }
        
        // Add shutdown hook to close database connection
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LiteConnection.closeConnection();
        }));
        
        SwingUtilities.invokeLater(() -> {
            Views views = new Views();
            views.show();
        });
    }
}

package com.quillapiclient.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages SQLite database connection for the application.
 * Creates the database file and directory structure if they don't exist.
 */
public class LiteConnection {
    private static final String APP_NAME = "quillclient";
    private static final String DB_DIR = System.getProperty("user.home") + File.separator + 
                                         "." + APP_NAME;
    private static final String DB_FILE = "app.db";
    private static final String DB_PATH = DB_DIR + File.separator + DB_FILE;
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    private static Connection connection;
    private static boolean driverLoaded = false;

    static {
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found. Make sure sqlite-jdbc is in the classpath.");
            e.printStackTrace();
        }
    }

    /**
     * Gets a connection to the SQLite database.
     * Creates the database file and directory structure if they don't exist.
     * 
     * @return Connection to the SQLite database
     * @throws RuntimeException if connection fails
     */
    public static Connection getConnection() {
        if (connection == null) {
            if (!driverLoaded) {
                throw new RuntimeException("SQLite JDBC driver not loaded");
            }
            
            try {
                // Ensure the directory exists
                File dbDir = new File(DB_DIR);
                if (!dbDir.exists()) {
                    boolean created = dbDir.mkdirs();
                    if (!created) {
                        throw new RuntimeException("Failed to create database directory: " + DB_DIR);
                    }
                }
                
                // Create connection (SQLite will create the file if it doesn't exist)
                connection = DriverManager.getConnection(DB_URL);
                
                // Enable foreign keys (recommended for SQLite)
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
                
                System.out.println("SQLite connected to: " + DB_PATH);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to connect to SQLite database at: " + DB_PATH, e);
            }
        }
        return connection;
    }

    /**
     * Closes the database connection.
     * Should be called when the application shuts down.
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("SQLite connection closed");
            } catch (SQLException e) {
                System.err.println("Error closing SQLite connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Checks if the connection is valid.
     * 
     * @return true if connection is valid, false otherwise
     */
    public static boolean isConnectionValid() {
        if (connection == null) {
            return false;
        }
        try {
            return connection.isValid(1); // 1 second timeout
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Gets the database file path.
     * 
     * @return The full path to the database file
     */
    public static String getDbPath() {
        return DB_PATH;
    }
}

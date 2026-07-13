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
    // Overridable so tests/benchmarks can point at a throwaway database
    private static final String DB_PATH = System.getProperty(
        "quill.db.path",
        DB_DIR + File.separator + DB_FILE
    );
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    // SQLite JDBC connections are not safe for concurrent use by multiple
    // threads. Long-lived background work (e.g. a streaming import that
    // holds an open transaction) must not share this singleton with the
    // EDT, or a concurrent read/write on the same Connection object can be
    // swept into the background transaction's commit/rollback. WAL mode
    // (enabled in DatabaseSchema) lets a dedicated connection opened via
    // openNewConnection() read/write concurrently with this one without
    // blocking, as long as two writers don't collide — busy_timeout below
    // makes a collision retry instead of failing immediately.
    private static final int BUSY_TIMEOUT_MS = 5000;

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
                File dbDir = new File(DB_PATH).getParentFile();
                if (dbDir != null && !dbDir.exists()) {
                    boolean created = dbDir.mkdirs();
                    if (!created) {
                        throw new RuntimeException("Failed to create database directory: " + dbDir);
                    }
                }
                
                // Create connection (SQLite will create the file if it doesn't exist)
                connection = DriverManager.getConnection(DB_URL);

                // Enable foreign keys (recommended for SQLite)
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
                connection
                    .createStatement()
                    .execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);

                System.out.println("SQLite connected to: " + DB_PATH);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to connect to SQLite database at: " + DB_PATH, e);
            }
        }
        return connection;
    }

    /**
     * Opens a standalone connection independent of the shared singleton
     * returned by {@link #getConnection()}, for background work that needs
     * its own transaction (e.g. a streaming import running on a
     * SwingWorker thread) without contending with the EDT for the same
     * Connection object. Callers are responsible for closing it.
     *
     * @return a new Connection to the same database file
     * @throws SQLException if the connection cannot be opened
     */
    public static Connection openNewConnection() throws SQLException {
        if (!driverLoaded) {
            throw new RuntimeException("SQLite JDBC driver not loaded");
        }
        Connection conn = DriverManager.getConnection(DB_URL);
        conn.createStatement().execute("PRAGMA foreign_keys = ON");
        conn
            .createStatement()
            .execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
        return conn;
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

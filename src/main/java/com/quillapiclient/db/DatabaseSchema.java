package com.quillapiclient.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and initializes the database schema for Postman collections.
 * Optimized for performance with frequently accessed fields (URL, method, headers, body).
 */
public class DatabaseSchema {
    
    /**
     * Initializes the database schema if it doesn't exist.
     * Creates all necessary tables with indexes for optimal performance.
     * 
     * @throws SQLException if schema creation fails
     */
    public static void initializeSchema() throws SQLException {
        Connection conn = LiteConnection.getConnection();
        
        try (Statement stmt = conn.createStatement()) {
            // Enable WAL mode for better concurrency
            stmt.execute("PRAGMA journal_mode = WAL");
            
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON");
            
            // Create collections table (Info from PostmanCollection)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS collections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    postman_id TEXT UNIQUE,
                    name TEXT NOT NULL,
                    schema_version TEXT,
                    exporter_id TEXT,
                    description TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // Create items table (hierarchical structure for folders and requests)
            // Most frequently accessed: name, parent_id, collection_id
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    collection_id INTEGER NOT NULL,
                    parent_id INTEGER,
                    name TEXT NOT NULL,
                    item_type TEXT NOT NULL CHECK(item_type IN ('folder', 'request')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                    FOREIGN KEY (parent_id) REFERENCES items(id) ON DELETE CASCADE
                )
            """);
            
            // Create requests table - DENORMALIZED for performance
            // Stores most frequently accessed fields directly (URL, method, body)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id INTEGER NOT NULL UNIQUE,
                    method TEXT NOT NULL,
                    url_raw TEXT NOT NULL,
                    url_protocol TEXT,
                    url_port TEXT,
                    body_mode TEXT,
                    body_raw TEXT,
                    body_language TEXT,
                    auth_type TEXT,
                    auth_basic_username TEXT,
                    auth_basic_password TEXT,
                    auth_bearer_token TEXT,
                    full_url_json TEXT,
                    full_body_json TEXT,
                    full_auth_json TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
                )
            """);
            
            // Create headers table (one-to-many with requests)
            // Frequently accessed, so separate table for querying
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS headers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    header_key TEXT NOT NULL,
                    header_value TEXT,
                    disabled INTEGER DEFAULT 0,
                    sort_order INTEGER DEFAULT 0,
                    FOREIGN KEY (request_id) REFERENCES requests(id) ON DELETE CASCADE
                )
            """);
            
            // Create query_params table (one-to-many with requests)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS query_params (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    param_key TEXT NOT NULL,
                    param_value TEXT,
                    sort_order INTEGER DEFAULT 0,
                    FOREIGN KEY (request_id) REFERENCES requests(id) ON DELETE CASCADE
                )
            """);
            
            // Create variables table (can be collection-level or item-level)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS variables (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    collection_id INTEGER,
                    item_id INTEGER,
                    variable_key TEXT NOT NULL,
                    variable_value TEXT,
                    variable_type TEXT,
                    CHECK((collection_id IS NOT NULL AND item_id IS NULL) OR 
                          (collection_id IS NULL AND item_id IS NOT NULL)),
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
                )
            """);
            
            // Create events table (pre-request scripts, test scripts)
            // Stored as JSON for flexibility
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    collection_id INTEGER,
                    item_id INTEGER,
                    event_type TEXT NOT NULL CHECK(event_type IN ('prerequest', 'test')),
                    script_type TEXT,
                    script_exec TEXT,
                    full_event_json TEXT,
                    CHECK((collection_id IS NOT NULL AND item_id IS NULL) OR 
                          (collection_id IS NULL AND item_id IS NOT NULL)),
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
                )
            """);
            
            // Create responses table (stores API call responses)
            // Linked to requests via request_id
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS responses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    status_code INTEGER NOT NULL,
                    body TEXT,
                    duration INTEGER,
                    full_response_json TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (request_id) REFERENCES requests(id) ON DELETE CASCADE
                )
            """);
            
            // Create response_headers table (one-to-many with responses)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS response_headers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    response_id INTEGER NOT NULL,
                    header_key TEXT NOT NULL,
                    header_value TEXT,
                    sort_order INTEGER DEFAULT 0,
                    FOREIGN KEY (response_id) REFERENCES responses(id) ON DELETE CASCADE
                )
            """);
            
            // Create indexes for frequently queried fields
            createIndexes(stmt);
            
            // Create triggers for updated_at timestamps
            createTriggers(stmt);
            
            System.out.println("Database schema initialized successfully");
        }
    }
    
    /**
     * Creates indexes on frequently queried columns for optimal performance.
     */
    private static void createIndexes(Statement stmt) throws SQLException {
        // Collection lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_collections_postman_id ON collections(postman_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_collections_name ON collections(name)");
        
        // Item hierarchy and lookups (most common queries)
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_items_collection_id ON items(collection_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_items_parent_id ON items(parent_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_items_name ON items(name)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_items_type ON items(item_type)");
        
        // Request lookups (most frequently accessed)
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_requests_item_id ON requests(item_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_requests_method ON requests(method)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_requests_url_raw ON requests(url_raw)");
        // Full-text search on URL (SQLite FTS5 would be better, but this works)
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_requests_url_search ON requests(url_raw COLLATE NOCASE)");
        
        // Header lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_headers_request_id ON headers(request_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_headers_key ON headers(header_key)");
        
        // Query param lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_query_params_request_id ON query_params(request_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_query_params_key ON query_params(param_key)");
        
        // Variable lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_variables_collection_id ON variables(collection_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_variables_item_id ON variables(item_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_variables_key ON variables(variable_key)");
        
        // Event lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_collection_id ON events(collection_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_item_id ON events(item_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_type ON events(event_type)");
        
        // Response lookups (most frequently accessed)
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_responses_request_id ON responses(request_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_responses_status_code ON responses(status_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_responses_created_at ON responses(created_at DESC)");
        
        // Response header lookups
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_response_headers_response_id ON response_headers(response_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_response_headers_key ON response_headers(header_key)");
    }
    
    /**
     * Creates triggers to automatically update the updated_at timestamp.
     */
    private static void createTriggers(Statement stmt) throws SQLException {
        // Collections
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS update_collections_timestamp 
            AFTER UPDATE ON collections
            BEGIN
                UPDATE collections SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
        """);
        
        // Items
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS update_items_timestamp 
            AFTER UPDATE ON items
            BEGIN
                UPDATE items SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
        """);
        
        // Requests
        stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS update_requests_timestamp 
            AFTER UPDATE ON requests
            BEGIN
                UPDATE requests SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
            END
        """);
    }
    
    /**
     * Checks if the schema has been initialized by checking if tables exist.
     * 
     * @return true if schema exists, false otherwise
     */
    public static boolean schemaExists() {
        try {
            Connection conn = LiteConnection.getConnection();
            try (Statement stmt = conn.createStatement()) {
                // Check if the main tables exist
                stmt.executeQuery("SELECT 1 FROM collections LIMIT 1");
                stmt.executeQuery("SELECT 1 FROM items LIMIT 1");
                stmt.executeQuery("SELECT 1 FROM requests LIMIT 1");
                stmt.executeQuery("SELECT 1 FROM responses LIMIT 1");
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Drops all tables (use with caution - for testing/reset only).
     * 
     * @throws SQLException if drop fails
     */
    public static void dropSchema() throws SQLException {
        Connection conn = LiteConnection.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // Drop in reverse order of dependencies
            stmt.execute("DROP TABLE IF EXISTS response_headers");
            stmt.execute("DROP TABLE IF EXISTS responses");
            stmt.execute("DROP TABLE IF EXISTS events");
            stmt.execute("DROP TABLE IF EXISTS variables");
            stmt.execute("DROP TABLE IF EXISTS query_params");
            stmt.execute("DROP TABLE IF EXISTS headers");
            stmt.execute("DROP TABLE IF EXISTS requests");
            stmt.execute("DROP TABLE IF EXISTS items");
            stmt.execute("DROP TABLE IF EXISTS collections");
            System.out.println("Database schema dropped");
        }
    }
}


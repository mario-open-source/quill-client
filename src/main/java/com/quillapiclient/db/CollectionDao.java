package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.*;
import com.quillapiclient.server.ApiResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing Postman collections in SQLite database.
 * Handles saving collections and querying them for UI building.
 */
public class CollectionDao {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Saves a Postman collection to the database.
     * If a collection with the same postman_id exists, it will be replaced.
     * 
     * @param collection The Postman collection to save
     * @param fileName The name of the file (used as collection name if info.name is null)
     * @return The collection ID in the database, or -1 if operation fails
     */
    public static int saveCollection(PostmanCollection collection, String fileName) {
        Connection conn = LiteConnection.getConnection();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            System.err.println("Error setting auto-commit to false: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
        
        try {
            Info info = collection.getInfo();
            String postmanId = info != null && info.getPostmanId() != null ? info.getPostmanId() : null;
            String collectionName = (info != null && info.getName() != null) ? info.getName() : fileName;
            String schemaVersion = info != null ? info.getSchema() : null;
            String exporterId = info != null ? info.getExporterId() : null;
            String description = info != null ? info.getDescription() : null;
            
            // Check if collection already exists
            int collectionId;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM collections WHERE postman_id = ?")) {
                checkStmt.setString(1, postmanId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    collectionId = rs.getInt("id");
                    // Delete existing collection (cascade will delete all related data)
                    try (PreparedStatement deleteStmt = conn.prepareStatement(
                            "DELETE FROM collections WHERE id = ?")) {
                        deleteStmt.setInt(1, collectionId);
                        deleteStmt.executeUpdate();
                    }
                } else {
                    collectionId = -1; // Will be set after insert
                }
            }
            
            // Insert collection
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO collections (postman_id, name, schema_version, exporter_id, description) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, postmanId);
                stmt.setString(2, collectionName);
                stmt.setString(3, schemaVersion);
                stmt.setString(4, exporterId);
                stmt.setString(5, description);
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    collectionId = rs.getInt(1);
                }
            }
            
            // Save collection-level variables
            if (collection.getVariable() != null) {
                saveVariables(conn, collectionId, null, collection.getVariable());
            }
            
            // Save collection-level events
            if (collection.getEvent() != null) {
                saveEvents(conn, collectionId, null, collection.getEvent());
            }
            
            // Save items recursively
            if (collection.getItem() != null) {
                for (Item item : collection.getItem()) {
                    saveItem(conn, collectionId, null, item);
                }
            }
            
            conn.commit();
            return collectionId;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
            System.err.println("Error saving collection to database: " + e.getMessage());
            e.printStackTrace();
            return -1;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Recursively saves an item (folder or request) to the database.
     */
    private static int saveItem(Connection conn, int collectionId, Integer parentId, Item item) {
        String itemType = item.getRequest() != null ? "request" : "folder";
        
        // Insert item
        int itemId = -1;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO items (collection_id, parent_id, name, item_type) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, collectionId);
            if (parentId != null) {
                stmt.setInt(2, parentId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, item.getName());
            stmt.setString(4, itemType);
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                itemId = rs.getInt(1);
            } else {
                System.err.println("Failed to get generated key for item: " + item.getName());
                return -1;
            }
        } catch (SQLException e) {
            System.err.println("Error saving item to database: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
        
        // Save item-level variables
        if (item.getVariable() != null) {
            saveVariables(conn, collectionId, itemId, item.getVariable());
        }
        
        // If it's a request, save request details
        if (item.getRequest() != null) {
            saveRequest(conn, itemId, item.getRequest());
        }
        
        // Recursively save child items
        if (item.getItem() != null) {
            for (Item child : item.getItem()) {
                saveItem(conn, collectionId, itemId, child);
            }
        }
        
        return itemId;
    }
    
    /**
     * Saves a request to the database.
     */
    private static void saveRequest(Connection conn, int itemId, Request request) {
        String method = request.getMethod() != null ? request.getMethod() : "";
        String urlRaw = request.getUrl() != null && request.getUrl().getRaw() != null 
                ? request.getUrl().getRaw() : "";
        String urlProtocol = request.getUrl() != null ? request.getUrl().getProtocol() : null;
        String urlPort = request.getUrl() != null ? request.getUrl().getPort() : null;
        String bodyMode = request.getBody() != null ? request.getBody().getMode() : null;
        String bodyRaw = request.getBody() != null ? request.getBody().getRaw() : null;
        String bodyLanguage = request.getBody() != null && request.getBody().getOptions() != null
                && request.getBody().getOptions().getRaw() != null
                ? request.getBody().getOptions().getRaw().getLanguage() : null;
        
        // Serialize complex objects to JSON
        String fullUrlJson = null;
        String fullBodyJson = null;
        String fullAuthJson = null;
        try {
            fullUrlJson = request.getUrl() != null 
                    ? objectMapper.writeValueAsString(request.getUrl()) : null;
            fullBodyJson = request.getBody() != null 
                    ? objectMapper.writeValueAsString(request.getBody()) : null;
            fullAuthJson = request.getAuth() != null 
                    ? objectMapper.writeValueAsString(request.getAuth()) : null;
        } catch (JsonProcessingException e) {
            System.err.println("Error serializing request data to JSON: " + e.getMessage());
            e.printStackTrace();
            return; // Exit early if serialization fails
        }
        
        // Extract auth details
        String authType = request.getAuth() != null ? request.getAuth().getType() : null;
        String authBasicUsername = null;
        String authBasicPassword = null;
        String authBearerToken = null;
        
        if (request.getAuth() != null) {
            if ("basic".equalsIgnoreCase(authType) && request.getAuth().getBasic() != null) {
                for (Credential cred : request.getAuth().getBasic()) {
                    if ("username".equals(cred.getKey())) {
                        authBasicUsername = cred.getValue();
                    } else if ("password".equals(cred.getKey())) {
                        authBasicPassword = cred.getValue();
                    }
                }
            } else if ("bearer".equalsIgnoreCase(authType) && request.getAuth().getBearer() != null) {
                for (Credential cred : request.getAuth().getBearer()) {
                    if ("token".equals(cred.getKey())) {
                        authBearerToken = cred.getValue();
                    }
                }
            }
        }
        
        // Insert request
        int requestId = -1;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO requests (item_id, method, url_raw, url_protocol, url_port, " +
                "body_mode, body_raw, body_language, auth_type, auth_basic_username, " +
                "auth_basic_password, auth_bearer_token, full_url_json, full_body_json, full_auth_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, itemId);
            stmt.setString(2, method);
            stmt.setString(3, urlRaw);
            stmt.setString(4, urlProtocol);
            stmt.setString(5, urlPort);
            stmt.setString(6, bodyMode);
            stmt.setString(7, bodyRaw);
            stmt.setString(8, bodyLanguage);
            stmt.setString(9, authType);
            stmt.setString(10, authBasicUsername);
            stmt.setString(11, authBasicPassword);
            stmt.setString(12, authBearerToken);
            stmt.setString(13, fullUrlJson);
            stmt.setString(14, fullBodyJson);
            stmt.setString(15, fullAuthJson);
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                requestId = rs.getInt(1);
            } else {
                System.err.println("Failed to get generated key for request");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error saving request to database: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // Save headers
        if (request.getHeader() != null) {
            int sortOrder = 0;
            for (Header header : request.getHeader()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO headers (request_id, header_key, header_value, disabled, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
                    stmt.setInt(1, requestId);
                    stmt.setString(2, header.getKey());
                    stmt.setString(3, header.getValue());
                    stmt.setInt(4, header.getDisabled() != null && header.getDisabled() ? 1 : 0);
                    stmt.setInt(5, sortOrder++);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("Error saving header to database: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // Save query parameters
        if (request.getUrl() != null && request.getUrl().getQuery() != null) {
            int sortOrder = 0;
            for (Query query : request.getUrl().getQuery()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO query_params (request_id, param_key, param_value, sort_order) " +
                        "VALUES (?, ?, ?, ?)")) {
                    stmt.setInt(1, requestId);
                    stmt.setString(2, query.getKey());
                    stmt.setString(3, query.getValue());
                    stmt.setInt(4, sortOrder++);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("Error saving query parameter to database: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Saves variables to the database.
     */
    private static void saveVariables(Connection conn, Integer collectionId, Integer itemId, 
                                     List<Variable> variables) {
        for (Variable variable : variables) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO variables (collection_id, item_id, variable_key, variable_value, variable_type) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                if (collectionId != null) {
                    stmt.setInt(1, collectionId);
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setNull(1, Types.INTEGER);
                    stmt.setInt(2, itemId);
                }
                stmt.setString(3, variable.getKey());
                stmt.setString(4, variable.getValue());
                stmt.setString(5, variable.getType());
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Error saving variable to database: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Saves events to the database.
     */
    private static void saveEvents(Connection conn, Integer collectionId, Integer itemId, 
                                  List<Event> events) {
        for (Event event : events) {
            String eventJson = null;
            try {
                eventJson = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing event data to JSON: " + e.getMessage());
                e.printStackTrace();
                continue; // Skip this event if serialization fails
            }
            
            String eventType = event.getListen() != null ? event.getListen() : null;
            String scriptType = null;
            String scriptExec = null;
            
            if (event.getScript() != null) {
                scriptType = event.getScript().getType();
                if (event.getScript().getExec() != null) {
                    scriptExec = String.join("\n", event.getScript().getExec());
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO events (collection_id, item_id, event_type, script_type, script_exec, full_event_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                if (collectionId != null) {
                    stmt.setInt(1, collectionId);
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setNull(1, Types.INTEGER);
                    stmt.setInt(2, itemId);
                }
                stmt.setString(3, eventType);
                stmt.setString(4, scriptType);
                stmt.setString(5, scriptExec);
                stmt.setString(6, eventJson);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Error saving event to database: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Gets all collections from the database.
     * 
     * @return List of collection IDs and names
     */
    public static List<CollectionInfo> getAllCollections() {
        List<CollectionInfo> collections = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name FROM collections ORDER BY created_at DESC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                collections.add(new CollectionInfo(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all collections from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return collections;
    }
    
    /**
     * Gets the most recently loaded collection ID.
     * 
     * @return The collection ID, or -1 if no collections exist
     */
    public static int getLatestCollectionId() {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM collections ORDER BY created_at DESC LIMIT 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error getting latest collection ID from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return -1;
    }
    
    /**
     * Gets all root items (items with no parent) for a collection.
     * 
     * @param collectionId The collection ID
     * @return List of item IDs and names
     */
    public static List<ItemInfo> getRootItems(int collectionId) {
        List<ItemInfo> items = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, item_type FROM items WHERE collection_id = ? AND parent_id IS NULL ORDER BY id")) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(new ItemInfo(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("item_type")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting root items from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return items;
    }
    
    /**
     * Gets all child items for a given parent item.
     * 
     * @param parentId The parent item ID
     * @return List of item IDs and names
     */
    public static List<ItemInfo> getChildItems(int parentId) {
        List<ItemInfo> items = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, item_type FROM items WHERE parent_id = ? ORDER BY id")) {
            stmt.setInt(1, parentId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(new ItemInfo(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("item_type")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting child items from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return items;
    }
    
    /**
     * Gets a request by item ID.
     * Reconstructs the Request object from database records.
     * 
     * @param itemId The item ID
     * @return The Request object, or null if not found
     */
    public static Request getRequestByItemId(int itemId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM requests WHERE item_id = ?")) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                return null;
            }
            
            Request request = new Request();
            request.setMethod(rs.getString("method"));
            
            // Reconstruct URL from JSON or individual fields
            Url url = null;
            String fullUrlJson = rs.getString("full_url_json");
            if (fullUrlJson != null && !fullUrlJson.isEmpty()) {
                try {
                    url = objectMapper.readValue(fullUrlJson, Url.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    url = new Url();
                    url.setRaw(rs.getString("url_raw"));
                    url.setProtocol(rs.getString("url_protocol"));
                    url.setPort(rs.getString("url_port"));
                }
            } else {
                url = new Url();
                url.setRaw(rs.getString("url_raw"));
                url.setProtocol(rs.getString("url_protocol"));
                url.setPort(rs.getString("url_port"));
            }
            request.setUrl(url);
            
            // Reconstruct Body from JSON or individual fields
            Body body = null;
            String fullBodyJson = rs.getString("full_body_json");
            if (fullBodyJson != null && !fullBodyJson.isEmpty()) {
                try {
                    body = objectMapper.readValue(fullBodyJson, Body.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    body = new Body();
                    body.setMode(rs.getString("body_mode"));
                    body.setRaw(rs.getString("body_raw"));
                }
            } else {
                body = new Body();
                body.setMode(rs.getString("body_mode"));
                body.setRaw(rs.getString("body_raw"));
            }
            request.setBody(body);
            
            // Reconstruct Auth from JSON or individual fields
            Auth auth = null;
            String fullAuthJson = rs.getString("full_auth_json");
            if (fullAuthJson != null && !fullAuthJson.isEmpty()) {
                try {
                    auth = objectMapper.readValue(fullAuthJson, Auth.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    auth = reconstructAuth(rs);
                }
            } else {
                auth = reconstructAuth(rs);
            }
            request.setAuth(auth);
            
            // Load headers
            int requestId = rs.getInt("id");
            List<Header> headers = getHeaders(requestId);
            request.setHeader(headers);
            
            // Load query parameters
            List<Query> queries = getQueryParams(requestId);
            if (queries != null && !queries.isEmpty() && url != null) {
                url.setQuery(queries);
            }
            
            return request;
        } catch (SQLException e) {
            System.err.println("Error getting request by item ID from database: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("Error deserializing request data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Reconstructs Auth object from database fields.
     */
    private static Auth reconstructAuth(ResultSet rs) {
        try {
            String authType = rs.getString("auth_type");
            if (authType == null) {
                return null;
            }
            
            Auth auth = new Auth();
            auth.setType(authType);
            
            if ("basic".equalsIgnoreCase(authType)) {
                List<Credential> basic = new ArrayList<>();
                String username = rs.getString("auth_basic_username");
                String password = rs.getString("auth_basic_password");
                if (username != null) {
                    Credential cred = new Credential();
                    cred.setKey("username");
                    cred.setValue(username);
                    basic.add(cred);
                }
                if (password != null) {
                    Credential cred = new Credential();
                    cred.setKey("password");
                    cred.setValue(password);
                    basic.add(cred);
                }
                auth.setBasic(basic);
            } else if ("bearer".equalsIgnoreCase(authType)) {
                List<Credential> bearer = new ArrayList<>();
                String token = rs.getString("auth_bearer_token");
                if (token != null) {
                    Credential cred = new Credential();
                    cred.setKey("token");
                    cred.setValue(token);
                    bearer.add(cred);
                }
                auth.setBearer(bearer);
            }
            
            return auth;
        } catch (SQLException e) {
            System.err.println("Error reconstructing auth from database: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Gets all headers for a request.
     */
    private static List<Header> getHeaders(int requestId) {
        List<Header> headers = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT header_key, header_value, disabled FROM headers WHERE request_id = ? ORDER BY sort_order")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Header header = new Header();
                header.setKey(rs.getString("header_key"));
                header.setValue(rs.getString("header_value"));
                header.setDisabled(rs.getInt("disabled") == 1);
                headers.add(header);
            }
        } catch (SQLException e) {
            System.err.println("Error getting headers from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return headers;
    }
    
    /**
     * Gets all query parameters for a request.
     */
    private static List<Query> getQueryParams(int requestId) {
        List<Query> queries = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT param_key, param_value FROM query_params WHERE request_id = ? ORDER BY sort_order")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Query query = new Query();
                query.setKey(rs.getString("param_key"));
                query.setValue(rs.getString("param_value"));
                queries.add(query);
            }
        } catch (SQLException e) {
            System.err.println("Error getting query parameters from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return queries;
    }
    
    /**
     * Gets the item name by item ID.
     */
    public static String getItemName(int itemId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM items WHERE id = ?")) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            System.err.println("Error getting item name from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Gets the item ID by item name within a collection.
     * Note: This may return multiple results if names are not unique.
     * For now, returns the first match.
     */
    public static int getItemIdByName(int collectionId, String itemName) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM items WHERE collection_id = ? AND name = ? LIMIT 1")) {
            stmt.setInt(1, collectionId);
            stmt.setString(2, itemName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error getting item ID by name from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return -1;
    }
    
    /**
     * Gets the request ID by item ID.
     * 
     * @param itemId The item ID
     * @return The request ID, or -1 if not found
     */
    public static int getRequestIdByItemId(int itemId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM requests WHERE item_id = ? LIMIT 1")) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error getting request ID by item ID from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return -1;
    }
    
    /**
     * Updates a request in the database by item ID.
     * 
     * @param itemId The item ID of the request to update
     * @param request The updated Request object
     * @return true if update was successful, false otherwise
     */
    public static boolean updateRequest(int itemId, Request request) {
        Connection conn = LiteConnection.getConnection();
        
        try {
            conn.setAutoCommit(false);
            
            // Get the request ID directly from the connection
            int requestId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM requests WHERE item_id = ? LIMIT 1")) {
                stmt.setInt(1, itemId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    requestId = rs.getInt("id");
                }
            }
            
            if (requestId <= 0) {
                System.err.println("Request not found for item ID: " + itemId);
                conn.rollback();
                return false;
            }
            
            // Extract request data
            String method = request.getMethod() != null ? request.getMethod() : "";
            String urlRaw = request.getUrl() != null && request.getUrl().getRaw() != null 
                    ? request.getUrl().getRaw() : "";
            String urlProtocol = request.getUrl() != null ? request.getUrl().getProtocol() : null;
            String urlPort = request.getUrl() != null ? request.getUrl().getPort() : null;
            String bodyMode = request.getBody() != null ? request.getBody().getMode() : null;
            String bodyRaw = request.getBody() != null ? request.getBody().getRaw() : null;
            String bodyLanguage = request.getBody() != null && request.getBody().getOptions() != null
                    && request.getBody().getOptions().getRaw() != null
                    ? request.getBody().getOptions().getRaw().getLanguage() : null;
            
            // Serialize complex objects to JSON
            String fullUrlJson = null;
            String fullBodyJson = null;
            String fullAuthJson = null;
            try {
                fullUrlJson = request.getUrl() != null 
                        ? objectMapper.writeValueAsString(request.getUrl()) : null;
                fullBodyJson = request.getBody() != null 
                        ? objectMapper.writeValueAsString(request.getBody()) : null;
                fullAuthJson = request.getAuth() != null 
                        ? objectMapper.writeValueAsString(request.getAuth()) : null;
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing request data to JSON: " + e.getMessage());
                e.printStackTrace();
                conn.rollback();
                return false;
            }
            
            // Extract auth details
            String authType = null;
            String authBasicUsername = null;
            String authBasicPassword = null;
            String authBearerToken = null;
            
            if (request.getAuth() != null) {
                if (request.getAuth().getBasic() != null && !request.getAuth().getBasic().isEmpty()) {
                    authType = "basic";
                    if (request.getAuth().getBasic().size() > 0) {
                        authBasicUsername = request.getAuth().getBasic().get(0).getValue();
                    }
                    if (request.getAuth().getBasic().size() > 1) {
                        authBasicPassword = request.getAuth().getBasic().get(1).getValue();
                    }
                } else if (request.getAuth().getBearer() != null && !request.getAuth().getBearer().isEmpty()) {
                    authType = "bearer";
                    if (request.getAuth().getBearer().size() > 0) {
                        authBearerToken = request.getAuth().getBearer().get(0).getValue();
                    }
                }
            }
            
            // Update request
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE requests SET method = ?, url_raw = ?, url_protocol = ?, url_port = ?, " +
                    "body_mode = ?, body_raw = ?, body_language = ?, auth_type = ?, auth_basic_username = ?, " +
                    "auth_basic_password = ?, auth_bearer_token = ?, full_url_json = ?, full_body_json = ?, " +
                    "full_auth_json = ?, updated_at = CURRENT_TIMESTAMP WHERE item_id = ?")) {
                stmt.setString(1, method);
                stmt.setString(2, urlRaw);
                stmt.setString(3, urlProtocol);
                stmt.setString(4, urlPort);
                stmt.setString(5, bodyMode);
                stmt.setString(6, bodyRaw);
                stmt.setString(7, bodyLanguage);
                stmt.setString(8, authType);
                stmt.setString(9, authBasicUsername);
                stmt.setString(10, authBasicPassword);
                stmt.setString(11, authBearerToken);
                stmt.setString(12, fullUrlJson);
                stmt.setString(13, fullBodyJson);
                stmt.setString(14, fullAuthJson);
                stmt.setInt(15, itemId);
                stmt.executeUpdate();
            }
            
            // Delete existing headers and query params
            try (PreparedStatement deleteHeaders = conn.prepareStatement(
                    "DELETE FROM headers WHERE request_id = ?")) {
                deleteHeaders.setInt(1, requestId);
                deleteHeaders.executeUpdate();
            }
            
            try (PreparedStatement deleteParams = conn.prepareStatement(
                    "DELETE FROM query_params WHERE request_id = ?")) {
                deleteParams.setInt(1, requestId);
                deleteParams.executeUpdate();
            }
            
            // Save new headers
            if (request.getHeader() != null) {
                int sortOrder = 0;
                for (Header header : request.getHeader()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO headers (request_id, header_key, header_value, disabled, sort_order) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                        stmt.setInt(1, requestId);
                        stmt.setString(2, header.getKey());
                        stmt.setString(3, header.getValue());
                        stmt.setInt(4, header.getDisabled() != null && header.getDisabled() ? 1 : 0);
                        stmt.setInt(5, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println("Error saving header to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            // Save new query parameters
            if (request.getUrl() != null && request.getUrl().getQuery() != null) {
                int sortOrder = 0;
                for (Query query : request.getUrl().getQuery()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO query_params (request_id, param_key, param_value, sort_order) " +
                            "VALUES (?, ?, ?, ?)")) {
                        stmt.setInt(1, requestId);
                        stmt.setString(2, query.getKey());
                        stmt.setString(3, query.getValue());
                        stmt.setInt(4, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println("Error saving query parameter to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Error updating request in database: " + e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Saves an API response to the database.
     * 
     * @param response The ApiResponse object to save
     * @param requestId The request ID this response belongs to
     * @return The response ID in the database, or -1 if operation fails
     */
    public static int saveResponse(ApiResponse response, int requestId) {
        Connection conn = LiteConnection.getConnection();
        
        try {
            // Serialize full response to JSON for flexibility
            String fullResponseJson = null;
            try {
                fullResponseJson = objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing response to JSON: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Insert response
            int responseId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO responses (request_id, status_code, body, duration, full_response_json) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, requestId);
                stmt.setInt(2, response.getStatusCode());
                stmt.setString(3, response.getBody());
                stmt.setLong(4, response.getDuration());
                stmt.setString(5, fullResponseJson);
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    responseId = rs.getInt(1);
                } else {
                    System.err.println("Failed to get generated key for response");
                    return -1;
                }
            }
            
            // Save response headers
            if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
                int sortOrder = 0;
                for (Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                    String headerKey = entry.getKey();
                    java.util.List<String> headerValues = entry.getValue();
                    // Join multiple values with comma
                    String headerValue = String.join(", ", headerValues);
                    
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO response_headers (response_id, header_key, header_value, sort_order) " +
                            "VALUES (?, ?, ?, ?)")) {
                        stmt.setInt(1, responseId);
                        stmt.setString(2, headerKey);
                        stmt.setString(3, headerValue);
                        stmt.setInt(4, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println("Error saving response header to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            return responseId;
        } catch (SQLException e) {
            System.err.println("Error saving response to database: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Gets a response by its ID.
     * Reconstructs the ApiResponse object from database records.
     * 
     * @param responseId The response ID
     * @return The ApiResponse object, or null if not found
     */
    public static ApiResponse getResponse(int responseId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM responses WHERE id = ?")) {
            stmt.setInt(1, responseId);
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                return null;
            }
            
            ApiResponse response = new ApiResponse();
            response.setStatusCode(rs.getInt("status_code"));
            response.setBody(rs.getString("body"));
            response.setDuration(rs.getLong("duration"));
            
            // Reconstruct headers
            Map<String, java.util.List<String>> headers = getResponseHeaders(responseId);
            response.setHeaders(headers);
            
            return response;
        } catch (SQLException e) {
            System.err.println("Error getting response from database: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Gets all responses for a specific request, ordered by creation time (newest first).
     * 
     * @param requestId The request ID
     * @return List of ApiResponse objects
     */
    public static List<ApiResponse> getResponsesByRequestId(int requestId) {
        List<ApiResponse> responses = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM responses WHERE request_id = ? ORDER BY created_at DESC")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int responseId = rs.getInt("id");
                ApiResponse response = getResponse(responseId);
                if (response != null) {
                    responses.add(response);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting responses by request ID from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return responses;
    }
    
    /**
     * Gets the most recent response for a specific request.
     * 
     * @param requestId The request ID
     * @return The ApiResponse object, or null if not found
     */
    public static ApiResponse getLatestResponseByRequestId(int requestId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM responses WHERE request_id = ? ORDER BY created_at DESC LIMIT 1")) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int responseId = rs.getInt("id");
                return getResponse(responseId);
            }
        } catch (SQLException e) {
            System.err.println("Error getting latest response by request ID from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Gets all response headers for a response.
     * 
     * @param responseId The response ID
     * @return Map of header keys to lists of values
     */
    private static Map<String, java.util.List<String>> getResponseHeaders(int responseId) {
        Map<String, java.util.List<String>> headers = new HashMap<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT header_key, header_value FROM response_headers WHERE response_id = ? ORDER BY sort_order")) {
            stmt.setInt(1, responseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString("header_key");
                String value = rs.getString("header_value");
                
                // Split comma-separated values back into list
                if (value != null && !value.isEmpty()) {
                    java.util.List<String> values = new ArrayList<>();
                    String[] parts = value.split(", ");
                    for (String part : parts) {
                        values.add(part.trim());
                    }
                    headers.put(key, values);
                } else {
                    headers.put(key, new ArrayList<>());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting response headers from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return headers;
    }
    
    /**
     * Simple data class for collection information.
     */
    public static class CollectionInfo {
        public final int id;
        public final String name;
        
        public CollectionInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    
    /**
     * Gets all items for a collection in a single query.
     * This is much more efficient than querying recursively (solves N+1 problem).
     * 
     * @param collectionId The collection ID
     * @return CollectionItemsData containing all items, relationships, and request methods
     */
    public static CollectionItemsData getAllItemsForCollection(int collectionId) {
        Map<Integer, ItemInfo> itemsMap = new HashMap<>();
        Map<Integer, List<Integer>> childrenMap = new HashMap<>();
        Map<Integer, String> requestMethodsMap = new HashMap<>();
        java.util.Set<Integer> childItemIds = new java.util.HashSet<>(); // Track which items are children
        
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT i.id, i.name, i.item_type, i.parent_id, r.method " +
                "FROM items i " +
                "LEFT JOIN requests r ON i.id = r.item_id " +
                "WHERE i.collection_id = ? ORDER BY i.id")) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int itemId = rs.getInt("id");
                String name = rs.getString("name");
                String itemType = rs.getString("item_type");
                Integer parentId = rs.getObject("parent_id") != null ? rs.getInt("parent_id") : null;
                String method = rs.getString("method");
                
                // Store item info
                itemsMap.put(itemId, new ItemInfo(itemId, name, itemType));
                
                // Store request method if available
                if (method != null && !method.isEmpty()) {
                    requestMethodsMap.put(itemId, method.toUpperCase());
                }
                
                // Build parent-child relationship map
                if (parentId != null) {
                    childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(itemId);
                    childItemIds.add(itemId); // Mark this item as a child
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting all items for collection from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return new CollectionItemsData(itemsMap, childrenMap, requestMethodsMap, childItemIds);
    }
    
    /**
     * Data class containing all items for a collection.
     * Provides efficient in-memory access to avoid N+1 query problems.
     */
    public static class CollectionItemsData {
        public final Map<Integer, ItemInfo> itemsMap; // itemId -> ItemInfo
        public final Map<Integer, List<Integer>> childrenMap; // parentId -> list of child itemIds
        public final Map<Integer, String> requestMethodsMap; // itemId -> HTTP method (only for requests)
        private final java.util.Set<Integer> childItemIds; // Set of all item IDs that are children
        
        public CollectionItemsData(Map<Integer, ItemInfo> itemsMap, 
                                  Map<Integer, List<Integer>> childrenMap,
                                  Map<Integer, String> requestMethodsMap,
                                  java.util.Set<Integer> childItemIds) {
            this.itemsMap = itemsMap;
            this.childrenMap = childrenMap;
            this.requestMethodsMap = requestMethodsMap;
            this.childItemIds = childItemIds;
        }
        
        /**
         * Gets root items (items with no parent).
         * Root items are those that are not in the childItemIds set.
         */
        public List<ItemInfo> getRootItems() {
            List<ItemInfo> rootItems = new ArrayList<>();
            for (ItemInfo item : itemsMap.values()) {
                // Root items are those that are not children of any other item
                if (!childItemIds.contains(item.id)) {
                    rootItems.add(item);
                }
            }
            // Sort by ID for consistent ordering
            rootItems.sort((a, b) -> Integer.compare(a.id, b.id));
            return rootItems;
        }
        
        /**
         * Gets child items for a given parent item ID.
         */
        public List<ItemInfo> getChildItems(int parentId) {
            List<Integer> childIds = childrenMap.get(parentId);
            if (childIds == null || childIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<ItemInfo> childItems = new ArrayList<>();
            for (Integer childId : childIds) {
                ItemInfo child = itemsMap.get(childId);
                if (child != null) {
                    childItems.add(child);
                }
            }
            // Sort by ID for consistent ordering
            childItems.sort((a, b) -> Integer.compare(a.id, b.id));
            return childItems;
        }
        
        /**
         * Gets the HTTP method for a request item.
         */
        public String getRequestMethod(int itemId) {
            return requestMethodsMap.get(itemId);
        }
    }
    
    /**
     * Simple data class for item information.
     */
    public static class ItemInfo {
        public final int id;
        public final String name;
        public final String itemType;
        
        public ItemInfo(int id, String name, String itemType) {
            this.id = id;
            this.name = name;
            this.itemType = itemType;
        }
    }
    
    /**
     * Gets all variables for a collection (collection-level variables).
     * 
     * @param collectionId The collection ID
     * @return Map of variable names to values, or empty map if none found
     */
    public static Map<String, String> getCollectionVariables(int collectionId) {
        Map<String, String> variables = new HashMap<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT variable_key, variable_value FROM variables WHERE collection_id = ?")) {
            stmt.setInt(1, collectionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("variable_key");
                    String value = rs.getString("variable_value");
                    if (key != null) {
                        variables.put(key, value != null ? value : "");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving collection variables: " + e.getMessage());
            e.printStackTrace();
        }
        
        return variables;
    }
    
    /**
     * Gets all variables for an item (item-level variables).
     * 
     * @param itemId The item ID
     * @return Map of variable names to values, or empty map if none found
     */
    public static Map<String, String> getItemVariables(int itemId) {
        Map<String, String> variables = new HashMap<>();
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT variable_key, variable_value FROM variables WHERE item_id = ?")) {
            stmt.setInt(1, itemId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("variable_key");
                    String value = rs.getString("variable_value");
                    if (key != null) {
                        variables.put(key, value != null ? value : "");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving item variables: " + e.getMessage());
            e.printStackTrace();
        }
        
        return variables;
    }
    
    /**
     * Gets all variables for a request, combining collection-level and item-level variables.
     * Item-level variables take precedence over collection-level variables if there's a conflict.
     * 
     * @param collectionId The collection ID
     * @param itemId The item ID (can be -1 if not available)
     * @return Map of variable names to values (item-level variables override collection-level)
     */
    public static Map<String, String> getAllVariablesForRequest(int collectionId, int itemId) {
        Map<String, String> variables = new HashMap<>();
        
        // First, get collection-level variables
        if (collectionId > 0) {
            variables.putAll(getCollectionVariables(collectionId));
        }
        
        // Then, get item-level variables (these will override collection-level if same key)
        if (itemId > 0) {
            variables.putAll(getItemVariables(itemId));
        }
        
        return variables;
    }
    
    /**
     * Gets the collection ID for a given item ID.
     * 
     * @param itemId The item ID
     * @return The collection ID, or -1 if not found
     */
    public static int getCollectionIdByItemId(int itemId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT collection_id FROM items WHERE id = ?")) {
            stmt.setInt(1, itemId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("collection_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving collection ID for item: " + e.getMessage());
            e.printStackTrace();
        }
        
        return -1;
    }
    
    /**
     * Gets the collection name for a given collection ID.
     * 
     * @param collectionId The collection ID
     * @return The collection name, or null if not found
     */
    public static String getCollectionNameById(int collectionId) {
        Connection conn = LiteConnection.getConnection();
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM collections WHERE id = ?")) {
            stmt.setInt(1, collectionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving collection name: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Creates a new request item in the database.
     * 
     * @param collectionId The collection ID
     * @param parentId The parent folder ID (null if root level)
     * @param requestName The name for the new request
     * @return The item ID of the newly created request, or -1 if operation fails
     */
    public static int createNewRequest(int collectionId, Integer parentId, String requestName) {
        Connection conn = LiteConnection.getConnection();
        
        try {
            conn.setAutoCommit(false);
            
            // Create a default request object
            Request defaultRequest = new Request();
            defaultRequest.setMethod("GET");
            
            Url defaultUrl = new Url();
            defaultUrl.setRaw("");
            defaultRequest.setUrl(defaultUrl);
            
            // Insert the item
            int itemId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO items (collection_id, parent_id, name, item_type) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, collectionId);
                if (parentId != null) {
                    stmt.setInt(2, parentId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, requestName);
                stmt.setString(4, "request");
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    itemId = rs.getInt(1);
                } else {
                    System.err.println("Failed to get generated key for new request");
                    conn.rollback();
                    return -1;
                }
            }
            
            // Save the request details
            saveRequest(conn, itemId, defaultRequest);
            
            conn.commit();
            return itemId;
        } catch (SQLException e) {
            System.err.println("Error creating new request: " + e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
            return -1;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}


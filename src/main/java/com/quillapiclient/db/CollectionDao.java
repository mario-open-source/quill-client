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
    public static int saveCollection(
        PostmanCollection collection,
        String fileName
    ) {
        Connection conn = LiteConnection.getConnection();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            System.err.println(
                "Error setting auto-commit to false: " + e.getMessage()
            );
            e.printStackTrace();
            return -1;
        }

        try {
            Info info = collection.getInfo();
            String postmanId =
                info != null && info.getPostmanId() != null
                    ? info.getPostmanId()
                    : null;
            String collectionName = (info != null && info.getName() != null)
                ? info.getName()
                : fileName;
            String schemaVersion = info != null ? info.getSchema() : null;
            String exporterId = info != null ? info.getExporterId() : null;
            String description = info != null ? info.getDescription() : null;

            // Check if collection already exists
            int collectionId;
            try (
                PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM collections WHERE postman_id = ?"
                )
            ) {
                checkStmt.setString(1, postmanId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    collectionId = rs.getInt("id");
                    // Delete existing collection (cascade will delete all related data)
                    try (
                        PreparedStatement deleteStmt = conn.prepareStatement(
                            "DELETE FROM collections WHERE id = ?"
                        )
                    ) {
                        deleteStmt.setInt(1, collectionId);
                        deleteStmt.executeUpdate();
                    }
                } else {
                    collectionId = -1; // Will be set after insert
                }
            }

            // Insert collection
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO collections (postman_id, name, schema_version, exporter_id, description) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                )
            ) {
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
                saveVariables(
                    conn,
                    collectionId,
                    null,
                    collection.getVariable()
                );
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
                System.err.println(
                    "Error rolling back transaction: " + rollbackEx.getMessage()
                );
                rollbackEx.printStackTrace();
            }
            System.err.println(
                "Error saving collection to database: " + e.getMessage()
            );
            e.printStackTrace();
            return -1;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println(
                    "Error resetting auto-commit: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }
    }

    /**
     * Recursively saves an item (folder or request) to the database.
     */
    private static int saveItem(
        Connection conn,
        int collectionId,
        Integer parentId,
        Item item
    ) {
        String itemType = item.getRequest() != null ? "request" : "folder";

        // Insert item
        int itemId = -1;
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO items (collection_id, parent_id, name, item_type) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
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
                System.err.println(
                    "Failed to get generated key for item: " + item.getName()
                );
                return -1;
            }
        } catch (SQLException e) {
            System.err.println(
                "Error saving item to database: " + e.getMessage()
            );
            e.printStackTrace();
            return -1;
        }

        // Save item-level variables
        if (item.getVariable() != null) {
            saveVariables(conn, collectionId, itemId, item.getVariable());
        }

        // Save item-level events (pre-request / test scripts)
        if (item.getEvent() != null) {
            saveEvents(conn, collectionId, itemId, item.getEvent());
        }

        // If it's a request, save request details
        if (item.getRequest() != null) {
            RequestDao.saveRequest(conn, itemId, item.getRequest());
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
     * Saves variables to the database.
     */
    private static void saveVariables(
        Connection conn,
        Integer collectionId,
        Integer itemId,
        List<Variable> variables
    ) {
        for (Variable variable : variables) {
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO variables (collection_id, item_id, variable_key, variable_value, variable_type) " +
                        "VALUES (?, ?, ?, ?, ?)"
                )
            ) {
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
                System.err.println(
                    "Error saving variable to database: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }
    }

    /**
     * Saves events to the database.
     */
    private static void saveEvents(
        Connection conn,
        Integer collectionId,
        Integer itemId,
        List<Event> events
    ) {
        for (Event event : events) {
            String eventJson = null;
            try {
                eventJson = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                System.err.println(
                    "Error serializing event data to JSON: " + e.getMessage()
                );
                e.printStackTrace();
                continue; // Skip this event if serialization fails
            }

            String eventType =
                event.getListen() != null ? event.getListen() : null;
            String scriptType = null;
            String scriptExec = null;

            if (event.getScript() != null) {
                scriptType = event.getScript().getType();
                if (event.getScript().getExec() != null) {
                    scriptExec = String.join("\n", event.getScript().getExec());
                }
            }

            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO events (collection_id, item_id, event_type, script_type, script_exec, full_event_json) " +
                        "VALUES (?, ?, ?, ?, ?, ?)"
                )
            ) {
                stmt.setInt(1, collectionId);
                if (itemId != null) {
                    stmt.setInt(2, itemId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, eventType);
                stmt.setString(4, scriptType);
                stmt.setString(5, scriptExec);
                stmt.setString(6, eventJson);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println(
                    "Error saving event to database: " + e.getMessage()
                );
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name FROM collections ORDER BY created_at DESC"
            )
        ) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                collections.add(
                    new CollectionInfo(rs.getInt("id"), rs.getString("name"))
                );
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting all collections from database: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return collections;
    }

    /**
     * Creates a new empty collection.
     *
     * @param collectionName The collection name
     * @return The new collection ID, or -1 if create failed
     */
    public static int createCollection(String collectionName) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            return -1;
        }

        Connection conn = LiteConnection.getConnection();
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO collections (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
            stmt.setString(1, collectionName.trim());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error creating collection: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Updates collection name.
     *
     * @param collectionId The collection ID
     * @param newName The new name
     * @return true when update succeeded
     */
    public static boolean updateCollectionName(
        int collectionId,
        String newName
    ) {
        if (collectionId <= 0 || newName == null || newName.trim().isEmpty()) {
            return false;
        }

        Connection conn = LiteConnection.getConnection();
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE collections SET name = ? WHERE id = ?"
            )
        ) {
            stmt.setString(1, newName.trim());
            stmt.setInt(2, collectionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println(
                "Error updating collection name: " + e.getMessage()
            );
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the most recently loaded collection ID.
     *
     * @return The collection ID, or -1 if no collections exist
     */
    public static int getLatestCollectionId() {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM collections ORDER BY created_at DESC LIMIT 1"
            )
        ) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting latest collection ID from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Deletes an entire collection and all related data.
     *
     * @param collectionId The collection ID
     * @return true if delete was successful, false otherwise
     */
    public static boolean deleteCollection(int collectionId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM collections WHERE id = ?"
            )
        ) {
            stmt.setInt(1, collectionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println(
                "Error deleting collection from database: " + e.getMessage()
            );
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes an item (request or folder). Cascades to child items and related data.
     *
     * @param itemId The item ID
     * @return true if delete was successful, false otherwise
     */
    public static boolean deleteItem(int itemId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM items WHERE id = ?"
            )
        ) {
            stmt.setInt(1, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println(
                "Error deleting item from database: " + e.getMessage()
            );
            e.printStackTrace();
            return false;
        }
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, item_type FROM items WHERE collection_id = ? AND parent_id IS NULL ORDER BY id"
            )
        ) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(
                    new ItemInfo(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("item_type")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting root items from database: " + e.getMessage()
            );
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, item_type FROM items WHERE parent_id = ? ORDER BY id"
            )
        ) {
            stmt.setInt(1, parentId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(
                    new ItemInfo(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("item_type")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting child items from database: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return items;
    }

    /**
     * Gets the item name by item ID.
     */
    public static String getItemName(int itemId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM items WHERE id = ?"
            )
        ) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting item name from database: " + e.getMessage()
            );
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM items WHERE collection_id = ? AND name = ? LIMIT 1"
            )
        ) {
            stmt.setInt(1, collectionId);
            stmt.setString(2, itemName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting item ID by name from database: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return -1;
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
    public static CollectionItemsData getAllItemsForCollection(
        int collectionId
    ) {
        Map<Integer, ItemInfo> itemsMap = new HashMap<>();
        Map<Integer, List<Integer>> childrenMap = new HashMap<>();
        Map<Integer, String> requestMethodsMap = new HashMap<>();
        java.util.Set<Integer> childItemIds = new java.util.HashSet<>(); // Track which items are children

        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT i.id, i.name, i.item_type, i.parent_id, r.method " +
                    "FROM items i " +
                    "LEFT JOIN requests r ON i.id = r.item_id " +
                    "WHERE i.collection_id = ? ORDER BY i.id"
            )
        ) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int itemId = rs.getInt("id");
                String name = rs.getString("name");
                String itemType = rs.getString("item_type");
                Integer parentId =
                    rs.getObject("parent_id") != null
                        ? rs.getInt("parent_id")
                        : null;
                String method = rs.getString("method");

                // Store item info
                itemsMap.put(itemId, new ItemInfo(itemId, name, itemType));

                // Store request method if available
                if (method != null && !method.isEmpty()) {
                    requestMethodsMap.put(itemId, method.toUpperCase());
                }

                // Build parent-child relationship map
                if (parentId != null) {
                    childrenMap
                        .computeIfAbsent(parentId, k -> new ArrayList<>())
                        .add(itemId);
                    childItemIds.add(itemId); // Mark this item as a child
                }
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting all items for collection from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return new CollectionItemsData(
            itemsMap,
            childrenMap,
            requestMethodsMap,
            childItemIds
        );
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

        public CollectionItemsData(
            Map<Integer, ItemInfo> itemsMap,
            Map<Integer, List<Integer>> childrenMap,
            Map<Integer, String> requestMethodsMap,
            java.util.Set<Integer> childItemIds
        ) {
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT variable_key, variable_value FROM variables WHERE collection_id = ?"
            )
        ) {
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
            System.err.println(
                "Error retrieving collection variables: " + e.getMessage()
            );
            e.printStackTrace();
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT collection_id FROM items WHERE id = ?"
            )
        ) {
            stmt.setInt(1, itemId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("collection_id");
                }
            }
        } catch (SQLException e) {
            System.err.println(
                "Error retrieving collection ID for item: " + e.getMessage()
            );
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

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM collections WHERE id = ?"
            )
        ) {
            stmt.setInt(1, collectionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            System.err.println(
                "Error retrieving collection name: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Creates a new folder item in the database.
     *
     * @param collectionId The collection ID
     * @param parentId The parent folder ID (null if root level)
     * @param folderName The name for the new folder
     * @return The item ID of the newly created folder, or -1 if operation fails
     */
    public static int createNewFolder(
        int collectionId,
        Integer parentId,
        String folderName
    ) {
        Connection conn = LiteConnection.getConnection();

        try {
            conn.setAutoCommit(false);

            int itemId = -1;
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO items (collection_id, parent_id, name, item_type) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                )
            ) {
                stmt.setInt(1, collectionId);
                if (parentId != null) {
                    stmt.setInt(2, parentId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, folderName);
                stmt.setString(4, "folder");
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    itemId = rs.getInt(1);
                } else {
                    System.err.println(
                        "Failed to get generated key for new folder"
                    );
                    conn.rollback();
                    return -1;
                }
            }

            conn.commit();
            return itemId;
        } catch (SQLException e) {
            System.err.println("Error creating new folder: " + e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println(
                    "Error rolling back transaction: " + rollbackEx.getMessage()
                );
                rollbackEx.printStackTrace();
            }
            return -1;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println(
                    "Error resetting auto-commit: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }
    }

    /**
     * Reconstructs a full PostmanCollection from the database for export.
     */
    public static PostmanCollection buildPostmanCollection(int collectionId) {
        Connection conn = LiteConnection.getConnection();

        PostmanCollection collection = new PostmanCollection();

        // Build Info
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT postman_id, name, schema_version, exporter_id, description FROM collections WHERE id = ?"
            )
        ) {
            stmt.setInt(1, collectionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Info info = new Info();
                info.setPostmanId(rs.getString("postman_id"));
                info.setName(rs.getString("name"));
                info.setSchema(rs.getString("schema_version"));
                info.setExporterId(rs.getString("exporter_id"));
                info.setDescription(rs.getString("description"));
                collection.setInfo(info);
            } else {
                return null;
            }
        } catch (SQLException e) {
            System.err.println(
                "Error building collection info: " + e.getMessage()
            );
            return null;
        }

        // Build variables
        Map<String, String> varMap = getCollectionVariables(collectionId);
        if (!varMap.isEmpty()) {
            List<Variable> variables = new ArrayList<>();
            for (Map.Entry<String, String> entry : varMap.entrySet()) {
                Variable v = new Variable();
                v.setKey(entry.getKey());
                v.setValue(entry.getValue());
                v.setType("string");
                variables.add(v);
            }
            collection.setVariable(variables);
        }

        // Build root items recursively
        CollectionItemsData itemsData = getAllItemsForCollection(collectionId);
        List<Item> rootItems = new ArrayList<>();
        for (ItemInfo rootInfo : itemsData.getRootItems()) {
            Item item = buildItemFromDb(itemsData, rootInfo, collectionId);
            if (item != null) {
                rootItems.add(item);
            }
        }
        collection.setItem(rootItems.isEmpty() ? null : rootItems);

        return collection;
    }

    private static Item buildItemFromDb(
        CollectionItemsData itemsData,
        ItemInfo itemInfo,
        int collectionId
    ) {
        Item item = new Item();
        item.setName(itemInfo.name);

        if ("request".equals(itemInfo.itemType)) {
            Request request = RequestDao.getRequestByItemId(itemInfo.id);
            item.setRequest(request);
            String method = itemsData.getRequestMethod(itemInfo.id);
            if (method != null && request != null) {
                request.setMethod(method);
            }
        }

        // Recursively build children
        List<ItemInfo> children = itemsData.getChildItems(itemInfo.id);
        if (!children.isEmpty()) {
            List<Item> childItems = new ArrayList<>();
            for (ItemInfo childInfo : children) {
                Item childItem = buildItemFromDb(
                    itemsData,
                    childInfo,
                    collectionId
                );
                if (childItem != null) {
                    childItems.add(childItem);
                }
            }
            item.setItem(childItems);
        }

        return item;
    }

    /**
     * Updates the name of an item (request or folder).
     *
     * @param itemId The item ID
     * @param newName The new name to persist
     * @return true if one or more rows were updated
     */
    public static boolean updateItemName(int itemId, String newName) {
        if (itemId <= 0 || newName == null || newName.trim().isEmpty()) {
            return false;
        }

        Connection conn = LiteConnection.getConnection();
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE items SET name = ? WHERE id = ?"
            )
        ) {
            stmt.setString(1, newName.trim());
            stmt.setInt(2, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating item name: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

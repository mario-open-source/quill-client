package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.*;
import java.io.File;
import java.io.IOException;
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
     * Imports a Postman collection file into the database using streaming JSON
     * parsing. Only one item subtree is held in memory at a time, so importing
     * a collection with thousands of requests does not require materializing
     * the whole file as objects.
     * If a collection with the same postman_id exists, it is replaced.
     *
     * @param file The Postman collection JSON file
     * @param fileName The file name (used as collection name if info.name is null)
     * @return The collection ID in the database, or -1 if the import fails
     */
    public static int importCollectionFile(File file, String fileName) {
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

        int collectionId = -1;
        try (
            JsonParser parser = objectMapper.getFactory().createParser(file)
        ) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException(
                    "Expected a JSON object at the collection root"
                );
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();

                switch (field) {
                    case "info" -> {
                        Info info = objectMapper.readValue(parser, Info.class);
                        collectionId = upsertCollectionRow(
                            conn,
                            collectionId,
                            info,
                            fileName
                        );
                    }
                    case "variable" -> {
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            List<Variable> variables = objectMapper.readValue(
                                parser,
                                new TypeReference<List<Variable>>() {}
                            );
                            collectionId = ensureCollectionRow(
                                conn,
                                collectionId,
                                fileName
                            );
                            VariableDao.saveVariables(
                                conn,
                                collectionId,
                                null,
                                variables
                            );
                        } else {
                            parser.skipChildren();
                        }
                    }
                    case "event" -> {
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            List<Event> events = objectMapper.readValue(
                                parser,
                                new TypeReference<List<Event>>() {}
                            );
                            collectionId = ensureCollectionRow(
                                conn,
                                collectionId,
                                fileName
                            );
                            EventDao.saveEvents(
                                conn,
                                collectionId,
                                null,
                                events
                            );
                        } else {
                            parser.skipChildren();
                        }
                    }
                    case "item" -> {
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            collectionId = ensureCollectionRow(
                                conn,
                                collectionId,
                                fileName
                            );
                            // Stream one item subtree at a time; each becomes
                            // garbage-collectable right after it is persisted
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                Item item = objectMapper.readValue(
                                    parser,
                                    Item.class
                                );
                                ItemDao.saveItem(conn, collectionId, null, item);
                            }
                        } else {
                            parser.skipChildren();
                        }
                    }
                    default -> parser.skipChildren();
                }
            }

            if (collectionId <= 0) {
                throw new IOException(
                    "Collection file contained no importable content"
                );
            }

            conn.commit();
            return collectionId;
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println(
                    "Error rolling back transaction: " + rollbackEx.getMessage()
                );
                rollbackEx.printStackTrace();
            }
            System.err.println(
                "Error importing collection file: " + e.getMessage()
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
     * Inserts the collection row from its info block, or fills in the metadata
     * on the placeholder row when "info" appears after "item" in the file.
     * Replaces any existing collection with the same postman_id.
     */
    private static int upsertCollectionRow(
        Connection conn,
        int existingId,
        Info info,
        String fileName
    ) throws SQLException {
        String postmanId = info != null ? info.getPostmanId() : null;
        String name = info != null && info.getName() != null
            ? info.getName()
            : fileName;
        String schemaVersion = info != null ? info.getSchema() : null;
        String exporterId = info != null ? info.getExporterId() : null;
        String description = info != null ? info.getDescription() : null;

        // postman_id is UNIQUE: drop any previous import of this collection
        if (postmanId != null) {
            try (
                PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM collections WHERE postman_id = ? AND id != ?"
                )
            ) {
                deleteStmt.setString(1, postmanId);
                deleteStmt.setInt(2, existingId);
                deleteStmt.executeUpdate();
            }
        }

        if (existingId > 0) {
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE collections SET postman_id = ?, name = ?, schema_version = ?, exporter_id = ?, description = ? WHERE id = ?"
                )
            ) {
                stmt.setString(1, postmanId);
                stmt.setString(2, name);
                stmt.setString(3, schemaVersion);
                stmt.setString(4, exporterId);
                stmt.setString(5, description);
                stmt.setInt(6, existingId);
                stmt.executeUpdate();
            }
            return existingId;
        }

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO collections (postman_id, name, schema_version, exporter_id, description) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
            stmt.setString(1, postmanId);
            stmt.setString(2, name);
            stmt.setString(3, schemaVersion);
            stmt.setString(4, exporterId);
            stmt.setString(5, description);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert collection row");
    }

    /**
     * Returns the current collection ID, inserting a placeholder row when
     * items/variables/events appear before the "info" block in the file.
     */
    private static int ensureCollectionRow(
        Connection conn,
        int existingId,
        String fileName
    ) throws SQLException {
        if (existingId > 0) {
            return existingId;
        }
        return upsertCollectionRow(conn, existingId, null, fileName);
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
        Map<Integer, ItemDao.ItemInfo> itemsMap = new HashMap<>();
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
                itemsMap.put(
                    itemId,
                    new ItemDao.ItemInfo(itemId, name, itemType)
                );

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

        public final Map<Integer, ItemDao.ItemInfo> itemsMap; // itemId -> ItemDao.ItemInfo
        public final Map<Integer, List<Integer>> childrenMap; // parentId -> list of child itemIds
        public final Map<Integer, String> requestMethodsMap; // itemId -> HTTP method (only for requests)
        private final java.util.Set<Integer> childItemIds; // Set of all item IDs that are children

        public CollectionItemsData(
            Map<Integer, ItemDao.ItemInfo> itemsMap,
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
        public List<ItemDao.ItemInfo> getRootItems() {
            List<ItemDao.ItemInfo> rootItems = new ArrayList<>();
            for (ItemDao.ItemInfo item : itemsMap.values()) {
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
        public List<ItemDao.ItemInfo> getChildItems(int parentId) {
            List<Integer> childIds = childrenMap.get(parentId);
            if (childIds == null || childIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<ItemDao.ItemInfo> childItems = new ArrayList<>();
            for (Integer childId : childIds) {
                ItemDao.ItemInfo child = itemsMap.get(childId);
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
        Map<String, String> varMap = VariableDao.getCollectionVariables(
            collectionId
        );
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
        for (ItemDao.ItemInfo rootInfo : itemsData.getRootItems()) {
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
        ItemDao.ItemInfo itemInfo,
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
        List<ItemDao.ItemInfo> children = itemsData.getChildItems(itemInfo.id);
        if (!children.isEmpty()) {
            List<Item> childItems = new ArrayList<>();
            for (ItemDao.ItemInfo childInfo : children) {
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
}

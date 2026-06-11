package com.quillapiclient.db;

import com.quillapiclient.objects.Item;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing items (folders and requests) in the items table.
 */
public class ItemDao {

    /**
     * Recursively saves an item (folder or request) to the database.
     */
    static int saveItem(
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
            CollectionDao.saveVariables(conn, collectionId, itemId, item.getVariable());
        }

        // Save item-level events (pre-request / test scripts)
        if (item.getEvent() != null) {
            EventDao.saveEvents(conn, collectionId, itemId, item.getEvent());
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
}

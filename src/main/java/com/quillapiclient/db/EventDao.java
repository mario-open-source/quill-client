package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.Event;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Data Access Object for managing pre-request and post-response scripts
 * (stored in the events table).
 */
public class EventDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Upserts a script row into the events table.
     *
     * @param collectionId  collection scope (always required)
     * @param itemId        item scope, or null for collection-level
     * @param eventType     'prerequest' or 'test'
     * @param scriptBody    JavaScript source
     */
    public static void saveScript(
        int collectionId,
        Integer itemId,
        String eventType,
        String scriptBody
    ) {
        Connection conn = LiteConnection.getConnection();
        try {
            conn.setAutoCommit(false);

            // Delete existing row for this scope + type
            String delSql =
                itemId == null
                    ? "DELETE FROM events WHERE collection_id = ? AND item_id IS NULL AND event_type = ?"
                    : "DELETE FROM events WHERE collection_id = ? AND item_id = ? AND event_type = ?";
            try (PreparedStatement del = conn.prepareStatement(delSql)) {
                del.setInt(1, collectionId);
                if (itemId != null) del.setInt(2, itemId);
                del.setString(itemId == null ? 2 : 3, eventType);
                del.executeUpdate();
            }

            // Insert new row if script is non-empty
            if (scriptBody != null && !scriptBody.isBlank()) {
                String insSql =
                    itemId == null
                        ? "INSERT INTO events (collection_id, item_id, event_type, script_exec) VALUES (?, NULL, ?, ?)"
                        : "INSERT INTO events (collection_id, item_id, event_type, script_exec) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insSql)) {
                    ins.setInt(1, collectionId);
                    if (itemId != null) ins.setInt(2, itemId);
                    ins.setString(itemId == null ? 2 : 3, eventType);
                    ins.setString(itemId == null ? 3 : 4, scriptBody);
                    ins.executeUpdate();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            System.err.println("saveScript error: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Saves events to the database.
     */
    static void saveEvents(
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
     * Loads a script from the events table.
     *
     * @param collectionId  collection scope
     * @param itemId        item scope, or null for collection-level
     * @param eventType     'prerequest' or 'test'
     * @return script body, or null if not found
     */
    public static String loadScript(
        int collectionId,
        Integer itemId,
        String eventType
    ) {
        Connection conn = LiteConnection.getConnection();
        String sql =
            itemId == null
                ? "SELECT script_exec FROM events WHERE collection_id = ? AND item_id IS NULL AND event_type = ? LIMIT 1"
                : "SELECT script_exec FROM events WHERE collection_id = ? AND item_id = ? AND event_type = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, collectionId);
            if (itemId != null) stmt.setInt(2, itemId);
            stmt.setString(itemId == null ? 2 : 3, eventType);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("script_exec");
            }
        } catch (SQLException e) {
            System.err.println("loadScript error: " + e.getMessage());
        }
        return null;
    }
}

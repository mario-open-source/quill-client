package com.quillapiclient.scripting;

import com.quillapiclient.db.LiteConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin persistence layer for the three variable scopes.
 *
 * <p>Reuses the existing schema:</p>
 * <ul>
 *   <li>{@code variables} table   → collection variables</li>
 *   <li>{@code environment_values} table → environment variables</li>
 *   <li>Globals are kept in-memory only.</li>
 * </ul>
 *
 * <p>Every mutation is written to the database immediately so that
 * scripts running in separate requests see up-to-date values.</p>
 */
public final class VariableScopeDao {

    private VariableScopeDao() {}

    // ---------------------------------------------------------------
    //  read
    // ---------------------------------------------------------------

    public static Map<String, String> loadEnvironment(int environmentId) {
        if (environmentId <= 0) return new LinkedHashMap<>();
        return loadKeyValues(
            "SELECT variable_key, variable_value FROM environment_values WHERE environment_id = ? AND enabled = 1 ORDER BY sort_order",
            environmentId
        );
    }

    public static Map<String, String> loadCollection(int collectionId) {
        if (collectionId <= 0) return new LinkedHashMap<>();
        return loadKeyValues(
            "SELECT variable_key, variable_value FROM variables WHERE collection_id = ? AND item_id IS NULL",
            collectionId
        );
    }

    public static Map<String, String> loadItem(int itemId) {
        if (itemId <= 0) return new LinkedHashMap<>();
        return loadKeyValues(
            "SELECT variable_key, variable_value FROM variables WHERE item_id = ?",
            itemId
        );
    }

    // ---------------------------------------------------------------
    //  write
    // ---------------------------------------------------------------

    public static void persistEnvironment(
        int environmentId,
        Map<String, String> vars
    ) {
        persistKeyValues(
            "environment_values",
            "environment_id",
            environmentId,
            vars,
            "",
            0 // sort_order starts at 0
        );
    }

    public static void persistCollection(
        int collectionId,
        Map<String, String> vars
    ) {
        persistKeyValues(
            "variables",
            "collection_id",
            collectionId,
            vars,
            "AND item_id IS NULL",
            0
        );
    }

    public static void persistItem(int itemId, Map<String, String> vars) {
        persistKeyValues("variables", "item_id", itemId, vars, "", 0);
    }

    // ---------------------------------------------------------------
    //  helpers
    // ---------------------------------------------------------------

    private static Map<String, String> loadKeyValues(
        String sql,
        int foreignId
    ) {
        Map<String, String> map = new LinkedHashMap<>();
        Connection conn = LiteConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, foreignId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                map.put(
                    rs.getString("variable_key"),
                    rs.getString("variable_value")
                );
            }
        } catch (SQLException e) {
            System.err.println(
                "VariableScopeDao load error: " + e.getMessage()
            );
        }
        return map;
    }

    private static void persistKeyValues(
        String table,
        String foreignColumn,
        int foreignId,
        Map<String, String> vars,
        String extraWhere,
        int sortOrderStart
    ) {
        Connection conn = LiteConnection.getConnection();
        try {
            conn.setAutoCommit(false);

            // delete existing rows for this scope
            String delSql =
                "DELETE FROM " +
                table +
                " WHERE " +
                foreignColumn +
                " = ?" +
                (extraWhere.isEmpty() ? "" : " " + extraWhere);
            try (PreparedStatement del = conn.prepareStatement(delSql)) {
                del.setInt(1, foreignId);
                del.executeUpdate();
            }

            // insert fresh batch
            if (!vars.isEmpty()) {
                String insSql =
                    "INSERT INTO " +
                    table +
                    " (" +
                    foreignColumn +
                    ", variable_key, variable_value, variable_type, sort_order) " +
                    "VALUES (?, ?, ?, 'string', ?)";
                try (PreparedStatement ins = conn.prepareStatement(insSql)) {
                    int order = sortOrderStart;
                    for (Map.Entry<String, String> e : vars.entrySet()) {
                        ins.setInt(1, foreignId);
                        ins.setString(2, e.getKey());
                        ins.setString(3, e.getValue());
                        ins.setInt(4, order++);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            System.err.println(
                "VariableScopeDao persist error: " + e.getMessage()
            );
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }
}

package com.quillapiclient.scripting;

import com.quillapiclient.db.LiteConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin persistence layer for the four variable scopes.
 *
 * <p>Reuses the existing schema:</p>
 * <ul>
 *   <li>{@code variables} table   → collection / item variables</li>
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
        try {
            return loadKeyValues(
                "SELECT variable_key, variable_value FROM environment_values WHERE environment_id = ? AND enabled = 1 ORDER BY sort_order",
                environmentId
            );
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error loading environment " +
                    environmentId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }

    public static Map<String, String> loadCollection(int collectionId) {
        if (collectionId <= 0) return new LinkedHashMap<>();
        try {
            return loadKeyValues(
                "SELECT variable_key, variable_value FROM variables WHERE collection_id = ? AND item_id IS NULL",
                collectionId
            );
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error loading collection " +
                    collectionId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }

    public static Map<String, String> loadItem(int itemId) {
        if (itemId <= 0) return new LinkedHashMap<>();
        try {
            return loadKeyValues(
                "SELECT variable_key, variable_value FROM variables WHERE item_id = ?",
                itemId
            );
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error loading item " +
                    itemId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }

    // ---------------------------------------------------------------
    //  write
    // ---------------------------------------------------------------

    public static void persistEnvironment(
        int environmentId,
        Map<String, String> vars
    ) {
        if (environmentId <= 0 || vars == null) return;
        try {
            persistKeyValues(
                "environment_values",
                "environment_id",
                environmentId,
                vars,
                "",
                0
            );
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error persisting environment " +
                    environmentId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
        }
    }

    public static void persistCollection(
        int collectionId,
        Map<String, String> vars
    ) {
        if (collectionId <= 0 || vars == null) return;
        try {
            persistKeyValues(
                "variables",
                "collection_id",
                collectionId,
                vars,
                "AND item_id IS NULL",
                0
            );
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error persisting collection " +
                    collectionId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
        }
    }

    public static void persistItem(int itemId, Map<String, String> vars) {
        if (itemId <= 0 || vars == null) return;
        try {
            persistKeyValues("variables", "item_id", itemId, vars, "", 0);
        } catch (Exception e) {
            System.err.println(
                "[VariableScopeDao] Error persisting item " +
                    itemId +
                    ": " +
                    e.getMessage()
            );
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------
    //  helpers
    // ---------------------------------------------------------------

    private static Map<String, String> loadKeyValues(String sql, int foreignId)
        throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        Connection conn = LiteConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, foreignId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(
                        rs.getString("variable_key"),
                        rs.getString("variable_value")
                    );
                }
            }
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
    ) throws SQLException {
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
            } catch (SQLException ignored) {
                // rollback failure is non-recoverable
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best-effort reset
            }
        }
    }
}

package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.PostmanEnvironment;
import com.quillapiclient.objects.PostmanEnvironmentValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentDao {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static int saveEnvironment(PostmanEnvironment environment, String fileName) {
        Connection conn = LiteConnection.getConnection();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            System.err.println("Error setting auto-commit to false: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }

        try {
            String postmanId = environment.getId();
            String name = environment.getName() != null ? environment.getName() : fileName;
            String variableScope = environment.getVariableScope();
            String exportedAt = environment.getExportedAt();
            String exportedUsing = environment.getExportedUsing();
            String fullEnvironmentJson = null;

            try {
                fullEnvironmentJson = objectMapper.writeValueAsString(environment);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing environment data to JSON: " + e.getMessage());
                e.printStackTrace();
            }

            int environmentId = findExistingEnvironmentId(conn, postmanId, name);
            if (environmentId > 0) {
                try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM environments WHERE id = ?")) {
                    deleteStmt.setInt(1, environmentId);
                    deleteStmt.executeUpdate();
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO environments (postman_id, name, variable_scope, exported_at, exported_using, full_environment_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, postmanId);
                stmt.setString(2, name);
                stmt.setString(3, variableScope);
                stmt.setString(4, exportedAt);
                stmt.setString(5, exportedUsing);
                stmt.setString(6, fullEnvironmentJson);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    environmentId = rs.getInt(1);
                } else {
                    environmentId = -1;
                }
            }

            if (environmentId > 0 && environment.getValues() != null) {
                saveEnvironmentValues(conn, environmentId, environment.getValues());
            }

            conn.commit();
            return environmentId;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
            System.err.println("Error saving environment to database: " + e.getMessage());
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

    private static int findExistingEnvironmentId(Connection conn, String postmanId, String name) throws SQLException {
        if (postmanId != null && !postmanId.isBlank()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM environments WHERE postman_id = ?")) {
                checkStmt.setString(1, postmanId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        if (name != null && !name.isBlank()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM environments WHERE name = ?")) {
                checkStmt.setString(1, name);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        return -1;
    }

    private static void saveEnvironmentValues(Connection conn, int environmentId, List<PostmanEnvironmentValue> values)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO environment_values (environment_id, variable_key, variable_value, variable_type, enabled, sort_order) "
                + "VALUES (?, ?, ?, ?, ?, ?)") ) {
            int sortOrder = 0;
            for (PostmanEnvironmentValue value : values) {
                stmt.setInt(1, environmentId);
                stmt.setString(2, value.getKey());
                stmt.setString(3, value.getValue());
                stmt.setString(4, value.getType());
                if (value.getEnabled() != null) {
                    stmt.setInt(5, value.getEnabled() ? 1 : 0);
                } else {
                    stmt.setNull(5, Types.INTEGER);
                }
                stmt.setInt(6, sortOrder++);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public static List<EnvironmentInfo> getAllEnvironments() {
        List<EnvironmentInfo> environments = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT id, name FROM environments ORDER BY created_at DESC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                environments.add(new EnvironmentInfo(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("Error loading environments from database: " + e.getMessage());
            e.printStackTrace();
        }

        return environments;
    }

    public static class EnvironmentInfo {
        public final int id;
        public final String name;

        public EnvironmentInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}

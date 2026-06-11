package com.quillapiclient.db;

import com.quillapiclient.objects.Variable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing variables in the variables table.
 */
public class VariableDao {

    /**
     * Saves variables to the database (called within an existing transaction).
     */
    static void saveVariables(
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
}

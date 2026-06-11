package com.quillapiclient.db;

import com.quillapiclient.objects.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing query parameters in SQLite.
 */
public class QueryParamDao {

    /**
     * Gets all query parameters for a request.
     *
     * @param requestId The request ID
     * @return List of Query objects
     */
    public static List<Query> getQueryParams(int requestId) {
        List<Query> queries = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT param_key, param_value FROM query_params WHERE request_id = ? ORDER BY sort_order"
            )
        ) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Query query = new Query();
                query.setKey(rs.getString("param_key"));
                query.setValue(rs.getString("param_value"));
                queries.add(query);
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting query parameters from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return queries;
    }
}

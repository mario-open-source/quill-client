package com.quillapiclient.db;

import com.quillapiclient.objects.Header;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing request headers in SQLite.
 */
public class HeaderDao {

    /**
     * Gets all headers for a request.
     *
     * @param requestId The request ID
     * @return List of Header objects
     */
    public static List<Header> getHeaders(int requestId) {
        List<Header> headers = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT header_key, header_value, disabled FROM headers WHERE request_id = ? ORDER BY sort_order"
            )
        ) {
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
            System.err.println(
                "Error getting headers from database: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return headers;
    }
}

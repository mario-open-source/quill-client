package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.server.ApiResponse;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing API responses in SQLite.
 * Handles saving and querying HTTP responses linked to requests.
 */
public class ResponseDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Saves an API response to the database, linked to a request.
     *
     * @param response The ApiResponse object
     * @param requestId The request ID to associate with
     * @return The response ID, or -1 on failure
     */
    public static int saveResponse(ApiResponse response, int requestId) {
        // Runs on ApiController's background executor thread on every "Send",
        // so it uses its own connection rather than the shared singleton the
        // EDT's DAO calls use — otherwise these inserts could interleave with
        // a concurrent EDT write on the same Connection object.
        Connection conn;
        try {
            conn = LiteConnection.openNewConnection();
        } catch (SQLException e) {
            System.err.println(
                "Error opening response connection: " + e.getMessage()
            );
            e.printStackTrace();
            return -1;
        }

        try {
            // Serialize full response to JSON for flexibility
            String fullResponseJson = null;
            try {
                fullResponseJson = objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                System.err.println(
                    "Error serializing response to JSON: " + e.getMessage()
                );
                e.printStackTrace();
            }

            // Insert response
            int responseId = -1;
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO responses (request_id, status_code, body, duration, full_response_json) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                )
            ) {
                stmt.setInt(1, requestId);
                stmt.setInt(2, response.getStatusCode());
                stmt.setString(3, response.getBody());
                stmt.setLong(4, response.getDuration());
                stmt.setString(5, fullResponseJson);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    responseId = rs.getInt(1);
                } else {
                    System.err.println(
                        "Failed to get generated key for response"
                    );
                    return -1;
                }
            }

            // Save response headers
            if (
                response.getHeaders() != null &&
                !response.getHeaders().isEmpty()
            ) {
                int sortOrder = 0;
                for (Map.Entry<String, java.util.List<String>> entry : response
                    .getHeaders()
                    .entrySet()) {
                    String headerKey = entry.getKey();
                    java.util.List<String> headerValues = entry.getValue();
                    // Join multiple values with comma
                    String headerValue = String.join(", ", headerValues);

                    try (
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO response_headers (response_id, header_key, header_value, sort_order) " +
                                "VALUES (?, ?, ?, ?)"
                        )
                    ) {
                        stmt.setInt(1, responseId);
                        stmt.setString(2, headerKey);
                        stmt.setString(3, headerValue);
                        stmt.setInt(4, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println(
                            "Error saving response header to database: " +
                                e.getMessage()
                        );
                        e.printStackTrace();
                    }
                }
            }

            return responseId;
        } catch (SQLException e) {
            System.err.println(
                "Error saving response to database: " + e.getMessage()
            );
            e.printStackTrace();
            return -1;
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println(
                    "Error closing response connection: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets a response by its ID.
     * Reconstructs the ApiResponse object from database records.
     *
     * @param responseId The response ID
     * @return The ApiResponse object, or null if not found
     */
    public static ApiResponse getResponse(int responseId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM responses WHERE id = ?"
            )
        ) {
            stmt.setInt(1, responseId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            ApiResponse response = new ApiResponse();
            response.setStatusCode(rs.getInt("status_code"));
            response.setBody(rs.getString("body"));
            response.setDuration(rs.getLong("duration"));

            // Reconstruct headers
            Map<String, java.util.List<String>> headers = getResponseHeaders(
                responseId
            );
            response.setHeaders(headers);

            return response;
        } catch (SQLException e) {
            System.err.println(
                "Error getting response from database: " + e.getMessage()
            );
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets all responses for a specific request, ordered by creation time (newest first).
     *
     * @param requestId The request ID
     * @return List of ApiResponse objects
     */
    public static List<ApiResponse> getResponsesByRequestId(int requestId) {
        List<ApiResponse> responses = new ArrayList<>();
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM responses WHERE request_id = ? ORDER BY created_at DESC"
            )
        ) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int responseId = rs.getInt("id");
                ApiResponse response = getResponse(responseId);
                if (response != null) {
                    responses.add(response);
                }
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting responses by request ID from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return responses;
    }

    /**
     * Gets the most recent response for a specific request.
     *
     * @param requestId The request ID
     * @return The ApiResponse object, or null if not found
     */
    public static ApiResponse getLatestResponseByRequestId(int requestId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM responses WHERE request_id = ? ORDER BY created_at DESC LIMIT 1"
            )
        ) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int responseId = rs.getInt("id");
                return getResponse(responseId);
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting latest response by request ID from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Gets all response headers for a response.
     *
     * @param responseId The response ID
     * @return Map of header keys to lists of values
     */
    private static Map<String, java.util.List<String>> getResponseHeaders(
        int responseId
    ) {
        Map<String, java.util.List<String>> headers = new HashMap<>();
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT header_key, header_value FROM response_headers WHERE response_id = ? ORDER BY sort_order"
            )
        ) {
            stmt.setInt(1, responseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString("header_key");
                String value = rs.getString("header_value");

                // Split comma-separated values back into list
                if (value != null && !value.isEmpty()) {
                    java.util.List<String> values = new ArrayList<>();
                    String[] parts = value.split(", ");
                    for (String part : parts) {
                        values.add(part.trim());
                    }
                    headers.put(key, values);
                } else {
                    headers.put(key, new ArrayList<>());
                }
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting response headers from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return headers;
    }
}

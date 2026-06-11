package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.*;
import com.quillapiclient.server.ApiResponse;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing Postman requests, responses, and scripts in SQLite.
 * Handles CRUD operations for requests, their responses, and pre-request / post-response scripts.
 */
public class RequestDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------
    //  Request persistence
    // -----------------------------------------------------------

    /**
     * Saves a request to the database (called within an existing transaction).
     */
    static void saveRequest(
        Connection conn,
        int itemId,
        Request request
    ) {
        String method = request.getMethod() != null ? request.getMethod() : "";
        String urlRaw =
            request.getUrl() != null && request.getUrl().getRaw() != null
                ? request.getUrl().getRaw()
                : "";
        String urlProtocol =
            request.getUrl() != null ? request.getUrl().getProtocol() : null;
        String urlPort =
            request.getUrl() != null ? request.getUrl().getPort() : null;
        String bodyMode =
            request.getBody() != null ? request.getBody().getMode() : null;
        String bodyRaw =
            request.getBody() != null ? request.getBody().getRaw() : null;
        String bodyLanguage =
            request.getBody() != null &&
            request.getBody().getOptions() != null &&
            request.getBody().getOptions().getRaw() != null
                ? request.getBody().getOptions().getRaw().getLanguage()
                : null;

        // Serialize complex objects to JSON
        String fullUrlJson = null;
        String fullBodyJson = null;
        String fullAuthJson = null;
        try {
            fullUrlJson =
                request.getUrl() != null
                    ? objectMapper.writeValueAsString(request.getUrl())
                    : null;
            fullBodyJson =
                request.getBody() != null
                    ? objectMapper.writeValueAsString(request.getBody())
                    : null;
            fullAuthJson =
                request.getAuth() != null
                    ? objectMapper.writeValueAsString(request.getAuth())
                    : null;
        } catch (JsonProcessingException e) {
            System.err.println(
                "Error serializing request data to JSON: " + e.getMessage()
            );
            e.printStackTrace();
            return; // Exit early if serialization fails
        }

        // Extract auth details
        String authTypeStr =
            request.getAuth() != null ? request.getAuth().getType() : null;
        AuthType authType = AuthType.fromDbKey(authTypeStr);
        String authBasicUsername = null;
        String authBasicPassword = null;
        String authBearerToken = null;

        if (request.getAuth() != null) {
            if (
                authType == AuthType.BASIC &&
                request.getAuth().getBasic() != null
            ) {
                for (Credential cred : request.getAuth().getBasic()) {
                    if ("username".equals(cred.getKey())) {
                        authBasicUsername = cred.getValue();
                    } else if ("password".equals(cred.getKey())) {
                        authBasicPassword = cred.getValue();
                    }
                }
            } else if (
                (authType == AuthType.BEARER ||
                    authType == AuthType.JWT_BEARER) &&
                request.getAuth().getBearer() != null
            ) {
                for (Credential cred : request.getAuth().getBearer()) {
                    if ("token".equals(cred.getKey())) {
                        authBearerToken = cred.getValue();
                    }
                }
            }
        }

        // Insert request
        int requestId = -1;
        try (
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO requests (item_id, method, url_raw, url_protocol, url_port, " +
                    "body_mode, body_raw, body_language, auth_type, auth_basic_username, " +
                    "auth_basic_password, auth_bearer_token, full_url_json, full_body_json, full_auth_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
            stmt.setInt(1, itemId);
            stmt.setString(2, method);
            stmt.setString(3, urlRaw);
            stmt.setString(4, urlProtocol);
            stmt.setString(5, urlPort);
            stmt.setString(6, bodyMode);
            stmt.setString(7, bodyRaw);
            stmt.setString(8, bodyLanguage);
            stmt.setString(
                9,
                authType != AuthType.NONE ? authType.getDbKey() : null
            );
            stmt.setString(10, authBasicUsername);
            stmt.setString(11, authBasicPassword);
            stmt.setString(12, authBearerToken);
            stmt.setString(13, fullUrlJson);
            stmt.setString(14, fullBodyJson);
            stmt.setString(15, fullAuthJson);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                requestId = rs.getInt(1);
            } else {
                System.err.println("Failed to get generated key for request");
                return;
            }
        } catch (SQLException e) {
            System.err.println(
                "Error saving request to database: " + e.getMessage()
            );
            e.printStackTrace();
            return;
        }

        // Save headers
        if (request.getHeader() != null) {
            int sortOrder = 0;
            for (Header header : request.getHeader()) {
                try (
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO headers (request_id, header_key, header_value, disabled, sort_order) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    )
                ) {
                    stmt.setInt(1, requestId);
                    stmt.setString(2, header.getKey());
                    stmt.setString(3, header.getValue());
                    stmt.setInt(
                        4,
                        header.getDisabled() != null && header.getDisabled()
                            ? 1
                            : 0
                    );
                    stmt.setInt(5, sortOrder++);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println(
                        "Error saving header to database: " + e.getMessage()
                    );
                    e.printStackTrace();
                }
            }
        }

        // Save query parameters
        if (request.getUrl() != null && request.getUrl().getQuery() != null) {
            int sortOrder = 0;
            for (Query query : request.getUrl().getQuery()) {
                try (
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO query_params (request_id, param_key, param_value, sort_order) " +
                            "VALUES (?, ?, ?, ?)"
                    )
                ) {
                    stmt.setInt(1, requestId);
                    stmt.setString(2, query.getKey());
                    stmt.setString(3, query.getValue());
                    stmt.setInt(4, sortOrder++);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println(
                        "Error saving query parameter to database: " +
                            e.getMessage()
                    );
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Gets a request by its item ID, including reconstructed URL, body, auth, headers, and query params.
     *
     * @param itemId The item ID
     * @return The Request object, or null if not found
     */
    public static Request getRequestByItemId(int itemId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM requests WHERE item_id = ?"
            )
        ) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            Request request = new Request();
            request.setMethod(rs.getString("method"));

            // Reconstruct URL from JSON or individual fields
            Url url = null;
            String fullUrlJson = rs.getString("full_url_json");
            if (fullUrlJson != null && !fullUrlJson.isEmpty()) {
                try {
                    url = objectMapper.readValue(fullUrlJson, Url.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    url = new Url();
                    url.setRaw(rs.getString("url_raw"));
                    url.setProtocol(rs.getString("url_protocol"));
                    url.setPort(rs.getString("url_port"));
                }
            } else {
                url = new Url();
                url.setRaw(rs.getString("url_raw"));
                url.setProtocol(rs.getString("url_protocol"));
                url.setPort(rs.getString("url_port"));
            }
            request.setUrl(url);

            // Reconstruct Body from JSON or individual fields
            Body body = null;
            String fullBodyJson = rs.getString("full_body_json");
            if (fullBodyJson != null && !fullBodyJson.isEmpty()) {
                try {
                    body = objectMapper.readValue(fullBodyJson, Body.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    body = new Body();
                    body.setMode(rs.getString("body_mode"));
                    body.setRaw(rs.getString("body_raw"));
                }
            } else {
                body = new Body();
                body.setMode(rs.getString("body_mode"));
                body.setRaw(rs.getString("body_raw"));
            }
            request.setBody(body);

            // Reconstruct Auth from JSON or individual fields
            Auth auth = null;
            String fullAuthJson = rs.getString("full_auth_json");
            if (fullAuthJson != null && !fullAuthJson.isEmpty()) {
                try {
                    auth = objectMapper.readValue(fullAuthJson, Auth.class);
                } catch (Exception e) {
                    // Fallback to individual fields
                    auth = reconstructAuth(rs);
                }
            } else {
                auth = reconstructAuth(rs);
            }
            request.setAuth(auth);

            // Load headers
            int requestId = rs.getInt("id");
            List<Header> headers = getHeaders(requestId);
            request.setHeader(headers);

            // Load query parameters
            List<Query> queries = getQueryParams(requestId);
            if (queries != null && !queries.isEmpty() && url != null) {
                url.setQuery(queries);
            }

            return request;
        } catch (SQLException e) {
            System.err.println(
                "Error getting request by item ID from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println(
                "Error deserializing request data: " + e.getMessage()
            );
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the request ID for a given item ID.
     *
     * @param itemId The item ID
     * @return The request ID, or -1 if not found
     */
    public static int getRequestIdByItemId(int itemId) {
        Connection conn = LiteConnection.getConnection();

        try (
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM requests WHERE item_id = ? LIMIT 1"
            )
        ) {
            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println(
                "Error getting request ID by item ID from database: " +
                    e.getMessage()
            );
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Updates a request in the database by item ID.
     *
     * @param itemId The item ID of the request to update
     * @param request The updated Request object
     * @return true if update was successful, false otherwise
     */
    public static boolean updateRequest(int itemId, Request request) {
        Connection conn = LiteConnection.getConnection();

        try {
            conn.setAutoCommit(false);

            // Get the request ID directly from the connection
            int requestId = -1;
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM requests WHERE item_id = ? LIMIT 1"
                )
            ) {
                stmt.setInt(1, itemId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    requestId = rs.getInt("id");
                }
            }

            if (requestId <= 0) {
                System.err.println("Request not found for item ID: " + itemId);
                conn.rollback();
                return false;
            }

            // Extract request data
            String method =
                request.getMethod() != null ? request.getMethod() : "";
            String urlRaw =
                request.getUrl() != null && request.getUrl().getRaw() != null
                    ? request.getUrl().getRaw()
                    : "";
            String urlProtocol =
                request.getUrl() != null
                    ? request.getUrl().getProtocol()
                    : null;
            String urlPort =
                request.getUrl() != null ? request.getUrl().getPort() : null;
            String bodyMode =
                request.getBody() != null ? request.getBody().getMode() : null;
            String bodyRaw =
                request.getBody() != null ? request.getBody().getRaw() : null;
            String bodyLanguage =
                request.getBody() != null &&
                request.getBody().getOptions() != null &&
                request.getBody().getOptions().getRaw() != null
                    ? request.getBody().getOptions().getRaw().getLanguage()
                    : null;

            // Serialize complex objects to JSON
            String fullUrlJson = null;
            String fullBodyJson = null;
            String fullAuthJson = null;
            try {
                fullUrlJson =
                    request.getUrl() != null
                        ? objectMapper.writeValueAsString(request.getUrl())
                        : null;
                fullBodyJson =
                    request.getBody() != null
                        ? objectMapper.writeValueAsString(request.getBody())
                        : null;
                fullAuthJson =
                    request.getAuth() != null
                        ? objectMapper.writeValueAsString(request.getAuth())
                        : null;
            } catch (JsonProcessingException e) {
                System.err.println(
                    "Error serializing request data to JSON: " + e.getMessage()
                );
                e.printStackTrace();
                conn.rollback();
                return false;
            }

            // Extract auth details
            String authType = null;
            String authBasicUsername = null;
            String authBasicPassword = null;
            String authBearerToken = null;

            if (request.getAuth() != null) {
                if (
                    request.getAuth().getBasic() != null &&
                    !request.getAuth().getBasic().isEmpty()
                ) {
                    authType = "basic";
                    if (request.getAuth().getBasic().size() > 0) {
                        authBasicUsername = request
                            .getAuth()
                            .getBasic()
                            .get(0)
                            .getValue();
                    }
                    if (request.getAuth().getBasic().size() > 1) {
                        authBasicPassword = request
                            .getAuth()
                            .getBasic()
                            .get(1)
                            .getValue();
                    }
                } else if (
                    request.getAuth().getBearer() != null &&
                    !request.getAuth().getBearer().isEmpty()
                ) {
                    authType = "bearer";
                    if (request.getAuth().getBearer().size() > 0) {
                        authBearerToken = request
                            .getAuth()
                            .getBearer()
                            .get(0)
                            .getValue();
                    }
                }
            }

            // Update request
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE requests SET method = ?, url_raw = ?, url_protocol = ?, url_port = ?, " +
                        "body_mode = ?, body_raw = ?, body_language = ?, auth_type = ?, auth_basic_username = ?, " +
                        "auth_basic_password = ?, auth_bearer_token = ?, full_url_json = ?, full_body_json = ?, " +
                        "full_auth_json = ?, updated_at = CURRENT_TIMESTAMP WHERE item_id = ?"
                )
            ) {
                stmt.setString(1, method);
                stmt.setString(2, urlRaw);
                stmt.setString(3, urlProtocol);
                stmt.setString(4, urlPort);
                stmt.setString(5, bodyMode);
                stmt.setString(6, bodyRaw);
                stmt.setString(7, bodyLanguage);
                stmt.setString(8, authType);
                stmt.setString(9, authBasicUsername);
                stmt.setString(10, authBasicPassword);
                stmt.setString(11, authBearerToken);
                stmt.setString(12, fullUrlJson);
                stmt.setString(13, fullBodyJson);
                stmt.setString(14, fullAuthJson);
                stmt.setInt(15, itemId);
                stmt.executeUpdate();
            }

            // Delete existing headers and query params
            try (
                PreparedStatement deleteHeaders = conn.prepareStatement(
                    "DELETE FROM headers WHERE request_id = ?"
                )
            ) {
                deleteHeaders.setInt(1, requestId);
                deleteHeaders.executeUpdate();
            }

            try (
                PreparedStatement deleteParams = conn.prepareStatement(
                    "DELETE FROM query_params WHERE request_id = ?"
                )
            ) {
                deleteParams.setInt(1, requestId);
                deleteParams.executeUpdate();
            }

            // Save new headers
            if (request.getHeader() != null) {
                int sortOrder = 0;
                for (Header header : request.getHeader()) {
                    try (
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO headers (request_id, header_key, header_value, disabled, sort_order) " +
                                "VALUES (?, ?, ?, ?, ?)"
                        )
                    ) {
                        stmt.setInt(1, requestId);
                        stmt.setString(2, header.getKey());
                        stmt.setString(3, header.getValue());
                        stmt.setInt(
                            4,
                            header.getDisabled() != null && header.getDisabled()
                                ? 1
                                : 0
                        );
                        stmt.setInt(5, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println(
                            "Error saving header to database: " + e.getMessage()
                        );
                        e.printStackTrace();
                    }
                }
            }

            // Save new query parameters
            if (
                request.getUrl() != null && request.getUrl().getQuery() != null
            ) {
                int sortOrder = 0;
                for (Query query : request.getUrl().getQuery()) {
                    try (
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO query_params (request_id, param_key, param_value, sort_order) " +
                                "VALUES (?, ?, ?, ?)"
                        )
                    ) {
                        stmt.setInt(1, requestId);
                        stmt.setString(2, query.getKey());
                        stmt.setString(3, query.getValue());
                        stmt.setInt(4, sortOrder++);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println(
                            "Error saving query parameter to database: " +
                                e.getMessage()
                        );
                        e.printStackTrace();
                    }
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println(
                "Error updating request in database: " + e.getMessage()
            );
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println(
                    "Error rolling back transaction: " + rollbackEx.getMessage()
                );
                rollbackEx.printStackTrace();
            }
            return false;
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
     * Creates a new request in a collection, with a default GET method.
     *
     * @param collectionId The collection ID
     * @param parentId The parent folder ID, or null for root-level
     * @param requestName The display name for the new request
     * @return The new item ID, or -1 on failure
     */
    public static int createNewRequest(
        int collectionId,
        Integer parentId,
        String requestName
    ) {
        Connection conn = LiteConnection.getConnection();

        try {
            conn.setAutoCommit(false);

            // Create a default request object
            Request defaultRequest = new Request();
            defaultRequest.setMethod("GET");

            Url defaultUrl = new Url();
            defaultUrl.setRaw("");
            defaultRequest.setUrl(defaultUrl);

            // Insert the item
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
                stmt.setString(3, requestName);
                stmt.setString(4, "request");
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    itemId = rs.getInt(1);
                } else {
                    System.err.println(
                        "Failed to get generated key for new request"
                    );
                    conn.rollback();
                    return -1;
                }
            }

            // Save the request details
            saveRequest(conn, itemId, defaultRequest);

            conn.commit();
            return itemId;
        } catch (SQLException e) {
            System.err.println("Error creating new request: " + e.getMessage());
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

    // -----------------------------------------------------------
    //  Response persistence
    // -----------------------------------------------------------

    /**
     * Saves an API response to the database, linked to a request.
     *
     * @param response The ApiResponse object
     * @param requestId The request ID to associate with
     * @return The response ID, or -1 on failure
     */
    public static int saveResponse(ApiResponse response, int requestId) {
        Connection conn = LiteConnection.getConnection();

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

    // -----------------------------------------------------------
    //  Script persistence (pre-request / post-response)
    // -----------------------------------------------------------

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

    // -----------------------------------------------------------
    //  Private helper methods
    // -----------------------------------------------------------

    /**
     * Reconstructs Auth object from database fields.
     */
    private static Auth reconstructAuth(ResultSet rs) {
        try {
            String authTypeStr = rs.getString("auth_type");
            AuthType authType = AuthType.fromDbKey(authTypeStr);
            if (authType == AuthType.NONE) {
                return null;
            }

            Auth auth = new Auth();
            auth.setType(authType.getDbKey());

            if (authType == AuthType.BASIC) {
                List<Credential> basic = new ArrayList<>();
                String username = rs.getString("auth_basic_username");
                String password = rs.getString("auth_basic_password");
                if (username != null) {
                    Credential cred = new Credential();
                    cred.setKey("username");
                    cred.setValue(username);
                    basic.add(cred);
                }
                if (password != null) {
                    Credential cred = new Credential();
                    cred.setKey("password");
                    cred.setValue(password);
                    basic.add(cred);
                }
                auth.setBasic(basic);
            } else if (
                authType == AuthType.BEARER || authType == AuthType.JWT_BEARER
            ) {
                List<Credential> bearer = new ArrayList<>();
                String token = rs.getString("auth_bearer_token");
                if (token != null) {
                    Credential cred = new Credential();
                    cred.setKey("token");
                    cred.setValue(token);
                    bearer.add(cred);
                }
                auth.setBearer(bearer);
            }

            return auth;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Gets all headers for a request.
     */
    private static List<Header> getHeaders(int requestId) {
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

    /**
     * Gets all query parameters for a request.
     */
    private static List<Query> getQueryParams(int requestId) {
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

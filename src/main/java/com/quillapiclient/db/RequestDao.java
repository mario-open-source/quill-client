package com.quillapiclient.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing Postman requests in SQLite.
 * Handles CRUD operations for requests.
 */
public class RequestDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------
    //  Request persistence
    // -----------------------------------------------------------

    /**
     * Saves a request to the database (called within an existing transaction).
     */
    static void saveRequest(Connection conn, int itemId, Request request) {
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
            throw new RuntimeException(
                "Error serializing request data to JSON",
                e
            );
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
                throw new RuntimeException(
                    "Failed to get generated key for request"
                );
            }
        } catch (SQLException e) {
            System.err.println(
                "Error saving request to database: " + e.getMessage()
            );
            e.printStackTrace();
            throw new RuntimeException("Error saving request to database", e);
        }

        // Save headers (batched: one statement for the whole list)
        if (request.getHeader() != null && !request.getHeader().isEmpty()) {
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO headers (request_id, header_key, header_value, disabled, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?)"
                )
            ) {
                int sortOrder = 0;
                for (Header header : request.getHeader()) {
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
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                System.err.println(
                    "Error saving headers to database: " + e.getMessage()
                );
                e.printStackTrace();
            }
        }

        // Save query parameters (batched: one statement for the whole list)
        if (
            request.getUrl() != null &&
            request.getUrl().getQuery() != null &&
            !request.getUrl().getQuery().isEmpty()
        ) {
            try (
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO query_params (request_id, param_key, param_value, sort_order) " +
                        "VALUES (?, ?, ?, ?)"
                )
            ) {
                int sortOrder = 0;
                for (Query query : request.getUrl().getQuery()) {
                    stmt.setInt(1, requestId);
                    stmt.setString(2, query.getKey());
                    stmt.setString(3, query.getValue());
                    stmt.setInt(4, sortOrder++);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                System.err.println(
                    "Error saving query parameters to database: " +
                        e.getMessage()
                );
                e.printStackTrace();
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
            List<Header> headers = HeaderDao.getHeaders(requestId);
            request.setHeader(headers);

            // Load query parameters
            List<Query> queries = QueryParamDao.getQueryParams(requestId);
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
        } catch (Exception e) {
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
}

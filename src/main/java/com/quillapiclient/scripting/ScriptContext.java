package com.quillapiclient.scripting;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.server.ApiResponse;
import java.util.Map;
import org.graalvm.polyglot.HostAccess;

/**
 * Java-side bindings for the Postman {@code pm} global object.
 *
 * <p>Exposes to scripts:</p>
 * <pre>{@code
 *   pm.environment.get(key)        pm.environment.set(key, value)        pm.environment.unset(key)
 *   pm.collectionVariables.get(key)  pm.collectionVariables.set(k, v)    pm.collectionVariables.unset(k)
 *   pm.globals.get(key)            pm.globals.set(key, value)            pm.globals.unset(key)
 * }</pre>
 *
 * <p>Future (stubs ready):</p>
 * <pre>{@code
 *   pm.request.url
 *   pm.response.json()
 *   pm.test("name", fn)
 * }</pre>
 *
 * <p>Thread-safety: a {@code ScriptContext} instance is created per request
 * and is therefore confined to the worker thread that executes the scripts.</p>
 */
public class ScriptContext {

    // --- variable scopes (visible to scripts) ---
    private final ScriptableVariableMap environment;
    private final ScriptableVariableMap collectionVariables;
    private final ScriptableVariableMap itemVariables;
    private final ScriptableVariableMap globals;

    // --- future stubs ---
    private PendingRequestStub request;
    private PendingResponseStub response;

    // --- context ids needed for persistence ---
    private final int collectionId;
    private final int itemId;
    private final int environmentId;

    // Shared globals survive across requests within the same app session
    private static final ScriptableVariableMap SHARED_GLOBALS =
        new ScriptableVariableMap();

    static {
        System.out.println(
            "[ScriptContext] SHARED_GLOBALS created: " +
                System.identityHashCode(SHARED_GLOBALS)
        );
    }

    /**
     * Full constructor — loads environment, collection, and item variables from DB
     * and seeds the scriptable maps.
     */
    public ScriptContext(int collectionId, int itemId, int environmentId) {
        this.collectionId = collectionId;
        this.itemId = itemId;
        this.environmentId = environmentId;

        this.environment = new ScriptableVariableMap(
            VariableScopeDao.loadEnvironment(environmentId)
        );
        this.collectionVariables = new ScriptableVariableMap(
            VariableScopeDao.loadCollection(collectionId)
        );
        this.itemVariables = new ScriptableVariableMap(
            VariableScopeDao.loadItem(itemId)
        );
        this.globals = SHARED_GLOBALS;
        System.out.println(
            "[ScriptContext] constructor: globals hash=" +
                System.identityHashCode(this.globals)
        );
    }

    // ---------------------------------------------------------------
    //  pm.environment  — exposed to JS via @HostAccess.Export
    // ---------------------------------------------------------------

    @HostAccess.Export
    public ScriptableVariableMap getEnvironment() {
        return environment;
    }

    // ---------------------------------------------------------------
    //  pm.collectionVariables
    // ---------------------------------------------------------------

    @HostAccess.Export
    public ScriptableVariableMap getCollectionVariables() {
        return collectionVariables;
    }

    // ---------------------------------------------------------------
    //  pm.variables  (item-level / request-level variables)
    // ---------------------------------------------------------------

    @HostAccess.Export
    public ScriptableVariableMap getItemVariables() {
        return itemVariables;
    }

    // ---------------------------------------------------------------
    //  pm.globals
    // ---------------------------------------------------------------

    @HostAccess.Export
    public ScriptableVariableMap getGlobals() {
        return globals;
    }

    // ---------------------------------------------------------------
    //  pm.request  (stub)
    // ---------------------------------------------------------------

    @HostAccess.Export
    public PendingRequestStub getRequest() {
        if (request == null) request = new PendingRequestStub();
        return request;
    }

    // ---------------------------------------------------------------
    //  pm.response  (stub — populated after HTTP call, before post-response script)
    // ---------------------------------------------------------------

    @HostAccess.Export
    public PendingResponseStub getResponse() {
        if (response == null) response = new PendingResponseStub();
        return response;
    }

    /** Call after the HTTP request completes so {@code pm.response} is available. */
    public void hydrateResponse(ApiResponse apiResponse) {
        this.response = new PendingResponseStub(apiResponse);
    }

    // ---------------------------------------------------------------
    //  persistence helpers (package-private, called by the orchestrator)
    // ---------------------------------------------------------------

    boolean anyDirty() {
        return (
            environment.isDirty() ||
            collectionVariables.isDirty() ||
            itemVariables.isDirty() ||
            globals.isDirty()
        );
    }

    void persist() {
        if (environment.isDirty() && environmentId > 0) {
            VariableScopeDao.persistEnvironment(
                environmentId,
                environment.snapshot()
            );
        }
        if (collectionVariables.isDirty() && collectionId > 0) {
            VariableScopeDao.persistCollection(
                collectionId,
                collectionVariables.snapshot()
            );
        }
        if (itemVariables.isDirty() && itemId > 0) {
            VariableScopeDao.persistItem(itemId, itemVariables.snapshot());
        }
        // globals are intentionally not persisted
    }

    /** Merges all scopes into a flat map for {{variable}} resolution. */
    java.util.Map<String, String> mergedForResolution() {
        java.util.Map<String, String> merged = new java.util.LinkedHashMap<>();
        merged.putAll(globals.asLiveMap());
        merged.putAll(collectionVariables.asLiveMap());
        merged.putAll(environment.asLiveMap());
        merged.putAll(itemVariables.asLiveMap());
        System.out.println(
            "[ScriptContext] merged size: " +
                merged.size() +
                " keys: " +
                merged.keySet()
        );
        return merged;
    }

    // ---------------------------------------------------------------
    //  pm.request  stub
    // ---------------------------------------------------------------

    public static class PendingRequestStub {

        private String url;
        private String method;

        PendingRequestStub() {}

        @HostAccess.Export
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @HostAccess.Export
        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }

    // ---------------------------------------------------------------
    //  pm.response  stub
    // ---------------------------------------------------------------

    public static class PendingResponseStub {

        private final ApiResponse delegate;

        PendingResponseStub() {
            this.delegate = null;
        }

        PendingResponseStub(ApiResponse delegate) {
            this.delegate = delegate;
        }

        @HostAccess.Export
        public int getCode() {
            return delegate != null ? delegate.getStatusCode() : 0;
        }

        @HostAccess.Export
        public String text() {
            return delegate != null ? delegate.getBody() : null;
        }

        @HostAccess.Export
        public Object json() {
            if (delegate == null || delegate.getBody() == null) return null;
            try {
                // Convert Java Map to a GraalJS-friendly object so scripts can use .property access
                Map<String, Object> map = new org.json.JSONObject(
                    delegate.getBody()
                ).toMap();
                return org.graalvm.polyglot.proxy.ProxyObject.fromMap(map);
            } catch (Exception e) {
                try {
                    return new org.json.JSONArray(delegate.getBody()).toList();
                } catch (Exception e2) {
                    return delegate.getBody();
                }
            }
        }
    }
}

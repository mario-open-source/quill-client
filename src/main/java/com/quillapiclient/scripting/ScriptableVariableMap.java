package com.quillapiclient.scripting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable variable map exposed to scripts as {@code pm.environment},
 * {@code pm.collectionVariables}, or {@code pm.globals}.
 *
 * <p>Supports the Postman scripting API:</p>
 * <pre>{@code
 *   pm.environment.get("key")
 *   pm.environment.set("key", "value")
 *   pm.environment.unset("key")
 * }</pre>
 *
 * <p>Thread-safety: instances are confined to a single script execution.</p>
 */
public class ScriptableVariableMap {

    private final Map<String, String> delegate;
    private boolean dirty;

    public ScriptableVariableMap() {
        this.delegate = new LinkedHashMap<>();
    }

    public ScriptableVariableMap(Map<String, String> initial) {
        this.delegate = new LinkedHashMap<>(initial);
    }

    // ---- Postman API ----

    public String get(String key) {
        if (key == null) return null;
        return delegate.get(key);
    }

    public void set(String key, String value) {
        if (key == null) return;
        delegate.put(key, value != null ? value : "");
        dirty = true;
    }

    public void unset(String key) {
        if (key == null) return;
        if (delegate.remove(key) != null) {
            dirty = true;
        }
    }

    // ---- internal ----

    public boolean isDirty() {
        return dirty;
    }

    /** Returns an unmodifiable snapshot. */
    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(delegate));
    }

    /** Merges another map's entries into this one (for loading from DB). */
    void mergeFrom(Map<String, String> other) {
        if (other != null) {
            delegate.putAll(other);
        }
    }

    /** For merging resolved variables for template resolution. */
    Map<String, String> asLiveMap() {
        return delegate;
    }
}

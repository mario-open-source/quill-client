package com.quillapiclient.scripting;

/**
 * Identifies which variable scope a Postman-style pm.* object targets.
 *
 * <ul>
 *   <li>{@code ENVIRONMENT}  – persisted to the {@code environment_values} table,
 *        scoped to the currently active environment.</li>
 *   <li>{@code COLLECTION}   – persisted to the {@code variables} table with
 *        {@code collection_id} set and {@code item_id = NULL}.</li>
 *   <li>{@code GLOBALS}      – stored in-memory only; survive a single
 *        request/response cycle but not application restarts.</li>
 * </ul>
 */
public enum VariableScope {
    ENVIRONMENT,
    COLLECTION,
    GLOBALS
}

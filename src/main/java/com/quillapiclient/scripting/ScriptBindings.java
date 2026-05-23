package com.quillapiclient.scripting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable carrier of named Java objects that will be exposed as global
 * variables inside a GraalJS {@code Context}.
 *
 * <p>Usage:</p>
 * <pre>{@code
 *   ScriptBindings b = ScriptBindings.empty()
 *       .with("pm", pmObject)
 *       .with("myVar", 42);
 * }</pre>
 */
public final class ScriptBindings {

    private final Map<String, Object> map;

    private ScriptBindings(Map<String, Object> map) {
        this.map = map;
    }

    public static ScriptBindings empty() {
        return new ScriptBindings(new LinkedHashMap<>());
    }

    public ScriptBindings with(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(this.map);
        copy.put(name, value);
        return new ScriptBindings(copy);
    }

    Set<String> names() {
        return map.keySet();
    }

    Object get(String name) {
        return map.get(name);
    }
}

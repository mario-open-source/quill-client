package com.quillapiclient.scripting;

import com.quillapiclient.db.CollectionDao;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the script-aware request execution flow:
 *
 * <pre>
 *  1. Load pre-request script from DB (events table, event_type = 'prerequest')
 *  2. Build {@link ScriptContext} with current variable scopes
 *  3. Execute pre-request script → mutations go into ScriptContext
 *  4. Persist variable changes to SQLite
 *  5. (caller executes the HTTP request)
 *  6. Hydrate {@code pm.response} from the ApiResponse
 *  7. Load post-response script from DB (event_type = 'test')
 *  8. Execute post-response script
 *  9. Persist any further variable changes
 * 10. Return merged variables for subsequent resolution
 * </pre>
 *
 * <p>Usage from {@code ApiController}:</p>
 * <pre>{@code
 *   ScriptOrchestrator orch = new ScriptOrchestrator(collectionId, environmentId);
 *   orch.runPreRequest();                   // steps 1-4
 *   Map<String,String> vars = orch.getMergedVariables();
 *   // ... execute HTTP call ...
 *   orch.runPostResponse(apiResponse);       // steps 6-9
 * }</pre>
 */
public class ScriptOrchestrator {

    private final int collectionId;
    private final Integer itemId;
    private final int environmentId;
    private final ScriptContext scriptContext;
    private final List<String> logs;

    public ScriptOrchestrator(
        int collectionId,
        Integer itemId,
        int environmentId
    ) {
        this.collectionId = collectionId;
        this.itemId = itemId;
        this.environmentId = environmentId;
        this.logs = new ArrayList<>();
        this.scriptContext = new ScriptContext(
            collectionId,
            itemId != null ? itemId : 0,
            environmentId
        );
    }

    // ---------------------------------------------------------------
    //  public API
    // ---------------------------------------------------------------

    /** Run the pre-request script (if any) and persist changes. */
    public void runPreRequest() {
        String script = loadScript("prerequest");
        if (script != null && !script.isBlank()) {
            try {
                ScriptExecutor.execute(script, buildBindings(), logs);
            } catch (ScriptExecutor.ScriptException e) {
                logs.add("[script-error] pre-request: " + e.getMessage());
            }
        }
        persistIfDirty();
    }

    /** Hydrate response, run the post-response / test script, persist changes. */
    public void runPostResponse(
        com.quillapiclient.server.ApiResponse apiResponse
    ) {
        scriptContext.hydrateResponse(apiResponse);

        String script = loadScript("test");
        if (script != null && !script.isBlank()) {
            System.out.println(
                "[ScriptOrchestrator] Running post-response script:\n" + script
            );
            try {
                ScriptExecutor.execute(script, buildBindings(), logs);
                System.out.println(
                    "[ScriptOrchestrator] Post-response done. globals size=" +
                        scriptContext.getGlobals().asLiveMap().size() +
                        " keys=" +
                        scriptContext.getGlobals().asLiveMap().keySet()
                );
            } catch (ScriptExecutor.ScriptException e) {
                System.err.println(
                    "[ScriptOrchestrator] Script error: " + e.getMessage()
                );
                e.printStackTrace();
                logs.add("[script-error] post-response: " + e.getMessage());
            }
        }
        persistIfDirty();
    }

    /**
     * Returns the merged variable map suitable for {@code {{variable}}} template
     * resolution during the HTTP call phase.
     */
    public java.util.Map<String, String> getMergedVariables() {
        return scriptContext.mergedForResolution();
    }

    /** Returns concatenated console output from both scripts. */
    public List<String> getLogs() {
        return logs;
    }

    // ---------------------------------------------------------------
    //  internals
    // ---------------------------------------------------------------

    private String loadScript(String eventType) {
        if (collectionId <= 0) return null;

        // Try item-level script first, then fall back to collection-level
        if (itemId != null && itemId > 0) {
            String script = CollectionDao.loadScript(
                collectionId,
                itemId,
                eventType
            );
            if (script != null && !script.isBlank()) return script;
        }
        return CollectionDao.loadScript(collectionId, null, eventType);
    }

    private ScriptBindings buildBindings() {
        java.util.Map<String, Object> pmProps = new java.util.LinkedHashMap<>();
        pmProps.put("environment", scriptContext.getEnvironment());
        pmProps.put(
            "collectionVariables",
            scriptContext.getCollectionVariables()
        );
        pmProps.put("variables", scriptContext.getItemVariables());
        pmProps.put("globals", scriptContext.getGlobals());
        pmProps.put("request", scriptContext.getRequest());
        pmProps.put("response", scriptContext.getResponse());
        return ScriptBindings.empty().with(
            "pm",
            org.graalvm.polyglot.proxy.ProxyObject.fromMap(pmProps)
        );
    }

    private void persistIfDirty() {
        if (scriptContext.anyDirty()) {
            scriptContext.persist();
        }
    }
}

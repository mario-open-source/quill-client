package com.quillapiclient.utility;

import com.quillapiclient.components.ScriptsPanel;
import com.quillapiclient.db.CollectionDao;

/**
 * Loads pre-request and post-response scripts from persistence
 * and populates a {@link ScriptsPanel}.
 *
 * <p>Encapsulates the fallback logic: item-level scripts take priority,
 * then collection-level scripts are used as a fallback.</p>
 */
public class ScriptLoader {

    /**
     * Loads scripts for the given item and populates the panel.
     *
     * @param itemId the item whose scripts to load
     * @param panel  the panel to populate (ignored if null)
     */
    public void loadScripts(int itemId, ScriptsPanel panel) {
        if (itemId <= 0 || panel == null) {
            return;
        }

        int collectionId = CollectionDao.getCollectionIdByItemId(itemId);
        if (collectionId <= 0) {
            return;
        }

        // Item-level scripts first, collection-level as fallback
        String preScript = findScript(collectionId, itemId, "prerequest");
        String testScript = findScript(collectionId, itemId, "test");

        panel.setPreRequestScript(preScript);
        panel.setTestScript(testScript);
    }

    private String findScript(int collectionId, int itemId, String type) {
        String script = CollectionDao.loadScript(collectionId, itemId, type);
        if (script != null) {
            return script;
        }
        return CollectionDao.loadScript(collectionId, null, type);
    }
}

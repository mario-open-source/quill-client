package com.quillapiclient.controller;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.EventDao;
import com.quillapiclient.db.RequestDao;
import com.quillapiclient.objects.Request;

/**
 * Controller for request-level persistence operations.
 * Encapsulates DAO calls so view layers never touch the database directly.
 */
public class RequestController {

    /**
     * Updates an existing request in the database.
     *
     * @param itemId  the item ID identifying the request
     * @param request the updated request data
     * @return true if the update succeeded
     */
    public boolean updateRequest(int itemId, Request request) {
        return RequestDao.updateRequest(itemId, request);
    }

    /**
     * Loads a request from the database by its item ID.
     *
     * @param itemId the item ID to look up
     * @return the Request, or null if not found
     */
    public Request getRequestByItemId(int itemId) {
        return RequestDao.getRequestByItemId(itemId);
    }

    /**
     * Saves pre-request and post-response (test) scripts for a given item.
     * Resolves the collection from the item ID internally.
     * Passing null or blank for a script means "clear the item-level script"
     * so collection-level scripts apply as fallback.
     *
     * @param itemId     the item to associate scripts with
     * @param preScript  the pre-request script (null/blank to clear)
     * @param testScript the post-response script (null/blank to clear)
     */
    public void saveScriptsForItem(
        int itemId,
        String preScript,
        String testScript
    ) {
        if (itemId <= 0) return;

        int collectionId = CollectionDao.getCollectionIdByItemId(itemId);
        if (collectionId <= 0) return;

        EventDao.saveScript(
            collectionId,
            itemId,
            "prerequest",
            preScript != null && !preScript.isBlank() ? preScript : null
        );
        EventDao.saveScript(
            collectionId,
            itemId,
            "test",
            testScript != null && !testScript.isBlank() ? testScript : null
        );
    }
}

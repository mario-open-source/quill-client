package com.quillapiclient.controller;

/**
 * Data class to store item information in tree nodes.
 * Kept minimal on purpose: the display text is derived on the fly instead
 * of being stored, so large collections don't retain a second copy of
 * every item name.
 */
public class TreeNodeData {

    public final int itemId;
    public final String itemName;
    public final String itemType;
    public final String method; // null unless the item is a request

    public TreeNodeData(
        int itemId,
        String itemName,
        String itemType,
        String method
    ) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.itemType = itemType;
        this.method = method;
    }

    @Override
    public String toString() {
        if (method == null) {
            return itemName;
        }
        return itemName + " [" + method + "]";
    }
}

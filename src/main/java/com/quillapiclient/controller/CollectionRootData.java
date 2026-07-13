package com.quillapiclient.controller;

/**
 * Data class to store collection root node information.
 */
class CollectionRootData {

    public final int collectionId;
    public final String collectionName;

    public CollectionRootData(int collectionId, String collectionName) {
        this.collectionId = collectionId;
        this.collectionName = collectionName;
    }

    @Override
    public String toString() {
        return collectionName;
    }
}

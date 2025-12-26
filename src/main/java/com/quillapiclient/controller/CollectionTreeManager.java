package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.objects.PostmanCollection;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;

public class CollectionTreeManager {
    private JTree tree;
    private int currentCollectionId = -1;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public CollectionTreeManager() {
        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("No collection loaded");
        tree = new JTree(emptyRoot);
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(new com.quillapiclient.components.MethodTreeCellRenderer());
    }
    
    /**
     * Loads a Postman collection file, saves it to the database, and builds the UI tree.
     * 
     * @param file The Postman collection JSON file
     */
    public void loadCollectionFile(File file) {
        if (file == null || !file.exists()) return;
        
        try {
            // Parse the collection
            PostmanCollection postmanCollection = objectMapper.readValue(file, PostmanCollection.class);
            
            // Save to database
            currentCollectionId = CollectionDao.saveCollection(postmanCollection, file.getName());
            
            // Build tree from database
            DefaultMutableTreeNode rootNode = buildTreeFromDatabase(currentCollectionId, file.getName());
            
            DefaultTreeModel model = new DefaultTreeModel(rootNode);
            tree.setModel(model);
            
            TreePath rootPath = new TreePath(rootNode);
            tree.expandPath(rootPath);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "Error loading collection: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Builds the tree structure from the database for the given collection.
     * Uses batch loading to avoid N+1 query problem.
     * 
     * @param collectionId The collection ID in the database
     * @param collectionName The name to display as root
     * @return The root tree node
     */
    private DefaultMutableTreeNode buildTreeFromDatabase(int collectionId, String collectionName) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(collectionName);
        
        // Load ALL items for the collection in a single query (solves N+1 problem)
        CollectionDao.CollectionItemsData itemsData = CollectionDao.getAllItemsForCollection(collectionId);
        
        // Get root items (items with no parent)
        java.util.List<CollectionDao.ItemInfo> rootItems = itemsData.getRootItems();
        
        // Build tree nodes recursively using in-memory data
        for (CollectionDao.ItemInfo itemInfo : rootItems) {
            rootNode.add(buildNodeFromMemory(itemInfo, itemsData));
        }
        
        return rootNode;
    }
    
    /**
     * Recursively builds a tree node from in-memory item data.
     * This avoids multiple database queries by using pre-loaded data.
     * 
     * @param itemInfo The item information
     * @param itemsData The collection items data (contains all items and relationships)
     * @return The tree node
     */
    private DefaultMutableTreeNode buildNodeFromMemory(CollectionDao.ItemInfo itemInfo, 
                                                       CollectionDao.CollectionItemsData itemsData) {
        String displayName = itemInfo.name;
        
        // If this item is a request, get the method from the pre-loaded map
        if ("request".equals(itemInfo.itemType)) {
            String method = itemsData.getRequestMethod(itemInfo.id);
            if (method != null && !method.isEmpty()) {
                displayName = displayName + " [" + method + "]";
            }
        }
        
        // Store the item data in the user object for later retrieval
        TreeNodeData nodeData = new TreeNodeData(itemInfo.id, itemInfo.name, itemInfo.itemType, displayName);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeData);
        
        // Get child items from in-memory map (no database query!)
        java.util.List<CollectionDao.ItemInfo> childItems = itemsData.getChildItems(itemInfo.id);
        for (CollectionDao.ItemInfo childInfo : childItems) {
            node.add(buildNodeFromMemory(childInfo, itemsData));
        }
        
        return node;
    }
    
    /**
     * Gets the current collection ID.
     * 
     * @return The collection ID, or -1 if no collection is loaded
     */
    public int getCurrentCollectionId() {
        return currentCollectionId;
    }
    
    public JTree getTree() { 
        return tree; 
    }
    
    /**
     * Data class to store item information in tree nodes.
     */
    public static class TreeNodeData {
        public final int itemId;
        public final String itemName;
        public final String itemType;
        public final String displayName;
        
        public TreeNodeData(int itemId, String itemName, String itemType, String displayName) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.itemType = itemType;
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}
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
     * 
     * @param collectionId The collection ID in the database
     * @param collectionName The name to display as root
     * @return The root tree node
     */
    private DefaultMutableTreeNode buildTreeFromDatabase(int collectionId, String collectionName) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(collectionName);
        
        // Get root items (items with no parent)
        java.util.List<CollectionDao.ItemInfo> rootItems = CollectionDao.getRootItems(collectionId);
        
        // Build tree nodes recursively
        for (CollectionDao.ItemInfo itemInfo : rootItems) {
            rootNode.add(buildNodeFromDatabase(itemInfo));
        }
        
        return rootNode;
    }
    
    /**
     * Recursively builds a tree node from database item information.
     * 
     * @param itemInfo The item information from database
     * @return The tree node
     */
    private DefaultMutableTreeNode buildNodeFromDatabase(CollectionDao.ItemInfo itemInfo) {
        String displayName = itemInfo.name;
        
        // If this item is a request, get the method and append it to the name
        if ("request".equals(itemInfo.itemType)) {
            com.quillapiclient.objects.Request request = CollectionDao.getRequestByItemId(itemInfo.id);
            if (request != null && request.getMethod() != null) {
                String method = request.getMethod().toUpperCase();
                displayName = displayName + " [" + method + "]";
            }
        }
        
        // Store the item data in the user object for later retrieval
        TreeNodeData nodeData = new TreeNodeData(itemInfo.id, itemInfo.name, itemInfo.itemType, displayName);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeData);
        
        // Get child items and build recursively
        java.util.List<CollectionDao.ItemInfo> childItems = CollectionDao.getChildItems(itemInfo.id);
        for (CollectionDao.ItemInfo childInfo : childItems) {
            node.add(buildNodeFromDatabase(childInfo));
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
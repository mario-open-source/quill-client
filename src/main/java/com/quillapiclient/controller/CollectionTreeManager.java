package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.objects.Item;
import com.quillapiclient.objects.PostmanCollection;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class CollectionTreeManager {
    private JTree tree;
    private int currentCollectionId = -1;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public CollectionTreeManager() {
        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("Collections");
        tree = new JTree(emptyRoot);
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(new com.quillapiclient.components.MethodTreeCellRenderer());
        
        // Add right-click context menu
        setupContextMenu();
    }
    
    /**
     * Sets up the right-click context menu for the tree
     */
    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem addRequestItem = new JMenuItem("Add Request");
        
        addRequestItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAddRequest();
            }
        });
        
        popupMenu.add(addRequestItem);
        
        // Add mouse listener to show popup menu on right-click
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            
            private void showPopupMenu(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                
                tree.setSelectionPath(path);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                
                // Only show menu for folders or collection roots
                boolean isFolder = false;
                int collectionId = -1;
                Integer parentId = null;
                
                if (userObject instanceof CollectionRootData) {
                    // Collection root - can add request at root level
                    CollectionRootData rootData = (CollectionRootData) userObject;
                    collectionId = rootData.collectionId;
                    parentId = null;
                    isFolder = true;
                } else if (userObject instanceof TreeNodeData) {
                    TreeNodeData nodeData = (TreeNodeData) userObject;
                    if ("folder".equals(nodeData.itemType)) {
                        isFolder = true;
                        collectionId = getCollectionIdByItemId(nodeData.itemId);
                        parentId = nodeData.itemId;
                    }
                }
                
                if (isFolder && collectionId > 0) {
                    // Store context for the action
                    tree.putClientProperty("contextCollectionId", collectionId);
                    tree.putClientProperty("contextParentId", parentId);
                    popupMenu.show(tree, e.getX(), e.getY());
                }
            }
        });
    }
    
    /**
     * Handles adding a new request when user clicks "Add Request" from context menu
     */
    private void handleAddRequest() {
        Integer collectionIdObj = (Integer) tree.getClientProperty("contextCollectionId");
        Integer parentIdObj = (Integer) tree.getClientProperty("contextParentId");
        
        if (collectionIdObj == null || collectionIdObj <= 0) {
            return;
        }
        
        int collectionId = collectionIdObj;
        Integer parentId = parentIdObj;
        
        // Prompt user for request name
        String requestName = JOptionPane.showInputDialog(
            tree,
            "Enter request name:",
            "New Request",
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (requestName == null || requestName.trim().isEmpty()) {
            return; // User cancelled or entered empty name
        }
        
        requestName = requestName.trim();
        Item item = new Item();
        item.setName(requestName);
        
        // Create the new request in database
        int newItemId = CollectionDao.createNewRequest(collectionId, parentId, requestName);
        
        if (newItemId > 0) {
            // Refresh the tree to show the new request
            addRequestNode(collectionId, parentId, item);
        } else {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to create new request",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private DefaultMutableTreeNode findCollectionNode(int collectionId) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object uo = child.getUserObject();
            if (uo instanceof CollectionRootData data && data.collectionId == collectionId) {
                return child;
            }
        }
        return null;
    }

    public void addRequestNode(int collectionId, Integer parentFolderId, Item requestUserObject) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

        DefaultMutableTreeNode collectionNode = findCollectionNode(collectionId);
        if (collectionNode == null) return;

        DefaultMutableTreeNode parentNode = collectionNode;

        // if adding inside a folder, find that folder node under this collection
        if (parentFolderId != null) {
            DefaultMutableTreeNode folderNode = findNodeDepthFirst(collectionNode, uo ->
                    (uo instanceof TreeNodeData nd)
                            && "folder".equals(nd.itemType)
                            && nd.itemId == parentFolderId
            );

            if (folderNode != null) {
                parentNode = folderNode;
            } else {
                // fallback: folder not found in tree (could be collapsed/unloaded or IDs mismatch)
                // You can keep fallback or handle it differently.
            }
        }

        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(requestUserObject.getName());
        model.insertNodeInto(newNode, parentNode, parentNode.getChildCount());

        TreePath path = new TreePath(newNode.getPath());
        tree.expandPath(path.getParentPath());
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
    }

    private DefaultMutableTreeNode findNodeDepthFirst(DefaultMutableTreeNode start,
                                                      java.util.function.Predicate<Object> match) {
        Object uo = start.getUserObject();
        if (match.test(uo)) return start;

        for (int i = 0; i < start.getChildCount(); i++) {
            DefaultMutableTreeNode found =
                    findNodeDepthFirst((DefaultMutableTreeNode) start.getChildAt(i), match);
            if (found != null) return found;
        }
        return null;
    }


    /**
     * Gets collection ID by item ID (helper method)
     */
    private int getCollectionIdByItemId(int itemId) {
        return CollectionDao.getCollectionIdByItemId(itemId);
    }
    
    /**
     * Loads a Postman collection file, saves it to the database, and adds it to the UI tree.
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
            
            // Add collection to tree
            addCollectionToTree(currentCollectionId, file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "Error loading collection: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Loads all collections from the database and builds the UI tree.
     */
    public void loadAllCollections() {
        java.util.List<CollectionDao.CollectionInfo> collections = CollectionDao.getAllCollections();
        
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Collections");
        
        for (CollectionDao.CollectionInfo collectionInfo : collections) {
            DefaultMutableTreeNode collectionNode = buildTreeFromDatabase(collectionInfo.id, collectionInfo.name);
            rootNode.add(collectionNode);
        }
        
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        tree.setModel(model);
        
        // Expand root node
        TreePath rootPath = new TreePath(rootNode);
        tree.expandPath(rootPath);
    }
    
    /**
     * Adds a single collection to the existing tree.
     * 
     * @param collectionId The collection ID
     * @param collectionName The collection name
     */
    private void addCollectionToTree(int collectionId, String collectionName) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) model.getRoot();
        
        // Check if collection already exists in tree (by collection ID)
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (child.getUserObject() instanceof CollectionRootData) {
                CollectionRootData rootData = (CollectionRootData) child.getUserObject();
                if (rootData.collectionId == collectionId) {
                    // Collection already exists, remove it first
                    model.removeNodeFromParent(child);
                    break;
                }
            }
        }
        
        // Build the collection tree
        DefaultMutableTreeNode collectionNode = buildTreeFromDatabase(collectionId, collectionName);
        
        // Add to root (insert at beginning to show newest first)
        model.insertNodeInto(collectionNode, rootNode, 0);
        
        // Expand the new collection
        TreePath collectionPath = new TreePath(collectionNode.getPath());
        tree.expandPath(collectionPath);
        
        // Expand root if not already expanded
        TreePath rootPath = new TreePath(rootNode);
        tree.expandPath(rootPath);
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
        // Use CollectionRootData to track collection ID in the root node
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new CollectionRootData(collectionId, collectionName));
        
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
        public final Integer collectionId; // Optional: only set for collection root nodes
        
        public TreeNodeData(int itemId, String itemName, String itemType, String displayName) {
            this(itemId, itemName, itemType, displayName, null);
        }
        
        public TreeNodeData(int itemId, String itemName, String itemType, String displayName, Integer collectionId) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.itemType = itemType;
            this.displayName = displayName;
            this.collectionId = collectionId;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Data class to store collection root node information.
     */
    private static class CollectionRootData {
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
}
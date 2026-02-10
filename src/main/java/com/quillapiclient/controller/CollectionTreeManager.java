package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.objects.PostmanCollection;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.io.File;

public class CollectionTreeManager {
    private JTree tree;
    private int currentCollectionId = -1;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public CollectionTreeManager() {
        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("Collections");
        tree = new JTree(createTreeModel(emptyRoot));
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(new com.quillapiclient.components.MethodTreeCellRenderer());
        setupInlineEditingSupport();

        // Add right-click context menu
        new CollectionTreeContextMenu(tree, this::handleAddRequest, this::handleAddFolder,
                this::handleDeleteItem, this::handleRenameItem);
    }

    /**
     * Handles adding a new request when user clicks "Add Request" from context menu
     */
    private void handleAddRequest(int collectionId, Integer parentId) {
        if (collectionId <= 0) {
            return;
        }
        
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
        // Create the new request in database
        int newItemId = CollectionDao.createNewRequest(collectionId, parentId, requestName);
        
        if (newItemId > 0) {
            // Refresh the tree to show the new request
            addRequestNode(collectionId, parentId, newItemId, requestName, "GET");
        } else {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to create new request",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Handles adding a new folder when user clicks "Add Folder" from context menu
     */
    private void handleAddFolder(int collectionId, Integer parentId) {
        if (collectionId <= 0) {
            return;
        }

        String folderName = JOptionPane.showInputDialog(
            tree,
            "Enter folder name:",
            "New Folder",
            JOptionPane.PLAIN_MESSAGE
        );

        if (folderName == null || folderName.trim().isEmpty()) {
            return;
        }

        folderName = folderName.trim();
        int newItemId = CollectionDao.createNewFolder(collectionId, parentId, folderName);

        if (newItemId > 0) {
            addFolderNode(collectionId, parentId, newItemId, folderName);
        } else {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to create new folder",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleDeleteItem(String itemType, int collectionId, Integer itemId, DefaultMutableTreeNode node) {
        if (collectionId <= 0 || itemType == null || node == null) {
            return;
        }

        String itemLabel = switch (itemType) {
            case "collection" -> "collection";
            case "folder" -> "folder";
            case "request" -> "request";
            default -> "item";
        };

        int confirm = JOptionPane.showConfirmDialog(
            tree,
            "Delete this " + itemLabel + "? This cannot be undone.",
            "Confirm Delete",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        boolean deleted = switch (itemType) {
            case "collection" -> CollectionDao.deleteCollection(collectionId);
            case "folder", "request" -> itemId != null && CollectionDao.deleteItem(itemId);
            default -> false;
        };

        if (deleted) {
            removeNodeFromTree(node);
        } else {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to delete " + itemLabel,
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleRenameItem(String itemType, int collectionId, Integer itemId, DefaultMutableTreeNode node) {
        if (collectionId <= 0 || itemId == null || node == null) {
            return;
        }
        if (!"folder".equals(itemType) && !"request".equals(itemType)) {
            return;
        }

        TreePath path = new TreePath(node.getPath());
        tree.setSelectionPath(path);
        tree.startEditingAtPath(path);
    }

    private void removeNodeFromTree(DefaultMutableTreeNode node) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        if (node.getParent() == null) {
            return;
        }
        TreePath parentPath = new TreePath(((DefaultMutableTreeNode) node.getParent()).getPath());
        model.removeNodeFromParent(node);
        tree.setSelectionPath(parentPath);
        tree.scrollPathToVisible(parentPath);
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

    public void addRequestNode(int collectionId, Integer parentFolderId, int itemId, String requestName, String method) {
        addNodeToCollection(collectionId, parentFolderId, buildRequestNodeData(itemId, requestName, method, "GET"));
    }

    public void addFolderNode(int collectionId, Integer parentFolderId, int itemId, String folderName) {
        addNodeToCollection(collectionId, parentFolderId, new TreeNodeData(itemId, folderName, "folder", folderName));
    }

    private void addNodeToCollection(int collectionId, Integer parentFolderId, TreeNodeData nodeData) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

        DefaultMutableTreeNode collectionNode = findCollectionNode(collectionId);
        if (collectionNode == null) return;

        DefaultMutableTreeNode parentNode = collectionNode;

        if (parentFolderId != null) {
            DefaultMutableTreeNode folderNode = findNodeDepthFirst(collectionNode, uo ->
                    (uo instanceof TreeNodeData nd)
                            && "folder".equals(nd.itemType)
                            && nd.itemId == parentFolderId
            );

            if (folderNode != null) {
                parentNode = folderNode;
            }
        }

        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(nodeData);
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

    private DefaultTreeModel createTreeModel(DefaultMutableTreeNode rootNode) {
        return new EditableCollectionTreeModel(rootNode);
    }

    private void setupInlineEditingSupport() {
        tree.setEditable(true);
        tree.setInvokesStopCellEditing(true);

        if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer renderer) {
            tree.setCellEditor(new NodeNameTreeCellEditor(tree, renderer));
        }
    }

    public void createCollectionAndStartEditing() {
        String defaultName = "New Collection";
        int collectionId = CollectionDao.createCollection(defaultName);
        if (collectionId <= 0) {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to create new collection",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        DefaultMutableTreeNode collectionNode = addCollectionToTree(collectionId, defaultName);
        if (collectionNode != null) {
            TreePath path = new TreePath(collectionNode.getPath());
            tree.setSelectionPath(path);
            tree.startEditingAtPath(path);
        }
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
        
        DefaultTreeModel model = createTreeModel(rootNode);
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
    private DefaultMutableTreeNode addCollectionToTree(int collectionId, String collectionName) {
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
        return collectionNode;
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
        // If this item is a request, get the method from the pre-loaded map
        TreeNodeData nodeData;
        if ("request".equals(itemInfo.itemType)) {
            String method = itemsData.getRequestMethod(itemInfo.id);
            nodeData = buildRequestNodeData(itemInfo.id, itemInfo.name, method, null);
        } else {
            nodeData = new TreeNodeData(itemInfo.id, itemInfo.name, itemInfo.itemType, itemInfo.name);
        }
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeData);
        
        // Get child items from in-memory map (no database query!)
        java.util.List<CollectionDao.ItemInfo> childItems = itemsData.getChildItems(itemInfo.id);
        for (CollectionDao.ItemInfo childInfo : childItems) {
            node.add(buildNodeFromMemory(childInfo, itemsData));
        }
        
        return node;
    }

    private static TreeNodeData buildRequestNodeData(int itemId, String requestName, String method, String defaultMethod) {
        String displayName = buildRequestDisplayName(requestName, method, defaultMethod);
        return new TreeNodeData(itemId, requestName, "request", displayName);
    }

    private static String buildRequestDisplayName(String requestName, String method, String defaultMethod) {
        String resolvedMethod = (method == null || method.isBlank()) ? defaultMethod : method;
        if (resolvedMethod == null || resolvedMethod.isBlank()) {
            return requestName;
        }
        return requestName + " [" + resolvedMethod + "]";
    }

    private static String extractRequestMethodFromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        int startIndex = displayName.lastIndexOf('[');
        int endIndex = displayName.lastIndexOf(']');
        if (startIndex >= 0 && endIndex > startIndex) {
            return displayName.substring(startIndex + 1, endIndex).trim();
        }
        return null;
    }

    /**
     * Updates the method shown for a request node already present in the tree.
     *
     * @param itemId The request item ID
     * @param method The HTTP method to display
     */
    public void updateRequestNodeMethod(int itemId, String method) {
        if (itemId <= 0) {
            return;
        }

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        DefaultMutableTreeNode requestNode = findNodeDepthFirst(root, uo ->
                (uo instanceof TreeNodeData nd)
                        && "request".equals(nd.itemType)
                        && nd.itemId == itemId
        );

        if (requestNode == null) {
            return;
        }

        Object userObject = requestNode.getUserObject();
        if (!(userObject instanceof TreeNodeData nodeData)) {
            return;
        }

        requestNode.setUserObject(buildRequestNodeData(nodeData.itemId, nodeData.itemName, method, "GET"));
        model.nodeChanged(requestNode);
        tree.repaint();
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

    private class EditableCollectionTreeModel extends DefaultTreeModel {
        private EditableCollectionTreeModel(DefaultMutableTreeNode root) {
            super(root);
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {
            if (path == null) {
                return;
            }

            Object pathNode = path.getLastPathComponent();
            if (!(pathNode instanceof DefaultMutableTreeNode node)) {
                return;
            }

            Object userObject = node.getUserObject();
            if (userObject instanceof CollectionRootData collectionRootData) {
                String newName = newValue != null ? newValue.toString().trim() : "";
                if (newName.isEmpty() || newName.equals(collectionRootData.collectionName)) {
                    nodeChanged(node);
                    return;
                }

                boolean saved = CollectionDao.updateCollectionName(collectionRootData.collectionId, newName);
                if (!saved) {
                    JOptionPane.showMessageDialog(
                        tree,
                        "Failed to rename collection.",
                        "Rename Failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                    nodeChanged(node);
                    return;
                }

                node.setUserObject(new CollectionRootData(collectionRootData.collectionId, newName));
                nodeChanged(node);
                return;
            }

            if (!(userObject instanceof TreeNodeData nodeData)) {
                return;
            }

            if (!"folder".equals(nodeData.itemType) && !"request".equals(nodeData.itemType)) {
                return;
            }

            String newName = newValue != null ? newValue.toString().trim() : "";
            if (newName.isEmpty()) {
                nodeChanged(node);
                return;
            }

            if (newName.equals(nodeData.itemName)) {
                nodeChanged(node);
                return;
            }

            boolean saved = CollectionDao.updateItemName(nodeData.itemId, newName);
            if (!saved) {
                JOptionPane.showMessageDialog(
                    tree,
                    "Failed to rename " + nodeData.itemType + ".",
                    "Rename Failed",
                    JOptionPane.ERROR_MESSAGE
                );
                nodeChanged(node);
                return;
            }

            String displayName = "request".equals(nodeData.itemType)
                    ? buildRequestDisplayName(newName, extractRequestMethodFromDisplayName(nodeData.displayName), "GET")
                    : newName;
            node.setUserObject(new TreeNodeData(
                    nodeData.itemId,
                    newName,
                    nodeData.itemType,
                    displayName,
                    nodeData.collectionId
            ));
            nodeChanged(node);
        }
    }

    private static class NodeNameTreeCellEditor extends DefaultTreeCellEditor {
        private NodeNameTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
            super(tree, renderer);
        }

        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected,
                                                    boolean expanded, boolean leaf, int row) {
            Component component = super.getTreeCellEditorComponent(
                tree, value, isSelected, expanded, leaf, row
            );

            if (value instanceof DefaultMutableTreeNode node
                    && editingComponent instanceof JTextField textField) {
                if (node.getUserObject() instanceof TreeNodeData nodeData
                        && ("folder".equals(nodeData.itemType) || "request".equals(nodeData.itemType))) {
                    textField.setText(nodeData.itemName);
                    textField.selectAll();
                } else if (node.getUserObject() instanceof CollectionRootData collectionRootData) {
                    textField.setText(collectionRootData.collectionName);
                    textField.selectAll();
                }
            }

            return component;
        }
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
    static class CollectionRootData {
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

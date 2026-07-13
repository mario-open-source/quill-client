package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import com.quillapiclient.objects.PostmanCollection;
import com.quillapiclient.objects.Request;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import com.quillapiclient.components.MethodTreeCellRenderer;

public class CollectionTreeManager {

    /**
     * Sentinel child that marks a node whose real children have not been
     * loaded from the database yet. It gives collapsed folders an expand
     * handle without materializing their subtree.
     */
    private static final Object LOADING_PLACEHOLDER = new Object() {
        @Override
        public String toString() {
            return "Loading...";
        }
    };

    private JTree tree;
    private int currentCollectionId = -1;
    private final RequestController requestController;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Consumer<TreeSelectionEvent>> treeSelectionHandlers;
    private final List<IntConsumer> requestItemIdListeners;
    private final List<Consumer<Request>> requestSelectionListeners;

    public CollectionTreeManager(RequestController requestController) {
        this.requestController = requestController;
        treeSelectionHandlers = new ArrayList<>();
        requestItemIdListeners = new ArrayList<>();
        requestSelectionListeners = new ArrayList<>();

        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode(
            "Collections"
        );
        tree = new JTree(createTreeModel(emptyRoot));
        // Large-model mode with a fixed row height uses a fixed-height layout
        // cache, so the tree does not keep per-row bounds for thousands of nodes
        tree.setLargeModel(true);
        if (tree.getRowHeight() <= 0) {
            tree.setRowHeight(22);
        }
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(
            new MethodTreeCellRenderer()
        );
        setupInlineEditingSupport();
        tree.addTreeSelectionListener(this::handleTreeSelectionChanged);
        tree.addTreeWillExpandListener(
            new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event) {
                    Object last = event.getPath().getLastPathComponent();
                    if (last instanceof DefaultMutableTreeNode node) {
                        loadChildrenIfNeeded(node);
                    }
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent event) {}
            }
        );

        // Add right-click context menu
        new CollectionTreeContextMenu(
            tree,
            this::handleAddRequest,
            this::handleAddFolder,
            this::handleDeleteItem,
            this::handleRenameItem,
            this::exportCollection
        );
    }

    public void addTreeSelectionHandler(Consumer<TreeSelectionEvent> handler) {
        if (handler == null) {
            return;
        }
        treeSelectionHandlers.add(handler);
    }

    public void addRequestItemIdSelectionListener(IntConsumer listener) {
        if (listener == null) {
            return;
        }
        requestItemIdListeners.add(listener);
    }

    public void addRequestSelectionListener(Consumer<Request> listener) {
        if (listener == null) {
            return;
        }
        requestSelectionListeners.add(listener);
    }

    private void handleTreeSelectionChanged(TreeSelectionEvent event) {
        for (Consumer<TreeSelectionEvent> handler : new ArrayList<>(
            treeSelectionHandlers
        )) {
            handler.accept(event);
        }

        Object selectedPath = tree.getLastSelectedPathComponent();
        if (!(selectedPath instanceof DefaultMutableTreeNode selectedNode)) {
            return;
        }

        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof TreeNodeData nodeData)) {
            return;
        }

        if (!"request".equals(nodeData.itemType)) {
            return;
        }

        for (IntConsumer listener : new ArrayList<>(requestItemIdListeners)) {
            listener.accept(nodeData.itemId);
        }

        Request selectedRequest = requestController.getRequestByItemId(
            nodeData.itemId
        );
        if (selectedRequest == null) {
            return;
        }

        for (Consumer<Request> listener : new ArrayList<>(
            requestSelectionListeners
        )) {
            listener.accept(selectedRequest);
        }
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
        int newItemId = requestController.createNewRequest(
            collectionId,
            parentId,
            requestName
        );

        if (newItemId > 0) {
            // Refresh the tree to show the new request
            addRequestNode(
                collectionId,
                parentId,
                newItemId,
                requestName,
                "GET"
            );
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
        int newItemId = ItemDao.createNewFolder(
            collectionId,
            parentId,
            folderName
        );

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

    private void handleDeleteItem(
        String itemType,
        int collectionId,
        Integer itemId,
        DefaultMutableTreeNode node
    ) {
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
            case "folder", "request" -> itemId != null &&
                ItemDao.deleteItem(itemId);
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

    private void handleRenameItem(
        String itemType,
        int collectionId,
        Integer itemId,
        DefaultMutableTreeNode node
    ) {
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
        TreePath parentPath = new TreePath(
            ((DefaultMutableTreeNode) node.getParent()).getPath()
        );
        model.removeNodeFromParent(node);
        tree.setSelectionPath(parentPath);
        tree.scrollPathToVisible(parentPath);
    }

    private DefaultMutableTreeNode findCollectionNode(int collectionId) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                (DefaultMutableTreeNode) root.getChildAt(i);
            Object uo = child.getUserObject();
            if (
                uo instanceof CollectionRootData data &&
                data.collectionId == collectionId
            ) {
                return child;
            }
        }
        return null;
    }

    public void addRequestNode(
        int collectionId,
        Integer parentFolderId,
        int itemId,
        String requestName,
        String method
    ) {
        addNodeToCollection(
            collectionId,
            parentFolderId,
            new TreeNodeData(
                itemId,
                requestName,
                "request",
                canonicalMethod(method, "GET")
            )
        );
    }

    public void addFolderNode(
        int collectionId,
        Integer parentFolderId,
        int itemId,
        String folderName
    ) {
        addNodeToCollection(
            collectionId,
            parentFolderId,
            new TreeNodeData(itemId, folderName, "folder", null)
        );
    }

    private void addNodeToCollection(
        int collectionId,
        Integer parentFolderId,
        TreeNodeData nodeData
    ) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

        DefaultMutableTreeNode collectionNode = findCollectionNode(
            collectionId
        );
        if (collectionNode == null) return;

        DefaultMutableTreeNode parentNode = collectionNode;

        if (parentFolderId != null) {
            DefaultMutableTreeNode folderNode = findNodeDepthFirst(
                collectionNode,
                uo ->
                    uo instanceof TreeNodeData nd &&
                    "folder".equals(nd.itemType) &&
                    nd.itemId == parentFolderId
            );

            if (folderNode != null) {
                parentNode = folderNode;
            }
        }

        DefaultMutableTreeNode newNode;
        if (hasUnloadedChildren(parentNode)) {
            // The parent's children were never materialized. The new item is
            // already in the database, so loading the level picks it up.
            loadChildrenIfNeeded(parentNode);
            newNode = findNodeDepthFirst(
                parentNode,
                uo ->
                    uo instanceof TreeNodeData nd &&
                    nd.itemId == nodeData.itemId
            );
            if (newNode == null) return;
        } else {
            newNode = new DefaultMutableTreeNode(nodeData);
            model.insertNodeInto(
                newNode,
                parentNode,
                parentNode.getChildCount()
            );
        }

        TreePath path = new TreePath(newNode.getPath());
        tree.expandPath(path.getParentPath());
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
    }

    /**
     * Loads the children of a collection or folder node from the database the
     * first time it is expanded, replacing the loading placeholder.
     */
    private void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
        if (!hasUnloadedChildren(node)) {
            return;
        }

        int collectionId;
        Integer parentId;
        Object userObject = node.getUserObject();
        if (userObject instanceof CollectionRootData rootData) {
            collectionId = rootData.collectionId;
            parentId = null;
        } else if (
            userObject instanceof TreeNodeData nodeData &&
            "folder".equals(nodeData.itemType)
        ) {
            collectionId = -1; // child lookup only needs the parent ID
            parentId = nodeData.itemId;
        } else {
            return;
        }

        List<ItemDao.ChildRow> rows = ItemDao.getChildRows(
            collectionId,
            parentId
        );

        node.removeAllChildren();
        for (ItemDao.ChildRow row : rows) {
            node.add(createNodeForRow(row));
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
    }

    private boolean hasUnloadedChildren(DefaultMutableTreeNode node) {
        return (
            node.getChildCount() == 1 &&
            ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() ==
                LOADING_PLACEHOLDER
        );
    }

    private static DefaultMutableTreeNode createNodeForRow(
        ItemDao.ChildRow row
    ) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
            new TreeNodeData(row.id, row.name, row.itemType, row.method)
        );
        if (row.hasChildren) {
            node.add(new DefaultMutableTreeNode(LOADING_PLACEHOLDER));
        }
        return node;
    }

    private static String canonicalMethod(String method, String defaultMethod) {
        if (method == null || method.isBlank()) {
            return defaultMethod;
        }
        return method.toUpperCase().intern();
    }

    private DefaultMutableTreeNode findNodeDepthFirst(
        DefaultMutableTreeNode start,
        java.util.function.Predicate<Object> match
    ) {
        Object uo = start.getUserObject();
        if (match.test(uo)) return start;

        for (int i = 0; i < start.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNodeDepthFirst(
                (DefaultMutableTreeNode) start.getChildAt(i),
                match
            );
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

        if (
            tree.getCellRenderer() instanceof DefaultTreeCellRenderer renderer
        ) {
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

        DefaultMutableTreeNode collectionNode = addCollectionToTree(
            collectionId,
            defaultName
        );
        if (collectionNode != null) {
            TreePath path = new TreePath(collectionNode.getPath());
            tree.setSelectionPath(path);
            tree.startEditingAtPath(path);
        }
    }

    public void exportCollection(int collectionId) {
        if (collectionId <= 0) return;

        PostmanCollection collection = CollectionDao.buildPostmanCollection(
            collectionId
        );
        if (collection == null) {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to build collection for export.",
                "Export Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String collectionName = CollectionDao.getCollectionNameById(
            collectionId
        );
        if (collectionName == null || collectionName.trim().isEmpty()) {
            collectionName = "collection";
        }
        String safeName = collectionName.replaceAll("[^a-zA-Z0-9._-]", "_");

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(
            new File(safeName + ".postman_collection.json")
        );
        fileChooser.setDialogTitle("Export Collection");

        if (
            fileChooser.showSaveDialog(tree) != JFileChooser.APPROVE_OPTION
        ) return;

        File outputFile = fileChooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".json")) {
            outputFile = new File(outputFile.getAbsolutePath() + ".json");
        }

        try {
            String json = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(collection);
            java.nio.file.Files.writeString(outputFile.toPath(), json);
            JOptionPane.showMessageDialog(
                tree,
                "Collection exported to:\n" + outputFile.getAbsolutePath(),
                "Export Complete",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                tree,
                "Error exporting collection: " + e.getMessage(),
                "Export Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Imports a Postman collection file into the database on a background
     * thread (streaming, so the whole file is never held in memory) and adds
     * it to the UI tree when done.
     *
     * @param file The Postman collection JSON file
     */
    public void loadCollectionFile(File file) {
        if (file == null || !file.exists()) return;

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return CollectionDao.importCollectionFile(
                    file,
                    file.getName()
                );
            }

            @Override
            protected void done() {
                int collectionId;
                try {
                    collectionId = get();
                } catch (Exception e) {
                    e.printStackTrace();
                    collectionId = -1;
                }

                if (collectionId <= 0) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Error loading collection: " + file.getName(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                currentCollectionId = collectionId;
                String collectionName = CollectionDao.getCollectionNameById(
                    collectionId
                );
                addCollectionToTree(
                    collectionId,
                    collectionName != null ? collectionName : file.getName()
                );
            }
        }.execute();
    }

    /**
     * Loads all collections from the database and builds the UI tree.
     * Only the collection roots are materialized; their contents load lazily
     * when a node is first expanded.
     */
    public void loadAllCollections() {
        java.util.List<CollectionDao.CollectionInfo> collections =
            CollectionDao.getAllCollections();

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            "Collections"
        );

        for (CollectionDao.CollectionInfo collectionInfo : collections) {
            rootNode.add(
                createCollectionNode(collectionInfo.id, collectionInfo.name)
            );
        }

        DefaultTreeModel model = createTreeModel(rootNode);

        // Update the tree on the EDT
        SwingUtilities.invokeLater(() -> {
            tree.setModel(model);
            TreePath rootPath = new TreePath(rootNode);
            tree.expandPath(rootPath);
        });
    }

    /**
     * Creates a collection root node with a lazy-loading placeholder child
     * when the collection has items.
     */
    private DefaultMutableTreeNode createCollectionNode(
        int collectionId,
        String collectionName
    ) {
        DefaultMutableTreeNode collectionNode = new DefaultMutableTreeNode(
            new CollectionRootData(collectionId, collectionName)
        );
        if (ItemDao.hasItems(collectionId)) {
            collectionNode.add(
                new DefaultMutableTreeNode(LOADING_PLACEHOLDER)
            );
        }
        return collectionNode;
    }

    /**
     * Adds a single collection to the existing tree.
     *
     * @param collectionId The collection ID
     * @param collectionName The collection name
     */
    private DefaultMutableTreeNode addCollectionToTree(
        int collectionId,
        String collectionName
    ) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode rootNode =
            (DefaultMutableTreeNode) model.getRoot();

        // Check if collection already exists in tree (by collection ID)
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (child.getUserObject() instanceof CollectionRootData) {
                CollectionRootData rootData =
                    (CollectionRootData) child.getUserObject();
                if (rootData.collectionId == collectionId) {
                    // Collection already exists, remove it first
                    model.removeNodeFromParent(child);
                    break;
                }
            }
        }

        // Build a lazy collection node; contents load on first expand
        DefaultMutableTreeNode collectionNode = createCollectionNode(
            collectionId,
            collectionName
        );

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

        DefaultMutableTreeNode requestNode = findNodeDepthFirst(
            root,
            uo ->
                uo instanceof TreeNodeData nd &&
                "request".equals(nd.itemType) &&
                nd.itemId == itemId
        );

        if (requestNode == null) {
            // Node not materialized yet; it will show the fresh method from
            // the database when its parent is expanded
            return;
        }

        Object userObject = requestNode.getUserObject();
        if (!(userObject instanceof TreeNodeData nodeData)) {
            return;
        }

        requestNode.setUserObject(
            new TreeNodeData(
                nodeData.itemId,
                nodeData.itemName,
                nodeData.itemType,
                canonicalMethod(method, "GET")
            )
        );
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
                String newName =
                    newValue != null ? newValue.toString().trim() : "";
                if (
                    newName.isEmpty() ||
                    newName.equals(collectionRootData.collectionName)
                ) {
                    nodeChanged(node);
                    return;
                }

                boolean saved = CollectionDao.updateCollectionName(
                    collectionRootData.collectionId,
                    newName
                );
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

                node.setUserObject(
                    new CollectionRootData(
                        collectionRootData.collectionId,
                        newName
                    )
                );
                nodeChanged(node);
                return;
            }

            if (!(userObject instanceof TreeNodeData nodeData)) {
                return;
            }

            if (
                !"folder".equals(nodeData.itemType) &&
                !"request".equals(nodeData.itemType)
            ) {
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

            boolean saved = ItemDao.updateItemName(nodeData.itemId, newName);
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

            node.setUserObject(
                new TreeNodeData(
                    nodeData.itemId,
                    newName,
                    nodeData.itemType,
                    nodeData.method
                )
            );
            nodeChanged(node);
        }
    }

    private static class NodeNameTreeCellEditor extends DefaultTreeCellEditor {

        private NodeNameTreeCellEditor(
            JTree tree,
            DefaultTreeCellRenderer renderer
        ) {
            super(tree, renderer);
        }

        @Override
        public Component getTreeCellEditorComponent(
            JTree tree,
            Object value,
            boolean isSelected,
            boolean expanded,
            boolean leaf,
            int row
        ) {
            Component component = super.getTreeCellEditorComponent(
                tree,
                value,
                isSelected,
                expanded,
                leaf,
                row
            );

            if (
                value instanceof DefaultMutableTreeNode node &&
                editingComponent instanceof JTextField textField
            ) {
                if (
                    node.getUserObject() instanceof TreeNodeData nodeData &&
                    ("folder".equals(nodeData.itemType) ||
                        "request".equals(nodeData.itemType))
                ) {
                    textField.setText(nodeData.itemName);
                    textField.selectAll();
                } else if (
                    node.getUserObject() instanceof
                        CollectionRootData collectionRootData
                ) {
                    textField.setText(collectionRootData.collectionName);
                    textField.selectAll();
                }
            }

            return component;
        }
    }

    /**
     * Data class to store item information in tree nodes.
     * Kept minimal on purpose: the display text is derived on the fly instead
     * of being stored, so large collections don't retain a second copy of
     * every item name.
     */
    public static class TreeNodeData {

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

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

public class CollectionTreeManager {

    private static final int PAGE_SIZE = 100;
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
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(
            new com.quillapiclient.components.MethodTreeCellRenderer()
        );
        setupInlineEditingSupport();
        tree.addTreeSelectionListener(this::handleTreeSelectionChanged);

        // Add right-click context menu
        new CollectionTreeContextMenu(
            tree,
            this::handleAddRequest,
            this::handleAddFolder,
            this::handleDeleteItem,
            this::handleRenameItem,
            this::exportCollection
        );

        // Lazy-load children when a node is expanded for the first time
        setupLazyLoading();
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

        // Handle "Show more..." pagination clicks
        if (userObject instanceof LoadMoreData loadMore) {
            handleLoadMoreClick(selectedNode, loadMore);
            return;
        }

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
     * Handles clicks on the "Show more..." pagination node.
     * Removes the node, loads the next page, and expands the parent.
     */
    private void handleLoadMoreClick(
        DefaultMutableTreeNode moreNode,
        LoadMoreData loadMore
    ) {
        DefaultMutableTreeNode parentNode =
            (DefaultMutableTreeNode) moreNode.getParent();
        if (parentNode == null) {
            return;
        }

        // Remove the "Show more..." node
        moreNode.removeFromParent();

        // Load the next page
        loadPage(
            parentNode,
            loadMore.collectionId,
            loadMore.parentId,
            loadMore.isCollectionRoot,
            loadMore.offset,
            loadMore.offset + loadMore.remaining
        );

        // Notify the model and expand to show new items
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.nodeStructureChanged(parentNode);
        tree.expandPath(new TreePath(parentNode.getPath()));

        // Scroll to the first newly loaded item
        if (parentNode.getChildCount() > 0) {
            int newIndex = parentNode.getChildCount() - 1;
            // Find the index of the last loaded page's first item
            int firstNewIndex = Math.max(
                0,
                newIndex - PAGE_SIZE + 1
            );
            DefaultMutableTreeNode firstNew =
                (DefaultMutableTreeNode) parentNode.getChildAt(
                    firstNewIndex
                );
            TreePath firstNewPath = new TreePath(firstNew.getPath());
            tree.scrollPathToVisible(firstNewPath);
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
            buildRequestNodeData(itemId, requestName, method, "GET")
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
            new TreeNodeData(itemId, folderName, "folder", folderName)
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

        // Force-load the collection's children if it hasn't been expanded yet.
        // This ensures findNodeDepthFirst can locate descendant folders.
        if (hasDummyChild(collectionNode)) {
            forceLoadNode(collectionNode, collectionId, -1, true);
            model.nodeStructureChanged(collectionNode);
        }

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
                // Force-load the folder's children if it hasn't been expanded yet
                if (hasDummyChild(folderNode)) {
                    forceLoadNode(
                        folderNode,
                        collectionId,
                        parentFolderId,
                        false
                    );
                    model.nodeStructureChanged(folderNode);
                }
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

    /**
     * Force-loads children of a node, bypassing the dummy check.
     * Used when we need to add a child to a node that hasn't been expanded.
     */
    private void forceLoadNode(
        DefaultMutableTreeNode node,
        int collectionId,
        int parentId,
        boolean isCollectionRoot
    ) {
        // Remove dummy if present
        if (hasDummyChild(node)) {
            node.removeAllChildren();
        }

        int total = isCollectionRoot
            ? ItemDao.getRootItemCount(collectionId)
            : ItemDao.getChildCount(parentId);

        if (total <= PAGE_SIZE) {
            loadChildrenForNode(
                node,
                collectionId,
                parentId,
                isCollectionRoot,
                false
            );
        } else {
            loadPage(node, collectionId, parentId, isCollectionRoot, 0, total);
        }
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

    // -----------------------------------------------------------------------
    // Lazy tree loading
    // -----------------------------------------------------------------------

    /**
     * Registers a listener that loads children on first expansion
     * and handles paginated "Show more..." clicks.
     */
    private void setupLazyLoading() {
        tree.addTreeWillExpandListener(
            new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event) {
                    DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event
                            .getPath()
                            .getLastPathComponent();

                    if (!hasDummyChild(node)) {
                        return; // already loaded
                    }

                    // Remove the dummy
                    node.removeAllChildren();

                    // Determine what to load
                    int parentId;
                    int collectionId;
                    boolean isCollectionRoot;

                    Object uo = node.getUserObject();
                    if (uo instanceof CollectionRootData crd) {
                        collectionId = crd.collectionId;
                        parentId = -1; // sentinel: load root-level items
                        isCollectionRoot = true;
                    } else if (
                        uo instanceof TreeNodeData nd &&
                        "folder".equals(nd.itemType)
                    ) {
                        collectionId = nd.collectionId != null
                            ? nd.collectionId
                            : -1;
                        parentId = nd.itemId;
                        isCollectionRoot = false;
                    } else {
                        return;
                    }

                    int total = isCollectionRoot
                        ? ItemDao.getRootItemCount(collectionId)
                        : ItemDao.getChildCount(parentId);

                    if (total <= PAGE_SIZE) {
                        // Small enough — load all at once
                        loadChildrenForNode(
                            node,
                            collectionId,
                            parentId,
                            isCollectionRoot,
                            false
                        );
                    } else {
                        // Large — load first page + "Show more..." node
                        loadPage(
                            node,
                            collectionId,
                            parentId,
                            isCollectionRoot,
                            0,
                            total
                        );
                    }

                    // Notify the model
                    DefaultTreeModel model =
                        (DefaultTreeModel) tree.getModel();
                    model.nodeStructureChanged(node);
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent event) {
                    // No-op: keep children in memory for fast re-expand.
                    // To free memory on collapse, uncomment the code below:
                    // DefaultMutableTreeNode node = ...;
                    // node.removeAllChildren();
                    // node.add(new DefaultMutableTreeNode(new DummyData()));
                }
            }
        );
    }

    /** Returns true if the first (and only) child of this node is a DummyData. */
    private static boolean hasDummyChild(DefaultMutableTreeNode node) {
        return node.getChildCount() == 1 &&
            ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject()
                instanceof DummyData;
    }

    /**
     * Loads ALL children for a node (when total ≤ PAGE_SIZE).
     * Each folder child gets its own dummy so it can be lazily expanded later.
     */
    private void loadChildrenForNode(
        DefaultMutableTreeNode parentNode,
        int collectionId,
        int parentId,
        boolean isCollectionRoot,
        boolean append
    ) {
        List<ItemDao.ItemInfo> children;
        if (isCollectionRoot) {
            children = new ArrayList<>(ItemDao.getRootItems(collectionId));
        } else {
            children = new ArrayList<>(ItemDao.getChildItems(parentId));
        }

        // If appending (used by force-load before insert), skip nodes already present
        if (append && parentNode.getChildCount() > 0) {
            java.util.Set<Integer> existingIds = new java.util.HashSet<>();
            for (int i = 0; i < parentNode.getChildCount(); i++) {
                DefaultMutableTreeNode child =
                    (DefaultMutableTreeNode) parentNode.getChildAt(i);
                Object uo = child.getUserObject();
                if (uo instanceof TreeNodeData nd) {
                    existingIds.add(nd.itemId);
                }
            }
            children.removeIf(info -> existingIds.contains(info.id));
        }

        for (ItemDao.ItemInfo info : children) {
            DefaultMutableTreeNode childNode = createTreeNode(info);
            parentNode.add(childNode);
            if ("folder".equals(info.itemType)) {
                childNode.add(
                    new DefaultMutableTreeNode(new DummyData())
                );
            }
        }
    }

    /**
     * Loads a single page of children and appends a "Show more..." node
     * if there are remaining items.
     */
    private void loadPage(
        DefaultMutableTreeNode parentNode,
        int collectionId,
        int parentId,
        boolean isCollectionRoot,
        int offset,
        int total
    ) {
        List<ItemDao.PaginatedItemInfo> page = isCollectionRoot
            ? ItemDao.getRootItemsPaginated(collectionId, offset, PAGE_SIZE)
            : ItemDao.getChildItemsPaginated(parentId, offset, PAGE_SIZE);

        for (ItemDao.PaginatedItemInfo info : page) {
            DefaultMutableTreeNode childNode = createTreeNode(info);
            parentNode.add(childNode);
            if ("folder".equals(info.itemType)) {
                childNode.add(
                    new DefaultMutableTreeNode(new DummyData())
                );
            }
        }

        int remaining = total - offset - page.size();
        if (remaining > 0) {
            DefaultMutableTreeNode moreNode = new DefaultMutableTreeNode(
                new LoadMoreData(
                    parentId,
                    offset + page.size(),
                    remaining,
                    collectionId,
                    isCollectionRoot
                )
            );
            parentNode.add(moreNode);
        }
    }

    /**
     * Creates a tree node from an ItemInfo (used for small, non-paginated loads).
     */
    private DefaultMutableTreeNode createTreeNode(ItemDao.ItemInfo info) {
        TreeNodeData nodeData;
        if ("request".equals(info.itemType)) {
            // Method is not available in ItemInfo — use "GET" as fallback.
            // The method will be corrected when the user selects the request.
            nodeData = buildRequestNodeData(
                info.id,
                info.name,
                null,
                "GET"
            );
        } else {
            nodeData = new TreeNodeData(
                info.id,
                info.name,
                info.itemType,
                info.name
            );
        }
        return new DefaultMutableTreeNode(nodeData);
    }

    /**
     * Creates a tree node from a PaginatedItemInfo (which includes the HTTP method).
     */
    private DefaultMutableTreeNode createTreeNode(
        ItemDao.PaginatedItemInfo info
    ) {
        TreeNodeData nodeData;
        if ("request".equals(info.itemType)) {
            nodeData = buildRequestNodeData(
                info.id,
                info.name,
                info.method,
                "GET"
            );
        } else {
            nodeData = new TreeNodeData(
                info.id,
                info.name,
                info.itemType,
                info.name
            );
        }
        return new DefaultMutableTreeNode(nodeData);
    }

    // -----------------------------------------------------------------------

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
     * Loads a Postman collection file, saves it to the database, and adds it to the UI tree.
     *
     * @param file The Postman collection JSON file
     */
    public void loadCollectionFile(File file) {
        if (file == null || !file.exists()) return;

        try {
            // Parse the collection
            PostmanCollection postmanCollection = objectMapper.readValue(
                file,
                PostmanCollection.class
            );

            // Save to database
            currentCollectionId = CollectionDao.saveCollection(
                postmanCollection,
                file.getName()
            );

            // Add collection to tree
            addCollectionToTree(currentCollectionId, file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                null,
                "Error loading collection: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Loads all collections from the database and builds the UI tree.
     */
    public void loadAllCollections() {
        java.util.List<CollectionDao.CollectionInfo> collections =
            CollectionDao.getAllCollections();

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            "Collections"
        );

        for (CollectionDao.CollectionInfo collectionInfo : collections) {
            DefaultMutableTreeNode collectionNode = buildTreeFromDatabase(
                collectionInfo.id,
                collectionInfo.name
            );
            rootNode.add(collectionNode);
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

        // Build the collection tree
        DefaultMutableTreeNode collectionNode = buildTreeFromDatabase(
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
     * Builds a collection root node with a dummy child that triggers
     * lazy loading on first expansion.
     *
     * @param collectionId The collection ID in the database
     * @param collectionName The name to display as root
     * @return The root tree node
     */
    private DefaultMutableTreeNode buildTreeFromDatabase(
        int collectionId,
        String collectionName
    ) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            new CollectionRootData(collectionId, collectionName)
        );

        // Add a dummy child so the expand arrow appears.
        // Real children are loaded on first expansion via the lazy-load listener.
        rootNode.add(new DefaultMutableTreeNode(new DummyData()));

        return rootNode;
    }

    private static TreeNodeData buildRequestNodeData(
        int itemId,
        String requestName,
        String method,
        String defaultMethod
    ) {
        String displayName = buildRequestDisplayName(
            requestName,
            method,
            defaultMethod
        );
        return new TreeNodeData(itemId, requestName, "request", displayName);
    }

    private static String buildRequestDisplayName(
        String requestName,
        String method,
        String defaultMethod
    ) {
        String resolvedMethod =
            method == null || method.isBlank() ? defaultMethod : method;
        if (resolvedMethod == null || resolvedMethod.isBlank()) {
            return requestName;
        }
        return requestName + " [" + resolvedMethod + "]";
    }

    private static String extractRequestMethodFromDisplayName(
        String displayName
    ) {
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

        DefaultMutableTreeNode requestNode = findNodeDepthFirst(
            root,
            uo ->
                uo instanceof TreeNodeData nd &&
                "request".equals(nd.itemType) &&
                nd.itemId == itemId
        );

        if (requestNode == null) {
            return;
        }

        Object userObject = requestNode.getUserObject();
        if (!(userObject instanceof TreeNodeData nodeData)) {
            return;
        }

        requestNode.setUserObject(
            buildRequestNodeData(
                nodeData.itemId,
                nodeData.itemName,
                method,
                "GET"
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

            String displayName = "request".equals(nodeData.itemType)
                ? buildRequestDisplayName(
                      newName,
                      extractRequestMethodFromDisplayName(nodeData.displayName),
                      "GET"
                  )
                : newName;
            node.setUserObject(
                new TreeNodeData(
                    nodeData.itemId,
                    newName,
                    nodeData.itemType,
                    displayName,
                    nodeData.collectionId
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
     */
    public static class TreeNodeData {

        public final int itemId;
        public final String itemName;
        public final String itemType;
        public final String displayName;
        public final Integer collectionId; // Optional: only set for collection root nodes

        public TreeNodeData(
            int itemId,
            String itemName,
            String itemType,
            String displayName
        ) {
            this(itemId, itemName, itemType, displayName, null);
        }

        public TreeNodeData(
            int itemId,
            String itemName,
            String itemType,
            String displayName,
            Integer collectionId
        ) {
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
     * Sentinal user-object that marks a tree node whose children haven't
     * been loaded yet.  Exactly one such node is placed as the only child
     * of a folder or collection root so that Swing draws the expand arrow.
     */
    static class DummyData {
        @Override
        public String toString() {
            return "Loading...";
        }
    }

    /**
     * User-object for the "Show more..." pagination node.
     * Clicking this node loads the next page of children.
     */
    static class LoadMoreData {
        final int parentId;
        final int offset;
        final int remaining;
        final int collectionId;
        final boolean isCollectionRoot;

        LoadMoreData(
            int parentId,
            int offset,
            int remaining,
            int collectionId,
            boolean isCollectionRoot
        ) {
            this.parentId = parentId;
            this.offset = offset;
            this.remaining = remaining;
            this.collectionId = collectionId;
            this.isCollectionRoot = isCollectionRoot;
        }

        @Override
        public String toString() {
            return "▼ Show more (" + remaining + " remaining)";
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

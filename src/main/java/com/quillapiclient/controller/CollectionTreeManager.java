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
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

public class CollectionTreeManager {

    /** Max children materialised per expand / "Show more" click. */
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
        setupLazyLoading();

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

        // Pagination control: load the next page under the parent.
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
            new TreeNodeData(
                itemId,
                folderName,
                "folder",
                folderName,
                collectionId
            )
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

        // Ensure collection children exist so nested folder lookup works.
        ensureChildrenLoaded(collectionNode, collectionId, null, true);

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
                ensureChildrenLoaded(
                    folderNode,
                    collectionId,
                    parentFolderId,
                    false
                );
                parentNode = folderNode;
            }
        }

        DefaultMutableTreeNode newNode = createNodeFromData(nodeData);
        int insertIndex = indexForNewChild(parentNode);
        model.insertNodeInto(newNode, parentNode, insertIndex);

        TreePath path = new TreePath(newNode.getPath());
        tree.expandPath(path.getParentPath());
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
    }

    /**
     * Insert before a trailing "Show more..." node when present.
     */
    private static int indexForNewChild(DefaultMutableTreeNode parentNode) {
        int count = parentNode.getChildCount();
        if (count == 0) {
            return 0;
        }
        DefaultMutableTreeNode last =
            (DefaultMutableTreeNode) parentNode.getChildAt(count - 1);
        if (last.getUserObject() instanceof LoadMoreData) {
            return count - 1;
        }
        return count;
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
     * Loads children on first expand of a collection or folder that still has
     * a placeholder (dummy) child.
     */
    private void setupLazyLoading() {
        tree.addTreeWillExpandListener(
            new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event)
                    throws ExpandVetoException {
                    Object last = event.getPath().getLastPathComponent();
                    if (!(last instanceof DefaultMutableTreeNode node)) {
                        return;
                    }
                    if (!isUnloadedParent(node)) {
                        return;
                    }

                    ParentLoadContext ctx = resolveParentContext(node);
                    if (ctx == null) {
                        return;
                    }

                    loadFirstPage(node, ctx);
                    ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(
                        node
                    );
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent event) {
                    // Keep children in memory for fast re-expand.
                }
            }
        );
    }

    private void handleLoadMoreClick(
        DefaultMutableTreeNode moreNode,
        LoadMoreData loadMore
    ) {
        DefaultMutableTreeNode parentNode =
            (DefaultMutableTreeNode) moreNode.getParent();
        if (parentNode == null) {
            return;
        }

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.removeNodeFromParent(moreNode);

        ParentLoadContext ctx = new ParentLoadContext(
            loadMore.collectionId,
            loadMore.parentItemId,
            loadMore.isCollectionRoot
        );
        appendPage(parentNode, ctx, loadMore.offset, loadMore.totalCount);
        model.nodeStructureChanged(parentNode);
        tree.expandPath(new TreePath(parentNode.getPath()));
    }

    /**
     * If the node is still unloaded, materialise all children now.
     * Used when adding items under an unexpanded parent so nested lookup works.
     */
    private void ensureChildrenLoaded(
        DefaultMutableTreeNode parentNode,
        int collectionId,
        Integer parentItemId,
        boolean isCollectionRoot
    ) {
        if (!isUnloadedParent(parentNode)) {
            return;
        }
        ParentLoadContext ctx = new ParentLoadContext(
            collectionId,
            parentItemId,
            isCollectionRoot
        );
        loadAllChildren(parentNode, ctx);
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(parentNode);
    }

    private void loadFirstPage(
        DefaultMutableTreeNode parentNode,
        ParentLoadContext ctx
    ) {
        parentNode.removeAllChildren();
        appendPage(parentNode, ctx, 0, countChildren(ctx));
    }

    /** Loads every child page without a trailing "Show more..." node. */
    private void loadAllChildren(
        DefaultMutableTreeNode parentNode,
        ParentLoadContext ctx
    ) {
        parentNode.removeAllChildren();
        int total = countChildren(ctx);
        int offset = 0;
        while (offset < total) {
            List<ItemDao.ChildItemInfo> page = fetchPage(
                ctx,
                offset,
                PAGE_SIZE
            );
            if (page.isEmpty()) {
                break;
            }
            for (ItemDao.ChildItemInfo info : page) {
                parentNode.add(
                    createNodeFromChildInfo(info, ctx.collectionId)
                );
            }
            offset += page.size();
        }
    }

    private void appendPage(
        DefaultMutableTreeNode parentNode,
        ParentLoadContext ctx,
        int offset,
        int totalCount
    ) {
        List<ItemDao.ChildItemInfo> page = fetchPage(ctx, offset, PAGE_SIZE);

        for (ItemDao.ChildItemInfo info : page) {
            parentNode.add(createNodeFromChildInfo(info, ctx.collectionId));
        }

        int nextOffset = offset + page.size();
        if (nextOffset < totalCount) {
            parentNode.add(
                new DefaultMutableTreeNode(
                    new LoadMoreData(
                        ctx.collectionId,
                        ctx.parentItemId,
                        ctx.isCollectionRoot,
                        nextOffset,
                        totalCount
                    )
                )
            );
        }
    }

    private static int countChildren(ParentLoadContext ctx) {
        if (ctx.isCollectionRoot) {
            return ItemDao.getRootItemCount(ctx.collectionId);
        }
        if (ctx.parentItemId == null) {
            return 0;
        }
        return ItemDao.getChildCount(ctx.parentItemId);
    }

    private static List<ItemDao.ChildItemInfo> fetchPage(
        ParentLoadContext ctx,
        int offset,
        int limit
    ) {
        if (ctx.isCollectionRoot) {
            return ItemDao.getRootItemsPage(ctx.collectionId, offset, limit);
        }
        if (ctx.parentItemId == null) {
            return List.of();
        }
        return ItemDao.getChildItemsPage(ctx.parentItemId, offset, limit);
    }

    private ParentLoadContext resolveParentContext(
        DefaultMutableTreeNode node
    ) {
        Object uo = node.getUserObject();
        if (uo instanceof CollectionRootData crd) {
            return new ParentLoadContext(crd.collectionId, null, true);
        }
        if (
            uo instanceof TreeNodeData nd && "folder".equals(nd.itemType)
        ) {
            int collectionId = nd.collectionId != null
                ? nd.collectionId
                : findCollectionIdForNode(node);
            return new ParentLoadContext(collectionId, nd.itemId, false);
        }
        return null;
    }

    private int findCollectionIdForNode(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null) {
            Object uo = current.getUserObject();
            if (uo instanceof CollectionRootData crd) {
                return crd.collectionId;
            }
            if (
                uo instanceof TreeNodeData nd && nd.collectionId != null
            ) {
                return nd.collectionId;
            }
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return -1;
    }

    /** True when the only child is an unloaded placeholder. */
    private static boolean isUnloadedParent(DefaultMutableTreeNode node) {
        return (
            node.getChildCount() == 1 &&
            ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject() instanceof
                PlaceholderData
        );
    }

    private DefaultMutableTreeNode createNodeFromChildInfo(
        ItemDao.ChildItemInfo info,
        int collectionId
    ) {
        if ("request".equals(info.itemType)) {
            return createNodeFromData(
                buildRequestNodeData(
                    info.id,
                    info.name,
                    info.method,
                    "GET"
                )
            );
        }
        return createNodeFromData(
            new TreeNodeData(
                info.id,
                info.name,
                info.itemType,
                info.name,
                collectionId
            )
        );
    }

    private DefaultMutableTreeNode createNodeFromData(TreeNodeData nodeData) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeData);
        if ("folder".equals(nodeData.itemType)) {
            // Placeholder so Swing shows an expand arrow; real children load on expand.
            node.add(new DefaultMutableTreeNode(new PlaceholderData()));
        }
        return node;
    }

    private static final class ParentLoadContext {

        final int collectionId;
        final Integer parentItemId;
        final boolean isCollectionRoot;

        ParentLoadContext(
            int collectionId,
            Integer parentItemId,
            boolean isCollectionRoot
        ) {
            this.collectionId = collectionId;
            this.parentItemId = parentItemId;
            this.isCollectionRoot = isCollectionRoot;
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
            rootNode.add(
                createUnloadedCollectionNode(
                    collectionInfo.id,
                    collectionInfo.name
                )
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

        // Unloaded shell only — children load on first expand.
        DefaultMutableTreeNode collectionNode = createUnloadedCollectionNode(
            collectionId,
            collectionName
        );

        // Add to root (insert at beginning to show newest first)
        model.insertNodeInto(collectionNode, rootNode, 0);

        // Expand root so the collection is visible; leave the collection collapsed
        // so large imports do not materialise thousands of nodes immediately.
        TreePath rootPath = new TreePath(rootNode);
        tree.expandPath(rootPath);
        return collectionNode;
    }

    /**
     * Builds a collection root with a placeholder child so Swing shows an
     * expand control. Real children load on first expansion.
     */
    private DefaultMutableTreeNode createUnloadedCollectionNode(
        int collectionId,
        String collectionName
    ) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            new CollectionRootData(collectionId, collectionName)
        );
        rootNode.add(new DefaultMutableTreeNode(new PlaceholderData()));
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

    /**
     * Placeholder child so unloaded collections/folders show an expand arrow.
     * Not selectable as a real item.
     */
    static class PlaceholderData {

        @Override
        public String toString() {
            return "Loading…";
        }
    }

    /**
     * Trailing control node: selecting it loads the next page of siblings.
     */
    static class LoadMoreData {

        final int collectionId;
        final Integer parentItemId;
        final boolean isCollectionRoot;
        final int offset;
        final int totalCount;

        LoadMoreData(
            int collectionId,
            Integer parentItemId,
            boolean isCollectionRoot,
            int offset,
            int totalCount
        ) {
            this.collectionId = collectionId;
            this.parentItemId = parentItemId;
            this.isCollectionRoot = isCollectionRoot;
            this.offset = offset;
            this.totalCount = totalCount;
        }

        @Override
        public String toString() {
            int remaining = Math.max(0, totalCount - offset);
            return "Show more… (" + remaining + " remaining)";
        }
    }
}

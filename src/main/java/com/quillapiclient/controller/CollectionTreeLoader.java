package com.quillapiclient.controller;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Materializes database rows into {@link JTree} nodes: builds the initial
 * collection roots, lazily loads a folder's children the first time it is
 * expanded, and inserts nodes for items created or renamed elsewhere.
 */
class CollectionTreeLoader {

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

    private final JTree tree;
    private int currentCollectionId = -1;

    CollectionTreeLoader(JTree tree) {
        this.tree = tree;
    }

    int getCurrentCollectionId() {
        return currentCollectionId;
    }

    private DefaultTreeModel model() {
        return (DefaultTreeModel) tree.getModel();
    }

    DefaultMutableTreeNode findCollectionNode(int collectionId) {
        DefaultTreeModel model = model();
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

    void addRequestNode(
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

    void addFolderNode(
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
        DefaultTreeModel model = model();

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
    void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
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
        model().nodeStructureChanged(node);
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

    private static String canonicalMethod(
        String method,
        String defaultMethod
    ) {
        if (method == null || method.isBlank()) {
            return defaultMethod;
        }
        return method.toUpperCase().intern();
    }

    private DefaultMutableTreeNode findNodeDepthFirst(
        DefaultMutableTreeNode start,
        Predicate<Object> match
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

    /**
     * Imports a Postman collection file into the database on a background
     * thread (streaming, so the whole file is never held in memory) and adds
     * it to the UI tree when done.
     *
     * @param file The Postman collection JSON file
     */
    void loadCollectionFile(File file) {
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
    void loadAllCollections() {
        List<CollectionDao.CollectionInfo> collections =
            CollectionDao.getAllCollections();

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            "Collections"
        );

        for (CollectionDao.CollectionInfo collectionInfo : collections) {
            rootNode.add(
                createCollectionNode(collectionInfo.id, collectionInfo.name)
            );
        }

        DefaultTreeModel model = new EditableCollectionTreeModel(
            rootNode,
            tree
        );

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
    DefaultMutableTreeNode addCollectionToTree(
        int collectionId,
        String collectionName
    ) {
        DefaultTreeModel model = model();
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
    void updateRequestNodeMethod(int itemId, String method) {
        if (itemId <= 0) {
            return;
        }

        DefaultTreeModel model = model();
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
}

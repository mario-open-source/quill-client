package com.quillapiclient.controller;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import com.quillapiclient.db.LiteConnection;
import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Nodes whose children are currently being read on a worker, mapped to
     * {@code afterLoad} callbacks that should run once the level lands.
     * Identity map: tree nodes have no useful equals/hashCode.
     * Touched only on the EDT (expand listener, context-menu inserts, worker
     * {@code done()}).
     */
    private final Map<DefaultMutableTreeNode, List<Runnable>> loadsInFlight =
        new IdentityHashMap<>();

    CollectionTreeLoader(JTree tree) {
        this.tree = tree;
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
                uo instanceof TreeNodeData data &&
                data.kind == TreeNodeData.Kind.COLLECTION &&
                data.id == collectionId
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
            TreeNodeData.request(
                itemId,
                requestName,
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
            TreeNodeData.folder(itemId, folderName)
        );
    }

    private void addNodeToCollection(
        int collectionId,
        Integer parentFolderId,
        TreeNodeData nodeData
    ) {
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
                    nd.kind == TreeNodeData.Kind.FOLDER &&
                    nd.id == parentFolderId
            );

            if (folderNode == null) {
                // The folder lives under a level that was never materialized,
                // so there is no node to hang the new item on. Falling back to
                // the collection root would show a parent the database does not
                // agree with; leave the tree alone instead. The item is already
                // persisted and appears when its folder is expanded.
                System.err.println(
                    "Tree insert skipped: parent folder " +
                    parentFolderId +
                    " is not loaded in the tree"
                );
                return;
            }
            parentNode = folderNode;
        }

        if (hasUnloadedChildren(parentNode)) {
            // Parent level was never materialized. The new item is already in
            // the database, so loading the level picks it up; select it once
            // the load lands.
            DefaultMutableTreeNode target = parentNode;
            loadChildren(parentNode, () -> selectNodeById(target, nodeData.id));
            return;
        }

        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(nodeData);
        model().insertNodeInto(
            newNode,
            parentNode,
            parentNode.getChildCount()
        );
        selectNode(newNode);
    }

    private void selectNodeById(DefaultMutableTreeNode parent, int itemId) {
        DefaultMutableTreeNode node = findNodeDepthFirst(
            parent,
            uo -> uo instanceof TreeNodeData nd && nd.id == itemId
        );
        if (node != null) {
            selectNode(node);
        }
    }

    private void selectNode(DefaultMutableTreeNode node) {
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path.getParentPath());
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
    }

    /**
     * Loads the children of a collection or folder node from the database the
     * first time it is expanded, replacing the loading placeholder.
     */
    void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
        loadChildren(node, null);
    }

    /**
     * Reads one level of children off the EDT and swaps them in when they
     * arrive, running {@code afterLoad} once the level is materialized.
     *
     * <p>The query runs on a worker because a flat level can hold thousands of
     * rows: doing it inline in the expand listener would freeze the UI for as
     * long as the read takes. The node keeps its placeholder ("Loading...")
     * until the rows land.
     *
     * <p>If a load is already in flight for {@code node}, no second worker is
     * started; {@code afterLoad} is queued and runs with any other callbacks
     * when that load finishes successfully.
     */
    private void loadChildren(
        DefaultMutableTreeNode node,
        Runnable afterLoad
    ) {
        if (!hasUnloadedChildren(node)) {
            if (afterLoad != null) {
                afterLoad.run();
            }
            return;
        }

        if (!(node.getUserObject() instanceof TreeNodeData data)) {
            return;
        }
        if (data.kind == TreeNodeData.Kind.REQUEST) {
            return;
        }

        // Expand + insert can both request the same level. One worker; queue
        // every afterLoad so selection still runs when the first load lands.
        List<Runnable> pending = loadsInFlight.get(node);
        if (pending != null) {
            if (afterLoad != null) {
                pending.add(afterLoad);
            }
            return;
        }

        List<Runnable> callbacks = new ArrayList<>(1);
        if (afterLoad != null) {
            callbacks.add(afterLoad);
        }
        loadsInFlight.put(node, callbacks);

        // COLLECTION roots filter by collection id; folders only need parent id
        int collectionId = data.kind == TreeNodeData.Kind.COLLECTION
            ? data.id
            : -1;
        Integer parentId = data.kind == TreeNodeData.Kind.FOLDER
            ? data.id
            : null;

        new SwingWorker<List<ItemDao.ChildRow>, Void>() {
            @Override
            protected List<ItemDao.ChildRow> doInBackground() {
                // Off the EDT, so this needs its own connection rather than the
                // shared singleton the EDT is using.
                return LiteConnection.withNewConnection(conn ->
                    ItemDao.getChildRows(collectionId, parentId)
                );
            }

            @Override
            protected void done() {
                List<Runnable> queued = loadsInFlight.remove(node);

                List<ItemDao.ChildRow> rows;
                try {
                    rows = get();
                } catch (Exception e) {
                    System.err.println(
                        "Failed to load tree children: " + e.getMessage()
                    );
                    e.printStackTrace();
                    // Leave the placeholder so a later expand can retry.
                    // Do not run afterLoad: the level never materialized.
                    return;
                }

                node.removeAllChildren();
                for (ItemDao.ChildRow row : rows) {
                    node.add(createNodeForRow(row));
                }
                model().nodeStructureChanged(node);
                // nodeStructureChanged collapses the node, so re-apply the
                // expansion the user asked for when they triggered the load.
                tree.expandPath(new TreePath(node.getPath()));

                if (queued != null) {
                    for (Runnable callback : queued) {
                        callback.run();
                    }
                }
            }
        }.execute();
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
        TreeNodeData.Kind kind = TreeNodeData.Kind.fromDbItemType(row.itemType);
        TreeNodeData data = kind == TreeNodeData.Kind.REQUEST
            ? TreeNodeData.request(row.id, row.name, row.method)
            : TreeNodeData.folder(row.id, row.name);

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(data);
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
            TreeNodeData.collection(collectionId, collectionName)
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
            if (
                child.getUserObject() instanceof TreeNodeData data &&
                data.kind == TreeNodeData.Kind.COLLECTION &&
                data.id == collectionId
            ) {
                model.removeNodeFromParent(child);
                break;
            }
        }

        // Build a lazy collection node; contents load on first expand
        DefaultMutableTreeNode collectionNode = createCollectionNode(
            collectionId,
            collectionName
        );

        // Add to root (insert at beginning to show newest first)
        model.insertNodeInto(collectionNode, rootNode, 0);

        // Expand root if not already expanded
        TreePath rootPath = new TreePath(rootNode);
        tree.expandPath(rootPath);

        // Deliberately left collapsed: expanding here would materialize the
        // whole first level (thousands of nodes for a flat export) right after
        // import, which is what the lazy tree exists to avoid. Select it so the
        // import is visible; expanding is the user's call.
        selectNode(collectionNode);
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
                nd.kind == TreeNodeData.Kind.REQUEST &&
                nd.id == itemId
        );

        if (requestNode == null) {
            // Node not materialized yet; it will show the fresh method from
            // the database when its parent is expanded
            return;
        }

        if (!(requestNode.getUserObject() instanceof TreeNodeData nodeData)) {
            return;
        }

        requestNode.setUserObject(
            nodeData.withMethod(canonicalMethod(method, "GET"))
        );
        model.nodeChanged(requestNode);
        tree.repaint();
    }
}

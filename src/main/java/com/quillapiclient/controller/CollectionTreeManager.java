package com.quillapiclient.controller;

import com.quillapiclient.components.MethodTreeCellRenderer;
import com.quillapiclient.objects.Request;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

public class CollectionTreeManager {

    private JTree tree;
    private final CollectionTreeLoader loader;
    private final CollectionTreeActions actions;
    private final RequestController requestController;
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
        // Built in two steps so EditableCollectionTreeModel captures the real
        // tree reference: constructing it inline here as the JTree's initial
        // model would read the `tree` field before this assignment completes.
        tree = new JTree(emptyRoot);
        tree.setModel(new EditableCollectionTreeModel(emptyRoot, tree));
        loader = new CollectionTreeLoader(tree);
        actions = new CollectionTreeActions(tree, requestController, loader);
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
                        loader.loadChildrenIfNeeded(node);
                    }
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent event) {}
            }
        );

        // Add right-click context menu
        new CollectionTreeContextMenu(
            tree,
            actions::handleAddRequest,
            actions::handleAddFolder,
            actions::handleDeleteItem,
            actions::handleRenameItem,
            actions::exportCollection
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
        actions.createCollectionAndStartEditing();
    }

    public void exportCollection(int collectionId) {
        actions.exportCollection(collectionId);
    }

    /**
     * Imports a Postman collection file into the database on a background
     * thread and adds it to the UI tree when done.
     *
     * @param file The Postman collection JSON file
     */
    public void loadCollectionFile(File file) {
        loader.loadCollectionFile(file);
    }

    /**
     * Loads all collections from the database and builds the UI tree.
     * Only the collection roots are materialized; their contents load lazily
     * when a node is first expanded.
     */
    public void loadAllCollections() {
        loader.loadAllCollections();
    }

    /**
     * Updates the method shown for a request node already present in the tree.
     *
     * @param itemId The request item ID
     * @param method The HTTP method to display
     */
    public void updateRequestNodeMethod(int itemId, String method) {
        loader.updateRequestNodeMethod(itemId, method);
    }

    /**
     * Gets the current collection ID.
     *
     * @return The collection ID, or -1 if no collection is loaded
     */
    public int getCurrentCollectionId() {
        return loader.getCurrentCollectionId();
    }

    public JTree getTree() {
        return tree;
    }
}

package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import com.quillapiclient.objects.PostmanCollection;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Handles the context-menu-triggered CRUD actions (add/delete/rename request,
 * folder, and collection; export) by prompting the user, persisting the
 * change via the DAOs, and delegating tree-node insertion to {@link
 * CollectionTreeLoader}.
 */
class CollectionTreeActions {

    private final JTree tree;
    private final RequestController requestController;
    private final CollectionTreeLoader loader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    CollectionTreeActions(
        JTree tree,
        RequestController requestController,
        CollectionTreeLoader loader
    ) {
        this.tree = tree;
        this.requestController = requestController;
        this.loader = loader;
    }

    /**
     * Handles adding a new request when user clicks "Add Request" from context menu
     */
    void handleAddRequest(int collectionId, Integer parentId) {
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
            loader.addRequestNode(
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
    void handleAddFolder(int collectionId, Integer parentId) {
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
            loader.addFolderNode(collectionId, parentId, newItemId, folderName);
        } else {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to create new folder",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    void handleDeleteItem(
        TreeNodeData.Kind kind,
        int collectionId,
        Integer itemId,
        DefaultMutableTreeNode node
    ) {
        if (collectionId <= 0 || kind == null || node == null) {
            return;
        }

        String itemLabel = kind.displayLabel();

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

        boolean deleted = switch (kind) {
            case COLLECTION -> CollectionDao.deleteCollection(collectionId);
            case FOLDER, REQUEST -> itemId != null && ItemDao.deleteItem(itemId);
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

    void handleRenameItem(
        TreeNodeData.Kind kind,
        int collectionId,
        Integer itemId,
        DefaultMutableTreeNode node
    ) {
        if (collectionId <= 0 || itemId == null || node == null) {
            return;
        }
        if (!kind.isContextRenamable()) {
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

    void createCollectionAndStartEditing() {
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

        DefaultMutableTreeNode collectionNode = loader.addCollectionToTree(
            collectionId,
            defaultName
        );
        if (collectionNode != null) {
            TreePath path = new TreePath(collectionNode.getPath());
            tree.setSelectionPath(path);
            tree.startEditingAtPath(path);
        }
    }

    void exportCollection(int collectionId) {
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
}

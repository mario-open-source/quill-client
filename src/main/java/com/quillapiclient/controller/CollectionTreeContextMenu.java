package com.quillapiclient.controller;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class CollectionTreeContextMenu {

    private final JTree tree;
    private final AddRequestHandler addRequestHandler;
    private final AddFolderHandler addFolderHandler;
    private final DeleteHandler deleteHandler;
    private final RenameHandler renameHandler;
    private final ExportHandler exportHandler;
    private final JPopupMenu popupMenu;
    private Integer contextCollectionId;
    private Integer contextParentId;
    private Integer contextItemId;
    private TreeNodeData.Kind contextKind;
    private DefaultMutableTreeNode contextNode;
    private JMenuItem addRequestItem;
    private JMenuItem addFolderItem;
    private JMenuItem renameItem;
    private JMenuItem deleteItem;
    private JMenuItem exportItem;

    public CollectionTreeContextMenu(
        JTree tree,
        AddRequestHandler addRequestHandler,
        AddFolderHandler addFolderHandler,
        DeleteHandler deleteHandler,
        RenameHandler renameHandler,
        ExportHandler exportHandler
    ) {
        this.tree = tree;
        this.addRequestHandler = addRequestHandler;
        this.addFolderHandler = addFolderHandler;
        this.deleteHandler = deleteHandler;
        this.renameHandler = renameHandler;
        this.exportHandler = exportHandler;
        this.popupMenu = new JPopupMenu();
        setupContextMenu();
    }

    private void setupContextMenu() {
        addRequestItem = new JMenuItem("Add Request");
        addRequestItem.addActionListener(event -> {
            if (contextCollectionId != null) {
                addRequestHandler.onAddRequest(
                    contextCollectionId,
                    contextParentId
                );
            }
        });

        addFolderItem = new JMenuItem("Add Folder");
        addFolderItem.addActionListener(event -> {
            if (contextCollectionId != null) {
                addFolderHandler.onAddFolder(
                    contextCollectionId,
                    contextParentId
                );
            }
        });

        renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(event -> {
            if (
                contextCollectionId != null &&
                contextKind != null &&
                contextItemId != null &&
                contextNode != null
            ) {
                renameHandler.onRename(
                    contextKind,
                    contextCollectionId,
                    contextItemId,
                    contextNode
                );
            }
        });

        deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(event -> {
            if (
                contextCollectionId != null &&
                contextKind != null &&
                contextNode != null
            ) {
                deleteHandler.onDelete(
                    contextKind,
                    contextCollectionId,
                    contextItemId,
                    contextNode
                );
            }
        });

        exportItem = new JMenuItem("Export Collection");
        exportItem.addActionListener(event -> {
            if (contextCollectionId != null) {
                exportHandler.onExport(contextCollectionId);
            }
        });

        popupMenu.add(addRequestItem);
        popupMenu.add(addFolderItem);
        popupMenu.addSeparator();
        popupMenu.add(renameItem);
        popupMenu.add(deleteItem);
        popupMenu.addSeparator();
        popupMenu.add(exportItem);

        tree.addMouseListener(
            new MouseAdapter() {
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
            }
        );
    }

    private void showPopupMenu(MouseEvent e) {
        // Use getClosestPathForLocation which is forgiving about x-coordinate
        // (allows right-click anywhere in the row, not just on the text)
        TreePath path = tree.getClosestPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        // Ensure click is vertically within the row (not between rows)
        Rectangle bounds = tree.getPathBounds(path);
        if (bounds != null) {
            if (e.getY() < bounds.y || e.getY() >= bounds.y + bounds.height) {
                return;
            }
        }

        tree.setSelectionPath(path);
        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof TreeNodeData nodeData)) {
            return;
        }

        Integer collectionId = resolveCollectionId(path);
        Integer parentId = null;
        Integer itemId = null;

        switch (nodeData.kind) {
            case COLLECTION -> collectionId = nodeData.id;
            case FOLDER -> {
                parentId = nodeData.id;
                itemId = nodeData.id;
            }
            case REQUEST -> itemId = nodeData.id;
        }

        if (collectionId != null && collectionId > 0) {
            contextCollectionId = collectionId;
            contextParentId = parentId;
            contextKind = nodeData.kind;
            contextItemId = itemId;
            contextNode = node;
            addRequestItem.setEnabled(nodeData.kind.isContainer());
            addFolderItem.setEnabled(nodeData.kind.isContainer());
            renameItem.setEnabled(nodeData.kind.isContextRenamable());
            renameItem.setText(buildRenameLabel(nodeData.kind));
            deleteItem.setEnabled(true);
            deleteItem.setText(buildDeleteLabel(nodeData.kind));
            exportItem.setEnabled(true);
            popupMenu.show(tree, e.getX(), e.getY());
        }
    }

    private String buildRenameLabel(TreeNodeData.Kind kind) {
        return switch (kind) {
            case FOLDER -> "Rename Folder";
            case REQUEST -> "Rename Request";
            case COLLECTION -> "Rename";
        };
    }

    private String buildDeleteLabel(TreeNodeData.Kind kind) {
        return switch (kind) {
            case COLLECTION -> "Delete Collection";
            case FOLDER -> "Delete Folder";
            case REQUEST -> "Delete Request";
        };
    }

    private Integer resolveCollectionId(TreePath path) {
        Object[] components = path.getPath();
        for (Object component : components) {
            if (component instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (
                    userObject instanceof TreeNodeData data &&
                    data.kind == TreeNodeData.Kind.COLLECTION
                ) {
                    return data.id;
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface AddRequestHandler {
        void onAddRequest(int collectionId, Integer parentId);
    }

    @FunctionalInterface
    public interface AddFolderHandler {
        void onAddFolder(int collectionId, Integer parentId);
    }

    @FunctionalInterface
    public interface DeleteHandler {
        void onDelete(
            TreeNodeData.Kind kind,
            int collectionId,
            Integer itemId,
            DefaultMutableTreeNode node
        );
    }

    @FunctionalInterface
    public interface RenameHandler {
        void onRename(
            TreeNodeData.Kind kind,
            int collectionId,
            Integer itemId,
            DefaultMutableTreeNode node
        );
    }

    @FunctionalInterface
    public interface ExportHandler {
        void onExport(int collectionId);
    }
}

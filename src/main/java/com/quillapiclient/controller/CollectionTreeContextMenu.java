package com.quillapiclient.controller;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
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
    private String contextItemType;
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
                contextItemType != null &&
                contextItemId != null &&
                contextNode != null
            ) {
                renameHandler.onRename(
                    contextItemType,
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
                contextItemType != null &&
                contextNode != null
            ) {
                deleteHandler.onDelete(
                    contextItemType,
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
        Object userObject = node.getUserObject();

        boolean isFolder = false;
        Integer collectionId = resolveCollectionId(path);
        Integer parentId = null;
        String itemType = null;
        Integer itemId = null;

        if (
            userObject instanceof
                CollectionTreeManager.CollectionRootData rootData
        ) {
            collectionId = rootData.collectionId;
            isFolder = true;
            itemType = "collection";
        } else if (
            userObject instanceof CollectionTreeManager.TreeNodeData nodeData
        ) {
            if ("folder".equals(nodeData.itemType)) {
                isFolder = true;
                parentId = nodeData.itemId;
                itemType = "folder";
                itemId = nodeData.itemId;
            } else if ("request".equals(nodeData.itemType)) {
                itemType = "request";
                itemId = nodeData.itemId;
            }
        }

        if (collectionId != null && collectionId > 0 && itemType != null) {
            boolean isRenamable =
                "folder".equals(itemType) || "request".equals(itemType);
            contextCollectionId = collectionId;
            contextParentId = parentId;
            contextItemType = itemType;
            contextItemId = itemId;
            contextNode = node;
            addRequestItem.setEnabled(isFolder);
            addFolderItem.setEnabled(isFolder);
            renameItem.setEnabled(isRenamable);
            renameItem.setText(buildRenameLabel(itemType));
            deleteItem.setEnabled(true);
            deleteItem.setText(buildDeleteLabel(itemType));
            exportItem.setEnabled(true);
            popupMenu.show(tree, e.getX(), e.getY());
        }
    }

    private String buildRenameLabel(String itemType) {
        return switch (itemType) {
            case "folder" -> "Rename Folder";
            case "request" -> "Rename Request";
            default -> "Rename";
        };
    }

    private String buildDeleteLabel(String itemType) {
        return switch (itemType) {
            case "collection" -> "Delete Collection";
            case "folder" -> "Delete Folder";
            case "request" -> "Delete Request";
            default -> "Delete";
        };
    }

    private Integer resolveCollectionId(TreePath path) {
        Object[] components = path.getPath();
        for (Object component : components) {
            if (component instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (
                    userObject instanceof
                        CollectionTreeManager.CollectionRootData rootData
                ) {
                    return rootData.collectionId;
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
            String itemType,
            int collectionId,
            Integer itemId,
            DefaultMutableTreeNode node
        );
    }

    @FunctionalInterface
    public interface RenameHandler {
        void onRename(
            String itemType,
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

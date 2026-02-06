package com.quillapiclient.controller;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CollectionTreeContextMenu {
    private final JTree tree;
    private final AddRequestHandler addRequestHandler;
    private final JPopupMenu popupMenu;
    private Integer contextCollectionId;
    private Integer contextParentId;

    public CollectionTreeContextMenu(JTree tree, AddRequestHandler addRequestHandler) {
        this.tree = tree;
        this.addRequestHandler = addRequestHandler;
        this.popupMenu = new JPopupMenu();
        setupContextMenu();
    }

    private void setupContextMenu() {
        JMenuItem addRequestItem = new JMenuItem("Add Request");
        addRequestItem.addActionListener(event -> {
            if (contextCollectionId != null) {
                addRequestHandler.onAddRequest(contextCollectionId, contextParentId);
            }
        });

        popupMenu.add(addRequestItem);

        tree.addMouseListener(new MouseAdapter() {
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
        });
    }

    private void showPopupMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        tree.setSelectionPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        boolean isFolder = false;
        Integer collectionId = resolveCollectionId(path);
        Integer parentId = null;

        if (userObject instanceof CollectionTreeManager.CollectionRootData rootData) {
            collectionId = rootData.collectionId;
            isFolder = true;
        } else if (userObject instanceof CollectionTreeManager.TreeNodeData nodeData) {
            if ("folder".equals(nodeData.itemType)) {
                isFolder = true;
                parentId = nodeData.itemId;
            }
        }

        if (isFolder && collectionId != null && collectionId > 0) {
            contextCollectionId = collectionId;
            contextParentId = parentId;
            popupMenu.show(tree, e.getX(), e.getY());
        }
    }

    private Integer resolveCollectionId(TreePath path) {
        Object[] components = path.getPath();
        for (Object component : components) {
            if (component instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (userObject instanceof CollectionTreeManager.CollectionRootData rootData) {
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
}

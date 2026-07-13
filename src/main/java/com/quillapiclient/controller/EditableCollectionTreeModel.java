package com.quillapiclient.controller;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

class EditableCollectionTreeModel extends DefaultTreeModel {

    private final JTree tree;

    EditableCollectionTreeModel(DefaultMutableTreeNode root, JTree tree) {
        super(root);
        this.tree = tree;
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
            String newName = newValue != null
                ? newValue.toString().trim()
                : "";
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

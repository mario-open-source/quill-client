package com.quillapiclient.controller;

import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.ItemDao;
import java.util.function.Function;
import java.util.function.Predicate;
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

        if (!(node.getUserObject() instanceof TreeNodeData nodeData)) {
            return;
        }

        switch (nodeData.kind) {
            case COLLECTION -> renamePathNode(
                node,
                newValue,
                nodeData.name,
                nodeData.kind.displayLabel(),
                newName -> CollectionDao.updateCollectionName(
                    nodeData.id,
                    newName
                ),
                newName -> TreeNodeData.collection(nodeData.id, newName)
            );
            case FOLDER, REQUEST -> renamePathNode(
                node,
                newValue,
                nodeData.name,
                nodeData.kind.displayLabel(),
                newName -> ItemDao.updateItemName(nodeData.id, newName),
                nodeData::withName
            );
        }
    }

    /**
     * Shared rename flow: trims the new value, bails out on empty/unchanged,
     * persists via {@code persist}, and on success replaces the node's user
     * object via {@code rebuild}. On failure shows a "Rename Failed" dialog
     * naming {@code failureLabel} and reverts the node's display text.
     */
    private void renamePathNode(
        DefaultMutableTreeNode node,
        Object newValue,
        String currentName,
        String failureLabel,
        Predicate<String> persist,
        Function<String, Object> rebuild
    ) {
        String newName = newValue != null ? newValue.toString().trim() : "";
        if (newName.isEmpty() || newName.equals(currentName)) {
            nodeChanged(node);
            return;
        }

        if (!persist.test(newName)) {
            JOptionPane.showMessageDialog(
                tree,
                "Failed to rename " + failureLabel + ".",
                "Rename Failed",
                JOptionPane.ERROR_MESSAGE
            );
            nodeChanged(node);
            return;
        }

        node.setUserObject(rebuild.apply(newName));
        nodeChanged(node);
    }
}

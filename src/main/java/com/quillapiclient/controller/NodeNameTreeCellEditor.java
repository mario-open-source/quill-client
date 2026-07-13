package com.quillapiclient.controller;

import java.awt.Component;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;

class NodeNameTreeCellEditor extends DefaultTreeCellEditor {

    NodeNameTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
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

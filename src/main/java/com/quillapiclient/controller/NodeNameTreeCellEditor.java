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
            node.getUserObject() instanceof TreeNodeData nodeData &&
            editingComponent instanceof JTextField textField
        ) {
            // Edit the plain name only — never the painted method tag.
            textField.setText(nodeData.name);
            textField.selectAll();
        }

        return component;
    }
}

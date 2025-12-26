package com.quillapiclient.utility;

import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import com.quillapiclient.controller.CollectionTreeManager.TreeNodeData;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.objects.Request;

public class FileSelectionListener implements TreeSelectionListener {
    private JTree jTree;
    private RequestSelectionCallback callback;

    /**
     * Callback interface for handling request selection
     */
    @FunctionalInterface
    public interface RequestSelectionCallback {
        void onRequestSelected(Request request);
    }

    public FileSelectionListener(JTree jTree, RequestSelectionCallback callback) {
        this.jTree = jTree;
        this.callback = callback;
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        // Get selected file/folder
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) jTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return;
        }
        
        // Get the TreeNodeData from the selected node
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof TreeNodeData)) {
            return;
        }
        
        TreeNodeData nodeData = (TreeNodeData) userObject;
        
        // Only process requests, not folders
        if (!"request".equals(nodeData.itemType)) {
            return;
        }
        
        // Query the database for the request
        Request selectedRequest = CollectionDao.getRequestByItemId(nodeData.itemId);
        if (callback != null && selectedRequest != null) {
            callback.onRequestSelected(selectedRequest);
        }
    }
}


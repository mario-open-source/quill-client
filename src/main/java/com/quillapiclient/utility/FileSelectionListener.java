package com.quillapiclient.utility;

import java.util.Map;

import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import com.quillapiclient.objects.Request;

public class FileSelectionListener implements TreeSelectionListener {
    private JTree jTree;
    private Map<String, Request> collectionMap;
    private RequestSelectionCallback callback;

    /**
     * Callback interface for handling request selection
     */
    @FunctionalInterface
    public interface RequestSelectionCallback {
        void onRequestSelected(Request request);
    }

    public FileSelectionListener(JTree jTree, Map<String, Request> collectionMap,
                                 RequestSelectionCallback callback) {
        this.jTree = jTree;
        this.collectionMap = collectionMap;
        this.callback = callback;
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        // Get selected file/folder
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) jTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return;
        }
        
        String nodeString = selectedNode.toString();
        
        // Extract the original name by removing the method part [METHOD] if present
        String originalName = nodeString;
        if (nodeString != null && nodeString.contains("[") && nodeString.contains("]")) {
            int bracketIndex = nodeString.indexOf("[");
            originalName = nodeString.substring(0, bracketIndex).trim();
        }
        
        if (!collectionMap.containsKey(originalName)) {
            return;
        }
        
        // Get the selected request and notify via callback
        Request selectedRequest = collectionMap.get(originalName);
        if (callback != null && selectedRequest != null) {
            callback.onRequestSelected(selectedRequest);
        }
    }
}


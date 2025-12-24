package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.objects.Item;
import com.quillapiclient.objects.PostmanCollection;
import com.quillapiclient.objects.Request;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CollectionTreeManager {
    private JTree tree;
    private Map<String, Request> collectionMap = new HashMap<>();
    private PostmanCollection postmanCollection;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public CollectionTreeManager() {
        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("No collection loaded");
        tree = new JTree(emptyRoot);
        // Set custom renderer to display methods with colors
        tree.setCellRenderer(new com.quillapiclient.components.MethodTreeCellRenderer());
    }
    
    public void loadCollectionFile(File file) {
        if (file == null || !file.exists()) return;
        
        collectionMap.clear();
        DefaultMutableTreeNode rootNode = buildTreeFromCollection(file);
        
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        tree.setModel(model);
        
        TreePath rootPath = new TreePath(rootNode);
        tree.expandPath(rootPath);
    }
    
    private DefaultMutableTreeNode buildTreeFromCollection(File file) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(file.getName());
        try {
            postmanCollection = objectMapper.readValue(file, PostmanCollection.class);
            // Iterate through top-level items and create tree nodes recursively
            for (Item item : postmanCollection.getItem()) {
                if (item.getRequest() != null) {
                    collectionMap.put(item.getName(), item.getRequest());
                }
                rootNode.add(buildNode(item));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rootNode;
    }
    
    private DefaultMutableTreeNode buildNode(Item item) {
        String nodeName = item.getName();
        
        // If this item has a request, append the method to the name
        if (item.getRequest() != null && item.getRequest().getMethod() != null) {
            String method = item.getRequest().getMethod().toUpperCase();
            nodeName = nodeName + " [" + method + "]";
        }
        
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeName);
        
        if (item.getItem() != null && !item.getItem().isEmpty()) {
            for (Item child : item.getItem()) {
                if (child.getRequest() != null) {
                    collectionMap.put(child.getName(), child.getRequest());
                }
                node.add(buildNode(child));
            }
        }
        return node;
    }
    
    public JTree getTree() { return tree; }
    public Map<String, Request> getCollectionMap() { return collectionMap; }
}
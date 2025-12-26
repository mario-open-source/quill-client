package com.quillapiclient.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;

public class LeftPanel {
    private JPanel panel;
    private JTree jTree;
    private JButton buttonImportCollection;
    private JButton buttonNewCollection;
    
    public LeftPanel(JTree jTree, TreeSelectionListener selectionListener, 
                     ActionListener importActionListener, ActionListener newActionListener) {
        this.jTree = jTree;
        this.panel = createPanelWithTree(jTree, selectionListener, importActionListener, newActionListener);
    }
    
    private JPanel createPanelWithTree(JTree jTree, TreeSelectionListener selectionListener,
                                       ActionListener importActionListener, ActionListener newActionListener) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Panel for Import and New buttons
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        buttonImportCollection = new JButton("Import");
        buttonNewCollection = new JButton("New");
        
        if (importActionListener != null) {
            buttonImportCollection.addActionListener(importActionListener);
        }
        if (newActionListener != null) {
            buttonNewCollection.addActionListener(newActionListener);
        }
        
        buttonPanel.add(buttonImportCollection, BorderLayout.WEST);
        buttonPanel.add(buttonNewCollection, BorderLayout.EAST);
        
        panel.add(buttonPanel);

        // Use the provided JTree and add a selection listener
        jTree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        jTree.addTreeSelectionListener(selectionListener);

        // Wrap the tree in a scroll pane to enable scrolling when it becomes too large
        JScrollPane scrollPane = new JScrollPane(jTree);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        panel.add(scrollPane);

        return panel;
    }
    
    public JPanel getPanel() {
        return panel;
    }
    
    public JTree getTree() {
        return jTree;
    }
    
    public JButton getImportButton() {
        return buttonImportCollection;
    }
    
    public JButton getNewButton() {
        return buttonNewCollection;
    }
}


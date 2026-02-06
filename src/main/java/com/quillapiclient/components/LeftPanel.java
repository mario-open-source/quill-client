package com.quillapiclient.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;

public class LeftPanel {
    private JPanel panel;
    private JTree jTree;
    private JButton buttonImportCollection;
    private JButton buttonNewCollection;
    private final String IMPORT_TEXT = "Import";
    private final String NEW_TEXT = "New";
    public LeftPanel(JTree jTree, TreeSelectionListener selectionListener, 
                     ActionListener importActionListener, ActionListener newActionListener) {
        this.jTree = jTree;
        this.panel = createPanelWithTree(jTree, selectionListener, importActionListener, newActionListener);
    }
    
    private JPanel createPanelWithTree(JTree jTree, TreeSelectionListener selectionListener,
                                       ActionListener importActionListener, ActionListener newActionListener) {
        // Use BorderLayout to properly fill available space
        JPanel panel = new JPanel(new BorderLayout());

        // Panel for Import and New buttons
        JPanel buttonPanel = new JPanel(new BorderLayout());

        // Set fixed height for all buttons (30 pixels to match TopPanel) - only constrain height, not width
        int fixedHeight = 35;
        
        buttonImportCollection = new JButton(IMPORT_TEXT);
        buttonImportCollection.setMargin(new Insets(0, 10, 0, 10)); // Remove vertical padding
        Dimension importSize = buttonImportCollection.getPreferredSize();
        buttonImportCollection.setPreferredSize(new Dimension(importSize.width, fixedHeight));
        buttonImportCollection.setMinimumSize(new Dimension(importSize.width, fixedHeight));
        buttonImportCollection.setMaximumSize(new Dimension(importSize.width, fixedHeight));
        
        buttonNewCollection = new JButton(NEW_TEXT);
        buttonNewCollection.setMargin(new Insets(0, 10, 0, 10)); // Remove vertical padding
        Dimension newSize = buttonNewCollection.getPreferredSize();
        buttonNewCollection.setPreferredSize(new Dimension(newSize.width, fixedHeight));
        buttonNewCollection.setMinimumSize(new Dimension(newSize.width, fixedHeight));
        buttonNewCollection.setMaximumSize(new Dimension(newSize.width, fixedHeight));
        
        if (importActionListener != null) {
            buttonImportCollection.addActionListener(importActionListener);
        }
        if (newActionListener != null) {
            buttonNewCollection.addActionListener(newActionListener);
        }

        // Label on the left
        JLabel titleLabel = new JLabel("QuillClient");
        titleLabel.setFont(titleLabel.getFont().deriveFont(titleLabel.getFont().getSize2D() + 2f));
        buttonPanel.add(titleLabel, BorderLayout.WEST);

        // Both buttons on the right in a flow panel
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        rightButtonPanel.add(buttonNewCollection);
        rightButtonPanel.add(buttonImportCollection);
        buttonPanel.add(rightButtonPanel, BorderLayout.EAST);
        
        // Add button panel at the top
        panel.add(buttonPanel, BorderLayout.NORTH);

        // Use the provided JTree and add a selection listener
        jTree.addTreeSelectionListener(selectionListener);

        // Wrap the tree in a scroll pane for the Collections tab
        JScrollPane collectionsPane = new JScrollPane(jTree);
        collectionsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        collectionsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Wrap the tree in a scroll pane for the Environments tab
        JScrollPane environmentsPane = new JScrollPane(new Label("Environments"));
        environmentsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        environmentsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Tabbed pane: Collections and Environments
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Collections", collectionsPane);
        tabbedPane.addTab("Environments", environmentsPane);

        panel.add(tabbedPane, BorderLayout.CENTER);

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


package com.quillapiclient.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;

public class LeftPanel {
    private JPanel panel;
    private JTree jTree;
    private JList<String> environmentList;
    private JLabel titleLabel;
    private JButton buttonImportCollection;
    private JButton buttonNewCollection;
    private JButton buttonAddCollectionTab;
    private JButton buttonAddEnvironmentTab;
    private final String APP_TITLE = "QuillClient";
    private final String IMPORT_TEXT = "Import";
    private final String NEW_TEXT = "New";
    private final String ADD_TEXT = "+";
    public LeftPanel(JTree jTree, JList<String> environmentList, TreeSelectionListener selectionListener,
                     ActionListener importActionListener, ActionListener newActionListener,
                     ActionListener addCollectionTabActionListener, ActionListener addEnvironmentTabActionListener) {
        this.jTree = jTree;
        this.environmentList = environmentList;
        this.panel = createPanelWithTree(jTree, environmentList, selectionListener, importActionListener,
            newActionListener, addCollectionTabActionListener, addEnvironmentTabActionListener);
    }
    
    private JPanel createPanelWithTree(JTree jTree, JList<String> environmentList, TreeSelectionListener selectionListener,
                                       ActionListener importActionListener, ActionListener newActionListener,
                                       ActionListener addCollectionTabActionListener,
                                       ActionListener addEnvironmentTabActionListener) {
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
        titleLabel = new JLabel(APP_TITLE);
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
        JScrollPane environmentsPane = new JScrollPane(environmentList);
        environmentsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        environmentsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Tabbed pane: Collections and Environments
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Collections", collectionsPane);
        tabbedPane.addTab("Environments", environmentsPane);
        tabbedPane.setTabComponentAt(0, createTabHeader("Collections", true, addCollectionTabActionListener));
        tabbedPane.setTabComponentAt(1, createTabHeader("Environments", false, addEnvironmentTabActionListener));

        panel.add(tabbedPane, BorderLayout.CENTER);

        return panel;
    }
    
    public JPanel getPanel() {
        return panel;
    }
    
    public JTree getTree() {
        return jTree;
    }

    public JList<String> getEnvironmentList() {
        return environmentList;
    }
    
    public JButton getImportButton() {
        return buttonImportCollection;
    }
    
    public JButton getNewButton() {
        return buttonNewCollection;
    }

    private JPanel createTabHeader(String title, boolean isCollectionsTab, ActionListener addActionListener) {
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabHeader.setOpaque(false);

        JLabel tabLabel = new JLabel(title);
        JButton addButton = new JButton(ADD_TEXT);
        addButton.setMargin(new Insets(0, 6, 0, 6));
        addButton.setFocusable(false);
        if (addActionListener != null) {
            addButton.addActionListener(addActionListener);
        } else {
            addButton.setEnabled(false);
        }

        if (isCollectionsTab) {
            buttonAddCollectionTab = addButton;
        } else {
            buttonAddEnvironmentTab = addButton;
        }

        tabHeader.add(tabLabel);
        tabHeader.add(addButton);
        return tabHeader;
    }

    public void setActiveEnvironmentName(String environmentName) {
        if (titleLabel == null) {
            return;
        }

        if (environmentName == null || environmentName.trim().isEmpty()) {
            titleLabel.setText(APP_TITLE);
            titleLabel.setToolTipText("No active environment");
            return;
        }

        String trimmedName = environmentName.trim();
        titleLabel.setText(APP_TITLE + " - Env: " + abbreviate(trimmedName, 24));
        titleLabel.setToolTipText("Active environment: " + trimmedName);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return "...";
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}

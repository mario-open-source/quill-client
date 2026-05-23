package com.quillapiclient.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;

public class LeftPanel {

    private JPanel panel;
    private JTree jTree;
    private JList<String> environmentList;
    private JLabel titleLabel;
    private JButton buttonImportCollection;
    private JButton buttonImportEnvironment;
    private JButton buttonAddCollectionTab;
    private JButton buttonAddEnvironmentTab;
    private final String APP_TITLE = "QuillClient";
    private final String IMPORT_TEXT = "Import";
    private final String NEW_TEXT = "New";

    public LeftPanel(
        JTree jTree,
        JList<String> environmentList,
        ActionListener importActionListener,
        ActionListener addCollectionTabActionListener,
        ActionListener addEnvironmentTabActionListener
    ) {
        this.jTree = jTree;
        this.environmentList = environmentList;
        this.panel = createPanelWithTree(
            jTree,
            environmentList,
            importActionListener,
            addCollectionTabActionListener,
            addEnvironmentTabActionListener
        );
    }

    private JPanel createPanelWithTree(
        JTree jTree,
        JList<String> environmentList,
        ActionListener importActionListener,
        ActionListener addCollectionTabActionListener,
        ActionListener addEnvironmentTabActionListener
    ) {
        // Use BorderLayout to properly fill available space
        JPanel panel = new JPanel(new BorderLayout());

        // Top area: just the title label
        JPanel topPanel = new JPanel();
        titleLabel = new JLabel(APP_TITLE);
        titleLabel.setFont(
            titleLabel
                .getFont()
                .deriveFont(titleLabel.getFont().getSize2D() + 2f)
        );
        topPanel.add(titleLabel);

        panel.add(topPanel, BorderLayout.NORTH);

        // ---- Collections tab ----
        buttonAddCollectionTab = new JButton(NEW_TEXT);
        buttonAddCollectionTab.setMargin(new Insets(0, 10, 0, 10));
        if (addCollectionTabActionListener != null) {
            buttonAddCollectionTab.addActionListener(
                addCollectionTabActionListener
            );
        }

        buttonImportCollection = new JButton(IMPORT_TEXT);
        buttonImportCollection.setMargin(new Insets(0, 10, 0, 10));
        if (importActionListener != null) {
            buttonImportCollection.addActionListener(importActionListener);
        }

        JPanel collectionButtonRow = new JPanel(new GridLayout(1, 2, 4, 0));
        collectionButtonRow.add(buttonImportCollection);
        collectionButtonRow.add(buttonAddCollectionTab);

        JScrollPane collectionScrollPane = new JScrollPane(jTree);
        collectionScrollPane.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        collectionScrollPane.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );

        JPanel collectionsPane = new JPanel(new BorderLayout());
        collectionsPane.add(collectionButtonRow, BorderLayout.NORTH);
        collectionsPane.add(collectionScrollPane, BorderLayout.CENTER);

        // ---- Environments tab ----
        buttonAddEnvironmentTab = new JButton(NEW_TEXT);
        buttonAddEnvironmentTab.setMargin(new Insets(0, 10, 0, 10));
        if (addEnvironmentTabActionListener != null) {
            buttonAddEnvironmentTab.addActionListener(
                addEnvironmentTabActionListener
            );
        }

        buttonImportEnvironment = new JButton(IMPORT_TEXT);
        buttonImportEnvironment.setMargin(new Insets(0, 10, 0, 10));
        if (importActionListener != null) {
            buttonImportEnvironment.addActionListener(importActionListener);
        }

        JPanel environmentButtonRow = new JPanel(new GridLayout(1, 2, 4, 0));
        environmentButtonRow.add(buttonImportEnvironment);
        environmentButtonRow.add(buttonAddEnvironmentTab);

        JScrollPane environmentScrollPane = new JScrollPane(environmentList);
        environmentScrollPane.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        environmentScrollPane.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );

        JPanel environmentsPane = new JPanel(new BorderLayout());
        environmentsPane.add(environmentButtonRow, BorderLayout.NORTH);
        environmentsPane.add(environmentScrollPane, BorderLayout.CENTER);

        // Tabbed pane: Collections and Environments (fills width and resizes with splitter)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setMinimumSize(new Dimension(0, 0));
        tabbedPane.addTab("Collections", collectionsPane);
        tabbedPane.addTab("Environments", environmentsPane);
        tabbedPane.setTabComponentAt(0, createTabLabel("Collections"));
        tabbedPane.setTabComponentAt(1, createTabLabel("Environments"));

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

    private JPanel createTabLabel(String title) {
        JPanel tabHeader = new JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0)
        );
        tabHeader.setOpaque(false);

        JLabel tabLabel = new JLabel(title);
        tabHeader.add(tabLabel);
        return tabHeader;
    }

    public void setActiveEnvironmentName(String environmentName) {
        if (titleLabel == null) {
            return;
        }

        if (environmentName == null || environmentName.trim().isEmpty()) {
            titleLabel.setText(APP_TITLE + " - Env: <none>");
            titleLabel.setToolTipText("No active environment");
            return;
        }

        String trimmedName = environmentName.trim();
        titleLabel.setText(
            APP_TITLE + " - Env: " + abbreviate(trimmedName, 20)
        );
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

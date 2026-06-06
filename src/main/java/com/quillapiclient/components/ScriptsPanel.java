package com.quillapiclient.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/**
 * Displays and edits pre-request and post-response (test) scripts
 * for the currently selected collection or request item.
 */
public class ScriptsPanel {

    private final JPanel panel;
    private final JTextArea preRequestArea;
    private final JTextArea testArea;

    private static final String PRE_REQUEST_LABEL = "Pre-request Script";
    private static final String TEST_LABEL = "Post-response (Test) Script";

    public ScriptsPanel() {
        panel = new JPanel(new BorderLayout());

        // Pre-request area
        JPanel prePanel = buildScriptSection(PRE_REQUEST_LABEL);
        preRequestArea = createScriptTextArea();

        JScrollPane preScroll = new JScrollPane(preRequestArea);
        preScroll.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        preScroll.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        prePanel.add(preScroll, BorderLayout.CENTER);

        // Test area
        JPanel testPanel = buildScriptSection(TEST_LABEL);
        testArea = createScriptTextArea();

        JScrollPane testScroll = new JScrollPane(testArea);
        testScroll.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        testScroll.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        testPanel.add(testScroll, BorderLayout.CENTER);

        // Split pane: pre-request on top, test on bottom
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(prePanel);
        splitPane.setBottomComponent(testPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
        splitPane.setBorder(null);

        panel.add(splitPane, BorderLayout.CENTER);
    }

    private JPanel buildScriptSection(String title) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JLabel header = new JLabel(title);
        header.setFont(
            header
                .getFont()
                .deriveFont(Font.BOLD, header.getFont().getSize2D() + 1f)
        );
        section.add(header, BorderLayout.NORTH);

        return section;
    }

    private JTextArea createScriptTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setTabSize(4);
        area.setLineWrap(false);
        area.setToolTipText(
            "JavaScript — use pm.environment, pm.collectionVariables, pm.globals, pm.request, pm.response"
        );
        return area;
    }

    // ---- public API ----

    public JPanel getPanel() {
        return panel;
    }

    public JTextArea getPreRequestArea() {
        return preRequestArea;
    }

    public JTextArea getTestArea() {
        return testArea;
    }

    public String getPreRequestScript() {
        return preRequestArea.getText();
    }

    public String getTestScript() {
        return testArea.getText();
    }

    public void setPreRequestScript(String script) {
        preRequestArea.setText(script != null ? script : "");
    }

    public void setTestScript(String script) {
        testArea.setText(script != null ? script : "");
    }

    /**
     * Registers a callback that fires whenever either script text area changes.
     */
    public void addChangeListener(Runnable listener) {
        java.awt.event.KeyAdapter adapter = new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                listener.run();
            }
        };
        preRequestArea.addKeyListener(adapter);
        testArea.addKeyListener(adapter);
    }
}

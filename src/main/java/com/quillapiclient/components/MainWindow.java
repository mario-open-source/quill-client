package com.quillapiclient.components;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JSplitPane;
import javax.swing.table.TableCellEditor;

public class MainWindow {
    private JFrame frame;
    private JSplitPane horizontalSplitPane;
    private JSplitPane verticalSplitPane;
    private final String TITLE = "Quill Client";
    public MainWindow() {
        initializeFrame();
    }

    private void initializeFrame() {
        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 1000);
    }

    public void setLayout(LeftPanel leftPanel, RequestPanel mainPanel, JComponent responsePanel) {
        JTable headersTable = mainPanel.getHeadersPanel().getTable();
        leftPanel.getTree().addTreeSelectionListener(e -> {
            commitOrCancelTableEdit(headersTable);  // and any other tables
        });
        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel.getPanel(), responsePanel);
        verticalSplitPane.setDividerLocation(750);
        verticalSplitPane.setContinuousLayout(true);

        horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel.getPanel(), verticalSplitPane);
        horizontalSplitPane.setDividerLocation(350);
        horizontalSplitPane.setContinuousLayout(true);

        frame.getContentPane().add(horizontalSplitPane);
    }

    private static void commitOrCancelTableEdit(JTable table) {
        if (table == null) return;

        if (table.isEditing()) {
            TableCellEditor editor = table.getCellEditor();
            if (editor != null) {
                // Try commit. If commit fails (validation), cancel to prevent crashes.
                boolean committed = editor.stopCellEditing();
                if (!committed) {
                    editor.cancelCellEditing();
                }
            }
        }
    }

    public JFrame getFrame() {
        return frame;
    }
}
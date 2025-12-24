package com.quillapiclient.components;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JSplitPane;

public class MainWindow {
    private JFrame frame;
    private JSplitPane horizontalSplitPane;
    private JSplitPane verticalSplitPane;
    public MainWindow() {
        initializeFrame();
    }

    private void initializeFrame() {
        frame = new JFrame("Quill Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 1000);
    }

    public void setLayout(JComponent leftPanel, JComponent mainPanel, JComponent responsePanel) {
        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel, responsePanel);
        verticalSplitPane.setDividerLocation(750);
        verticalSplitPane.setContinuousLayout(true);

        horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, verticalSplitPane);
        horizontalSplitPane.setDividerLocation(250);
        horizontalSplitPane.setContinuousLayout(true);

        frame.getContentPane().add(horizontalSplitPane);
    }
    public JFrame getFrame() {
        return frame;
    }
}
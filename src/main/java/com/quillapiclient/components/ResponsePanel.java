package com.quillapiclient.components;

import com.quillapiclient.utility.AppColorTheme;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ResponsePanel {
    private JPanel panel;
    private JTextArea responseArea;
    private JLabel statusLabel;
    private JLabel durationLabel;
    private JLabel sizeLabel;
    private boolean errorState;
    private final String RESPONSE_LABEL = "Response";
    private final String STATUS_LABEL = "Status";
    private final String DURATION_LABEL = "Duration";
    private final String SIZE_LABEL = "Size";
    
    public ResponsePanel() {
        this.panel = createPanel();
        this.errorState = false;
    }
    
    private JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Create top panel with Response label on left and Status/Duration on right
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel responseLabel = new JLabel(RESPONSE_LABEL);
        
        // Create panel for Status and Duration labels on the right
        JPanel rightPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        statusLabel = new JLabel(STATUS_LABEL);
        durationLabel = new JLabel(DURATION_LABEL);
        sizeLabel = new JLabel(SIZE_LABEL);
        rightPanel.add(statusLabel);
        rightPanel.add(durationLabel);
        rightPanel.add(sizeLabel);
        topPanel.add(responseLabel, BorderLayout.WEST);
        topPanel.add(rightPanel, BorderLayout.EAST);
        
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        JScrollPane responseScroll = new JScrollPane(responseArea);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(responseScroll, BorderLayout.CENTER);
        return panel;
    }
    
    public JPanel getPanel() {
        return panel;
    }
    
    public JTextArea getResponseArea() {
        return responseArea;
    }
    
    public void setResponse(String response) {
        if (responseArea != null) {
            responseArea.setText(response);
            responseArea.setCaretPosition(0); // Scroll to top
        }
    }
    
    public void setErrorState(boolean isError) {
        this.errorState = isError;
        if (responseArea != null) {
            if (isError) {
                // Set error styling using theme colors
                responseArea.setForeground(AppColorTheme.ERROR_TEXT);
                responseArea.setBackground(AppColorTheme.ERROR_BACKGROUND);
            } else {
                // Set normal styling - use theme colors
                responseArea.setForeground(AppColorTheme.getColor("TextArea.foreground", AppColorTheme.TEXT_AREA_FOREGROUND));
                responseArea.setBackground(AppColorTheme.getColor("TextArea.background", AppColorTheme.TEXT_AREA_BACKGROUND));
            }
        }
    }
    
    public boolean isErrorState() {
        return errorState;
    }
    
    /**
     * Updates the status label with the status code from the response.
     * 
     * @param statusCode The HTTP status code
     */
    public void setStatus(int statusCode) {
        if (statusLabel != null) {
            statusLabel.setText(STATUS_LABEL + ": " + statusCode);
        }
    }
    
    /**
     * Updates the duration label with the duration from the response.
     * 
     * @param duration The duration in milliseconds
     */
    public void setDuration(long duration) {
        if (durationLabel != null) {
            durationLabel.setText(DURATION_LABEL + ": " + duration + " ms");
        }
    }

    public void setSize(String size) {
        if (sizeLabel != null) {
            sizeLabel.setText(SIZE_LABEL + ": " + size);
        }
    }
    /**
     * Resets the status and duration labels to their default values.
     */
    public void resetStatusDurationSize() {
        if (statusLabel != null) {
            statusLabel.setText(STATUS_LABEL);
        }
        if (durationLabel != null) {
            durationLabel.setText(DURATION_LABEL);
        }
        if (sizeLabel != null) {
            sizeLabel.setText(SIZE_LABEL);
        }
    }
}


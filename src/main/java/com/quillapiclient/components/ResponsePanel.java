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
    private boolean errorState;
    
    public ResponsePanel() {
        this.panel = createPanel();
        this.errorState = false;
    }
    
    private JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel responseLabel = new JLabel("Response");
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        JScrollPane responseScroll = new JScrollPane(responseArea);

        panel.add(responseLabel, BorderLayout.NORTH);
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
}


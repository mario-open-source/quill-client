package com.quillapiclient.components;

import com.quillapiclient.objects.Request;

import javax.swing.*;
import java.awt.*;

public class AuthPanel {
    private JPanel authPanel;
    private JComboBox<String> authTypeComboBox;
    private JTextField userField;
    private JTextField passField;
    private JTextField tokenField;

    private String[] authTypes = {"No auth", "Basic auth", "Bearer token", "Jwt bearer"}; 
    private final String NO_AUTH_TEXT = "No auth";
    private final String BASIC_AUTH_TEXT = "Basic auth";
    private final String BEARER_TOKEN_TEXT = "Bearer token";
    private final String JWT_BEARER_TEXT = "Jwt bearer";
    
    public AuthPanel() {
        authPanel = new JPanel(new BorderLayout());
        
        // Auth component
        authTypeComboBox = new JComboBox<>(authTypes);
        authPanel.add(authTypeComboBox, BorderLayout.NORTH);
        
        // Add action listener to update UI when selection changes
        authTypeComboBox.addActionListener(e -> updateAuthPanel());
        
        // Initialize fields
        userField = new JTextField();
        userField.setToolTipText("Username");
        passField = new JTextField();
        passField.setToolTipText("Password");
        tokenField = new JTextField();
        tokenField.setToolTipText("Token");
        
        updateAuthPanel();
    }
    
    private void updateAuthPanel() {
        if (authTypeComboBox == null || authPanel == null) {
            return;
        }
        
        // Remove all components except the combo box
        authPanel.removeAll();
        authPanel.add(authTypeComboBox, BorderLayout.NORTH);
        
        // Create a panel for the auth fields
        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
        fieldsPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        String selectedAuth = (String) authTypeComboBox.getSelectedItem();
        
        switch(selectedAuth) {
            case NO_AUTH_TEXT:
                JLabel noAuthLabel = new JLabel("No authentication required");
                noAuthLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                fieldsPanel.add(noAuthLabel);
                break;
                
            case BASIC_AUTH_TEXT:
                JLabel userLabel = new JLabel("Username:");
                userLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                userField.setAlignmentX(JTextField.LEFT_ALIGNMENT);
                userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, userField.getPreferredSize().height));
                fieldsPanel.add(userLabel);
                fieldsPanel.add(Box.createVerticalStrut(5));
                fieldsPanel.add(userField);
                fieldsPanel.add(Box.createVerticalStrut(10));
                
                JLabel passLabel = new JLabel("Password:");
                passLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                passField.setAlignmentX(JTextField.LEFT_ALIGNMENT);
                passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, passField.getPreferredSize().height));
                fieldsPanel.add(passLabel);
                fieldsPanel.add(Box.createVerticalStrut(5));
                fieldsPanel.add(passField);
                break;
                
            case BEARER_TOKEN_TEXT:
                tokenField.setToolTipText("Bearer Token");
                JLabel tokenLabel = new JLabel("Token:");
                tokenLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                tokenField.setAlignmentX(JTextField.LEFT_ALIGNMENT);
                tokenField.setMaximumSize(new Dimension(Integer.MAX_VALUE, tokenField.getPreferredSize().height));
                fieldsPanel.add(tokenLabel);
                fieldsPanel.add(Box.createVerticalStrut(5));
                fieldsPanel.add(tokenField);
                break;
                
            case JWT_BEARER_TEXT:
                tokenField.setToolTipText("JWT Token");
                JLabel jwtLabel = new JLabel("JWT Token:");
                jwtLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                tokenField.setAlignmentX(JTextField.LEFT_ALIGNMENT);
                tokenField.setMaximumSize(new Dimension(Integer.MAX_VALUE, tokenField.getPreferredSize().height));
                fieldsPanel.add(jwtLabel);
                fieldsPanel.add(Box.createVerticalStrut(5));
                fieldsPanel.add(tokenField);
                break;
        }
        
        authPanel.add(fieldsPanel, BorderLayout.CENTER);
        authPanel.revalidate();
        authPanel.repaint();
    }
    
    public void populateFromRequest(Request request) {
        if (request == null) {
            // No auth, clear all fields
            userField.setText("");
            passField.setText("");
            tokenField.setText("");
            authTypeComboBox.setSelectedItem("No auth");
            return;
        }
        
        if (request.getAuth() != null) {
            if (request.getAuth().getBasic() != null) {
                // Basic auth - username and password
                if (request.getAuth().getBasic().size() > 0) {
                    userField.setText(request.getAuth().getBasic().get(0).getValue());
                } else {
                    userField.setText("");
                }
                if (request.getAuth().getBasic().size() > 1) {
                    passField.setText(request.getAuth().getBasic().get(1).getValue());
                } else {
                    passField.setText("");
                }
                tokenField.setText("");
                // Update auth type combo box
                authTypeComboBox.setSelectedItem("Basic auth");
            } else if (request.getAuth().getBearer() != null) {
                // Bearer token auth
                if (request.getAuth().getBearer().size() > 0) {
                    tokenField.setText(request.getAuth().getBearer().get(0).getValue());
                } else {
                    tokenField.setText("");
                }
                userField.setText("");
                passField.setText("");
                // Update auth type combo box
                authTypeComboBox.setSelectedItem("Bearer token");
            } else {
                // Unknown auth type, clear all
                userField.setText("");
                passField.setText("");
                tokenField.setText("");
                authTypeComboBox.setSelectedItem("No auth");
            }
        } else {
            // No auth, clear all fields
            userField.setText("");
            passField.setText("");
            tokenField.setText("");
            authTypeComboBox.setSelectedItem("No auth");
        }
        
        // Update the UI to reflect the new auth type selection
        updateAuthPanel();
    }
    
    // Getters for controller
    public String getAuthType() { 
        return (String) authTypeComboBox.getSelectedItem(); 
    }
    
    public String getUsername() { 
        return userField != null ? userField.getText() : ""; 
    }
    
    public String getPassword() { 
        return passField != null ? passField.getText() : ""; 
    }
    
    public String getToken() { 
        return tokenField != null ? tokenField.getText() : ""; 
    }
    
    public JComboBox<String> getAuthTypeComboBox() {
        return authTypeComboBox;
    }
    
    public JPanel getPanel() { 
        return authPanel; 
    }
    
    /**
     * Sets a key listener on all auth input fields
     */
    public void setKeyListener(java.awt.event.KeyListener listener) {
        if (userField != null) {
            userField.addKeyListener(listener);
        }
        if (passField != null) {
            passField.addKeyListener(listener);
        }
        if (tokenField != null) {
            tokenField.addKeyListener(listener);
        }
        // For combo box, use action listener to enable save button
        // We'll handle this separately in RequestPanel since we can't easily create a KeyEvent
    }
}
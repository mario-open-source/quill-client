package com.quillapiclient.components;

import com.quillapiclient.objects.Header;
import com.quillapiclient.objects.Query;
import com.quillapiclient.objects.Request;

import javax.swing.*;
import java.awt.*;

public class RequestPanel {
    private JPanel panel;
    private TopPanel topPanel;
    private JTextField urlField;
    private JComboBox<String> methodDropdown;
    private JButton sendButton;
    private JTextArea bodyTextArea;
    private JTextArea headersTextArea;
    private JTextArea paramsTextArea;
    private AuthPanel authPanel;
    
    public RequestPanel() {
        panel = new JPanel(new BorderLayout());
        
        // Initialize TopPanel which contains URL, method dropdown, and send button
        topPanel = new TopPanel();
        urlField = topPanel.getUrlField();
        methodDropdown = topPanel.getMethodDropdown();
        sendButton = topPanel.getSendButton();
        
        panel.add(topPanel.getPanel(), BorderLayout.NORTH);
        panel.add(createRequestTabs(), BorderLayout.CENTER);
    }
    
    private JTabbedPane createRequestTabs() {
        JTabbedPane tabs = new JTabbedPane();
        bodyTextArea = new JTextArea();
        authPanel = new AuthPanel();
        
        headersTextArea = new JTextArea();
        headersTextArea.setToolTipText("Enter headers in format: Key: Value (one per line)");
        
        paramsTextArea = new JTextArea();
        paramsTextArea.setToolTipText("Enter query parameters in format: key=value&key2=value2 or key: value (one per line)");
        
        tabs.addTab("Body", new JScrollPane(bodyTextArea));
        tabs.addTab("Authorization", authPanel.getPanel());
        tabs.addTab("Headers", new JScrollPane(headersTextArea));
        tabs.addTab("Params", new JScrollPane(paramsTextArea));
        tabs.addTab("Scripts", new JScrollPane(new JTextArea()));
        tabs.addTab("Settings", new JScrollPane(new JTextArea()));
        return tabs;
    }
    
    public void populateFromRequest(Request request) {
        if (request == null) {
            return;
        }
        
        // Populate URL
        if (request.getUrl() != null && request.getUrl().getRaw() != null) {
            urlField.setText(request.getUrl().getRaw());
        } else {
            urlField.setText("");
        }
        
        // Populate method
        if (request.getMethod() != null) {
            methodDropdown.setSelectedItem(request.getMethod());
        }
        
        // Populate body
        if (request.getBody() != null && request.getBody().getRaw() != null) {
            bodyTextArea.setText(request.getBody().getRaw());
        } else {
            bodyTextArea.setText("");
        }
        
        // Populate headers if available
        if (request.getHeader() != null && !request.getHeader().isEmpty()) {
            StringBuilder headersBuilder = new StringBuilder();
            for (Header header : request.getHeader()) {
                // Assuming header format is [key, value, description]
                if (header.getKey() != null && header.getValue() != null) {
                    headersBuilder.append(header.getKey())
                                 .append(": ")
                                 .append(header.getValue())
                                 .append("\n");
                }
            }
            headersTextArea.setText(headersBuilder.toString());
        } else {
            headersTextArea.setText("");
        }
        
        // Populate query parameters if available
        if (request.getUrl() != null && request.getUrl().getQuery() != null) {
            StringBuilder paramsBuilder = new StringBuilder();
            for (Query queryParam : request.getUrl().getQuery()) {
                // Assuming query param format is [key, value, description]
                if (queryParam.getKey() != null && queryParam.getValue() != null) {
                    paramsBuilder.append(queryParam.getKey())
                                .append("=")
                                .append(queryParam.getValue())
                                .append("\n");
                }
            }
            paramsTextArea.setText(paramsBuilder.toString());
        } else {
            paramsTextArea.setText("");
        }
        
        // Populate auth fields
        authPanel.populateFromRequest(request);
    }
    
    // Getters for controller
    public String getUrl() { 
        return urlField.getText().trim(); 
    }
    
    public String getMethod() { 
        return (String) methodDropdown.getSelectedItem(); 
    }
    
    public String getBody() { 
        return bodyTextArea != null ? bodyTextArea.getText() : ""; 
    }
    
    public String getHeaders() { 
        return headersTextArea != null ? headersTextArea.getText() : ""; 
    }
    
    public String getParams() { 
        return paramsTextArea != null ? paramsTextArea.getText() : ""; 
    }
    
    public AuthPanel getAuthPanel() { 
        return authPanel; 
    }
    
    public JButton getSendButton() {
        return sendButton;
    }
    
    public JPanel getPanel() { 
        return panel; 
    }
}
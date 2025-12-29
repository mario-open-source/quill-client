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
    private String headersTextArea;
    private JTextArea paramsTextArea;
    private AuthPanel authPanel;
    private HeadersPanel headersPanel;
    private ParamsPanel paramsPanel;

    private final String BODY_LABEL = "Body";
    private final String AUTHORIZATION_LABEL = "Authorization";
    private final String HEADERS_LABEL = "Headers";
    private final String PARAMS_LABEL = "Params";
    private final String SCRIPTS_LABEL = "Scripts";
    private final String SETTINGS_LABEL = "Settings";
    
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
        paramsPanel = new ParamsPanel();
        headersPanel = new HeadersPanel();
        
        paramsTextArea = new JTextArea();
        paramsTextArea.setToolTipText("Enter query parameters in format: key=value&key2=value2 or key: value (one per line)");
        
        tabs.addTab(BODY_LABEL, new JScrollPane(bodyTextArea));
        tabs.addTab(AUTHORIZATION_LABEL, authPanel.getPanel());
        tabs.addTab(HEADERS_LABEL, headersPanel.getScrollPane());
        tabs.addTab(PARAMS_LABEL, paramsPanel.getScrollPane());
        tabs.addTab(SCRIPTS_LABEL, new JScrollPane(new JTextArea()));
        tabs.addTab(SETTINGS_LABEL, new JScrollPane(new JTextArea()));
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
            headersTextArea = headersBuilder.toString();
        } else {
            headersTextArea = "";
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
        headersPanel.populateFromRequest(request);
        paramsPanel.populateFromRequest(request);
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
        return headersTextArea != null ? headersTextArea : "";
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
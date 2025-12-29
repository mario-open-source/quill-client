package com.quillapiclient.components;

import com.quillapiclient.objects.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import java.awt.*;

public class RequestPanel {
    private JPanel panel;
    private TopPanel topPanel;
    private JComboBox<String> methodDropdown;
    private JButton sendButton;
    private JTextArea bodyTextArea;
    private String headersTextArea;
    private JTextArea paramsTextArea;
    private AuthPanel authPanel;
    private HeadersPanel headersPanel;
    private ParamsPanel paramsPanel;
    private Runnable saveCallback;

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
        methodDropdown = topPanel.getMethodDropdown();
        sendButton = topPanel.getSendButton();
        
        // Wire up Save button
        topPanel.getSaveButton().addActionListener(e -> {
            if (saveCallback != null) {
                saveCallback.run();
            }
        });
        
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
        //tabs.addTab(SCRIPTS_LABEL, new JScrollPane(new JTextArea()));
        //tabs.addTab(SETTINGS_LABEL, new JScrollPane(new JTextArea()));
        return tabs;
    }
    
    public void populateFromRequest(Request request) {
        if (request == null) {
            return;
        }
        
        // Populate URL (using placeholder-aware method)
        if (request.getUrl() != null && request.getUrl().getRaw() != null) {
            topPanel.setUrlText(request.getUrl().getRaw());
        } else {
            topPanel.setUrlText("");
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
        return topPanel.getUrlText(); 
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
    
    /**
     * Builds a Request object from all UI components
     */
    public Request buildRequestFromUI() {
        Request request = new Request();
        
        // Set method
        request.setMethod(getMethod());
        
        // Build URL
        Url url = new Url();
        url.setRaw(getUrl());
        url.setQuery(paramsPanel.getQueryParams());
        request.setUrl(url);
        
        // Build Body
        String bodyText = getBody();
        if (bodyText != null && !bodyText.trim().isEmpty()) {
            Body body = new Body();
            body.setMode("raw");
            body.setRaw(bodyText);
            request.setBody(body);
        }
        
        // Build Headers
        List<Header> headers = headersPanel.getHeaders();
        if (!headers.isEmpty()) {
            request.setHeader(headers);
        }
        
        // Build Auth
        Auth auth = buildAuthFromUI();
        if (auth != null) {
            request.setAuth(auth);
        }
        
        return request;
    }
    
    /**
     * Builds an Auth object from AuthPanel
     */
    private Auth buildAuthFromUI() {
        String authType = authPanel.getAuthType();
        
        if (authType == null || authType.equals("No auth")) {
            return null;
        }
        
        Auth auth = new Auth();
        auth.setType(authType.toLowerCase().replace(" ", ""));
        
        if (authType.equals("Basic auth")) {
            List<Credential> basic = new ArrayList<>();
            Credential usernameCred = new Credential();
            usernameCred.setKey("username");
            usernameCred.setValue(authPanel.getUsername());
            basic.add(usernameCred);
            
            Credential passwordCred = new Credential();
            passwordCred.setKey("password");
            passwordCred.setValue(authPanel.getPassword());
            basic.add(passwordCred);
            
            auth.setBasic(basic);
        } else if (authType.equals("Bearer token") || authType.equals("Jwt bearer")) {
            List<Credential> bearer = new ArrayList<>();
            Credential tokenCred = new Credential();
            tokenCred.setKey("token");
            tokenCred.setValue(authPanel.getToken());
            bearer.add(tokenCred);
            
            auth.setBearer(bearer);
        }
        
        return auth;
    }
    
    /**
     * Sets the callback to be executed when Save button is clicked
     */
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }
}
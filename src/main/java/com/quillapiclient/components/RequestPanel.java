package com.quillapiclient.components;

import com.quillapiclient.objects.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean isPopulating = false; // Flag to prevent listeners from enabling save during population
    private int currentItemId = -1; // Track current item ID
    private static Map<Integer, Request> unsavedChanges = new HashMap<>(); // Store unsaved changes by item ID

    private final String BODY_LABEL = "Body";
    private final String AUTHORIZATION_LABEL = "Authorization";
    private final String HEADERS_LABEL = "Headers";
    private final String PARAMS_LABEL = "Params";
    //Will see about implementing these later
    //private final String SCRIPTS_LABEL = "Scripts";
    //private final String SETTINGS_LABEL = "Settings";
    
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
        topPanel.getSaveButton().setEnabled(false);
        
        panel.add(topPanel.getPanel(), BorderLayout.NORTH);
        panel.add(createRequestTabs(), BorderLayout.CENTER);
        
        // Setup change listeners for all input fields (after components are created)
        setupChangeListeners();
    }
    
    /**
     * Sets up key listeners on all input fields to enable save button on any key press
     */
    private void setupChangeListeners() {
        // Create a simple key listener that enables the save button and stores changes (only if not populating)
        java.awt.event.KeyListener enableSaveListener = new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (!isPopulating) {
                    topPanel.getSaveButton().setEnabled(true);
                    saveCurrentStateToMemory();
                }
            }
        };
        
        // URL field
        topPanel.getUrlField().addKeyListener(enableSaveListener);
        
        // Method dropdown - use action listener instead
        methodDropdown.addActionListener(e -> {
            if (!isPopulating) {
                topPanel.getSaveButton().setEnabled(true);
                saveCurrentStateToMemory();
            }
        });
        
        // Body text area
        bodyTextArea.addKeyListener(enableSaveListener);
        
        // Headers table - use TableModelListener to catch all edits (including double-click edits)
        headersPanel.getTableModel().addTableModelListener(e -> {
            if (!isPopulating) {
                topPanel.getSaveButton().setEnabled(true);
                saveCurrentStateToMemory();
            }
        });
        headersPanel.getTable().addKeyListener(enableSaveListener);
        
        // Params table - use TableModelListener to catch all edits (including double-click edits)
        paramsPanel.getTableModel().addTableModelListener(e -> {
            if (!isPopulating) {
                topPanel.getSaveButton().setEnabled(true);
                saveCurrentStateToMemory();
            }
        });
        paramsPanel.getTable().addKeyListener(enableSaveListener);
        
        // Auth panel fields
        authPanel.setKeyListener(enableSaveListener);
        
        // Auth type combo box - use action listener
        authPanel.getAuthTypeComboBox().addActionListener(e -> {
            if (!isPopulating) {
                topPanel.getSaveButton().setEnabled(true);
                saveCurrentStateToMemory();
            }
        });
    }
    
    /**
     * Saves the current UI state to memory for the current item
     */
    private void saveCurrentStateToMemory() {
        if (currentItemId > 0) {
            Request currentState = buildRequestFromUI();
            unsavedChanges.put(currentItemId, currentState);
        }
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
    
    public void populateFromRequest(Request request, int itemId) {
        // Save current state before switching (if there was a previous item and it's different)
        if (currentItemId > 0 && currentItemId != itemId) {
            // Build current state and save it with the old itemId
            Request currentState = buildRequestFromUI();
            unsavedChanges.put(currentItemId, currentState);
        }
        
        // Update current item ID
        currentItemId = itemId;
        
        // Set flag to prevent listeners from enabling save button during population
        isPopulating = true;
        
        // Check if there are unsaved changes for this item
        Request requestToLoad = unsavedChanges.getOrDefault(itemId, request);
        
        // Disable save button if loading from database (no unsaved changes), enable if loading from memory
        topPanel.getSaveButton().setEnabled(unsavedChanges.containsKey(itemId));
        
        if (requestToLoad == null) {
            isPopulating = false;
            return;
        }
        
        // Populate URL (using placeholder-aware method) - use requestToLoad, not request
        if (requestToLoad.getUrl() != null && requestToLoad.getUrl().getRaw() != null) {
            topPanel.setUrlText(requestToLoad.getUrl().getRaw());
        } else {
            topPanel.setUrlText("");
        }
        
        // Populate method
        if (requestToLoad.getMethod() != null) {
            methodDropdown.setSelectedItem(requestToLoad.getMethod());
        }
        
        // Populate body
        if (requestToLoad.getBody() != null && requestToLoad.getBody().getRaw() != null) {
            bodyTextArea.setText(requestToLoad.getBody().getRaw());
        } else {
            bodyTextArea.setText("");
        }
        
        // Populate headers if available
        if (requestToLoad.getHeader() != null && !requestToLoad.getHeader().isEmpty()) {
            StringBuilder headersBuilder = new StringBuilder();
            for (Header header : requestToLoad.getHeader()) {
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
        if (requestToLoad.getUrl() != null && requestToLoad.getUrl().getQuery() != null) {
            StringBuilder paramsBuilder = new StringBuilder();
            for (Query queryParam : requestToLoad.getUrl().getQuery()) {
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
        authPanel.populateFromRequest(requestToLoad);
        headersPanel.populateFromRequest(requestToLoad);
        paramsPanel.populateFromRequest(requestToLoad);
        
        // Reset flag after population is complete
        isPopulating = false;
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    public void populateFromRequest(Request request) {
        populateFromRequest(request, currentItemId);
    }
    
    /**
     * Clears unsaved changes for the current item (called after successful save)
     */
    public void clearUnsavedChanges() {
        if (currentItemId > 0) {
            unsavedChanges.remove(currentItemId);
        }
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
    
    /**
     * Gets the save button for external access
     */
    public JButton getSaveButton() {
        return topPanel.getSaveButton();
    }
}
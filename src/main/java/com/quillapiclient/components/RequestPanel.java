package com.quillapiclient.components;

import com.quillapiclient.objects.*;
import com.quillapiclient.utility.ScriptLoader;
import java.awt.*;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

public class RequestPanel {

    private JPanel panel;
    private TopPanel topPanel;
    private JComboBox<String> methodDropdown;
    private JButton sendButton;
    private JButton saveButton;
    private RSyntaxTextArea bodyTextArea;
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
    private final String SCRIPTS_LABEL = "Scripts";

    private ScriptsPanel scriptsPanel;
    private final ScriptLoader scriptLoader = new ScriptLoader();

    public RequestPanel() {
        panel = new JPanel(new BorderLayout());

        // Initialize TopPanel which contains URL, method dropdown, and send button
        topPanel = new TopPanel();
        methodDropdown = topPanel.getMethodDropdown();
        sendButton = topPanel.getSendButton();
        saveButton = topPanel.getSaveButton();

        // Wire up Save button
        topPanel.getSaveButton().addActionListener(e -> {
            if (saveCallback != null) {
                saveCallback.run();
            }
        });

        panel.add(topPanel.getPanel(), BorderLayout.NORTH);
        panel.add(createRequestTabs(), BorderLayout.CENTER);

        // Setup change listeners for all input fields (after components are created)
        setupChangeListeners();
    }

    /**
     * Sets up change listeners on all sub-panels to track dirty state.
     * Each sub-panel owns its own listener wiring; RequestPanel only registers callbacks.
     */
    private void setupChangeListeners() {
        Runnable markDirty = () -> {
            if (!isPopulating) {
                try {
                    saveCurrentStateToMemory();
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        };

        // Sub-panels — each handles its own internal listener wiring
        topPanel.addChangeListener(markDirty);
        headersPanel.addChangeListener(markDirty);
        paramsPanel.addChangeListener(markDirty);
        authPanel.addChangeListener(markDirty);
        scriptsPanel.addChangeListener(markDirty);

        // Body text area is owned directly by RequestPanel
        bodyTextArea.addKeyListener(
            new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    markDirty.run();
                }
            }
        );
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

        // Body text area with JSON syntax highlighting
        bodyTextArea = new RSyntaxTextArea();
        bodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        bodyTextArea.setCodeFoldingEnabled(true);
        bodyTextArea.setAntiAliasingEnabled(true);
        // Inherit size from FlatLaf default, but use monospaced for code
        Font defaultFont = javax.swing.UIManager.getFont("TextArea.font");
        if (defaultFont != null) {
            bodyTextArea.setFont(
                new Font(
                    defaultFont.getFontName(),
                    defaultFont.getStyle(),
                    defaultFont.getSize()
                )
            );
        }
        bodyTextArea.setTabSize(2);
        bodyTextArea.setTabsEmulated(true);
        // Apply dark theme
        try {
            Theme theme = Theme.load(
                getClass().getResourceAsStream(
                    "/org/fifesoft/rsyntaxtextarea/themes/dark.xml"
                )
            );
            theme.apply(bodyTextArea);
        } catch (Exception e) {
            // Fallback: manual dark styling
            bodyTextArea.setBackground(new java.awt.Color(25, 25, 25));
            bodyTextArea.setForeground(new java.awt.Color(220, 220, 220));
            bodyTextArea.setCaretColor(new java.awt.Color(220, 220, 220));
            bodyTextArea.setCurrentLineHighlightColor(
                new java.awt.Color(35, 35, 35)
            );
        }

        authPanel = new AuthPanel();
        paramsPanel = new ParamsPanel();
        headersPanel = new HeadersPanel();

        paramsTextArea = new JTextArea();
        paramsTextArea.setToolTipText(
            "Enter query parameters in format: key=value&key2=value2 or key: value (one per line)"
        );

        tabs.addTab(BODY_LABEL, new JScrollPane(bodyTextArea));
        tabs.addTab(AUTHORIZATION_LABEL, authPanel.getPanel());
        tabs.addTab(HEADERS_LABEL, headersPanel.getScrollPane());
        tabs.addTab(PARAMS_LABEL, paramsPanel.getScrollPane());

        scriptsPanel = new ScriptsPanel();
        tabs.addTab(SCRIPTS_LABEL, scriptsPanel.getPanel());

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

        // Populate URL and method (delegated to TopPanel)
        topPanel.populateFromRequest(requestToLoad);

        // Populate body
        populateBody(requestToLoad);

        // Populate headers if available
        if (
            requestToLoad.getHeader() != null &&
            !requestToLoad.getHeader().isEmpty()
        ) {
            StringBuilder headersBuilder = new StringBuilder();
            for (Header header : requestToLoad.getHeader()) {
                // Assuming header format is [key, value, description]
                if (header.getKey() != null && header.getValue() != null) {
                    headersBuilder
                        .append(header.getKey())
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
        if (
            requestToLoad.getUrl() != null &&
            requestToLoad.getUrl().getQuery() != null
        ) {
            StringBuilder paramsBuilder = new StringBuilder();
            for (Query queryParam : requestToLoad.getUrl().getQuery()) {
                // Assuming query param format is [key, value, description]
                if (
                    queryParam.getKey() != null && queryParam.getValue() != null
                ) {
                    paramsBuilder
                        .append(queryParam.getKey())
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

        // Populate scripts from DB (item-level first, then collection-level fallback)
        populateScripts(itemId);

        // Reset flag after population is complete
        isPopulating = false;
    }

    private void populateScripts(int itemId) {
        scriptLoader.loadScripts(itemId, scriptsPanel);
    }

    private void populateBody(Request request) {
        if (
            request != null &&
            request.getBody() != null &&
            request.getBody().getRaw() != null
        ) {
            bodyTextArea.setText(request.getBody().getRaw());
        } else {
            bodyTextArea.setText("");
        }
    }

    /**
     * Clears unsaved changes for the current item (called after successful save)
     */
    public void clearUnsavedChanges() {
        if (currentItemId > 0) {
            unsavedChanges.remove(currentItemId);
        }
    }

    public HeadersPanel getHeadersPanel() {
        return headersPanel;
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

    public JButton getSaveButton() {
        return saveButton;
    }

    public JPanel getPanel() {
        return panel;
    }

    public ScriptsPanel getScriptsPanel() {
        return scriptsPanel;
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

        // Build Auth (delegated to AuthPanel)
        Auth auth = authPanel.buildAuth();
        if (auth != null) {
            request.setAuth(auth);
        }

        return request;
    }

    /**
     * Sets the callback to be executed when Save button is clicked
     */
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }
}

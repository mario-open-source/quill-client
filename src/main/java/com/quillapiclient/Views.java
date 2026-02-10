package com.quillapiclient;

import com.quillapiclient.controller.ApiController;
import com.quillapiclient.controller.CollectionTreeManager;
import com.quillapiclient.controller.EnvironmentListManager;
import com.quillapiclient.components.MainWindow;
import com.quillapiclient.components.RequestPanel;
import com.quillapiclient.components.LeftPanel;
import com.quillapiclient.components.ResponsePanel;
import com.quillapiclient.components.EnvironmentVariablesWindow;
import com.quillapiclient.utility.FileSelectionListener;
import com.quillapiclient.utility.OpenFileAction;
import com.quillapiclient.objects.Request;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.utility.ResponseFormatter;

import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class Views {
    private MainWindow mainWindow;
    private LeftPanel leftPanelComponent;
    private RequestPanel requestPanel;
    private CollectionTreeManager collectionManager;
    private EnvironmentListManager environmentManager;
    private ApiController apiController;
    private ResponsePanel responsePanel;
    private int environmentContextIndex = -1;
    private boolean environmentPopupInteraction = false;
    private int currentItemId = -1; // Track the currently selected item ID
    
    public Views() {
        mainWindow = new MainWindow();
        responsePanel = new ResponsePanel();
        apiController = new ApiController(responsePanel);
        collectionManager = new CollectionTreeManager();
        environmentManager = new EnvironmentListManager();
        requestPanel = new RequestPanel();
        
        setupComponents();
        
        // Load all collections from database on startup
        collectionManager.loadAllCollections();
        environmentManager.loadAllEnvironments();
        updateActiveEnvironmentIndicator();
    }
    
    private void setupComponents() {
        // Setup left panel with tree
        FileSelectionListener.RequestSelectionCallback requestCallback = 
            this::handleRequestSelection;
        
        FileSelectionListener fileSelectionListener = new FileSelectionListener(
            collectionManager.getTree(), 
            requestCallback
        );
        
        // Also track the item ID when a request is selected
        fileSelectionListener.setItemIdCallback(this::setCurrentItemId);
        
        OpenFileAction.FileChooserCallback importCallback = this::handleImportFile;
        
        OpenFileAction importAction = new OpenFileAction(importCallback);
        
        leftPanelComponent = new LeftPanel(
            collectionManager.getTree(), 
            environmentManager.getList(),
            fileSelectionListener, 
            importAction, 
            e -> System.out.println("New collection button clicked"),
            e -> collectionManager.createCollectionAndStartEditing(),
            e -> environmentManager.createEnvironmentAndStartEditing()
        );

        setupEnvironmentContextMenu();

        environmentManager.getList().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            if (environmentPopupInteraction) {
                environmentPopupInteraction = false;
                return;
            }
            int selectedIndex = environmentManager.getList().getSelectedIndex();
            EnvironmentDao.EnvironmentInfo info = environmentManager.getEnvironmentInfoAt(selectedIndex);
            if (info != null) {
                new EnvironmentVariablesWindow(info.id, info.name);
                // Allow reopening the same environment by making next click a fresh selection change.
                SwingUtilities.invokeLater(() -> environmentManager.getList().clearSelection());
            }
        });
        
        // Connect send button to API controller
        requestPanel.getSendButton().addActionListener(e -> executeApiCall());
        
        // Connect save button to save handler
        requestPanel.setSaveCallback(() -> saveRequest());
        
        // Set up main window layout
        mainWindow.setLayout(
            leftPanelComponent,
            requestPanel,
            responsePanel.getPanel()
        );
    }

    private void handleImportFile(File file) {
        if (file == null) {
            return;
        }

        String filename = file.getName().toLowerCase();
        if (filename.contains("environment")) {
            environmentManager.loadEnvironmentFile(file);
            updateActiveEnvironmentIndicator();
        } else if (filename.contains("collection")) {
            collectionManager.loadCollectionFile(file);
        } else {
            JOptionPane.showMessageDialog(null,
                "Import failed: filename must include \"environment\" or \"collection\".",
                "Invalid Import",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void handleRequestSelection(Request request) {
        requestPanel.populateFromRequest(request, currentItemId);
        // Load and display the response for this request
        loadAndDisplayResponse();
    }
    
    private void setCurrentItemId(int itemId) {
        this.currentItemId = itemId;
    }
    
    /**
     * Loads and displays the response for the currently selected request.
     * Shows "There is no response for this request" if no response exists.
     */
    private void loadAndDisplayResponse() {
        if (currentItemId <= 0) {
            responsePanel.setResponse(ResponseFormatter.NO_RESPONSE_MESSAGE);
            responsePanel.setErrorState(false);
            responsePanel.resetStatusDurationSize();
            return;
        }
        
        // Get the request ID from the item ID
        int requestId = CollectionDao.getRequestIdByItemId(currentItemId);
        if (requestId <= 0) {
            responsePanel.setResponse(ResponseFormatter.NO_RESPONSE_MESSAGE);
            responsePanel.setErrorState(false);
            responsePanel.resetStatusDurationSize();
            return;
        }
        
        // Get the latest response for this request
        ApiResponse response = CollectionDao.getLatestResponseByRequestId(requestId);
        
        if (response == null) {
            responsePanel.setResponse(ResponseFormatter.NO_RESPONSE_MESSAGE);
            responsePanel.setErrorState(false);
            responsePanel.resetStatusDurationSize();
        } else {
            // Format and display the response using the unified formatter
            String formattedResponse = ResponseFormatter.formatResponse(response, "Response");
            responsePanel.setResponse(formattedResponse);
            
            // Update status and duration labels from the response
            responsePanel.setStatus(response.getStatusCode());
            responsePanel.setDuration(response.getDuration());
            responsePanel.setSize(ResponseFormatter.formatSize(response.getBody().length()));
            
            responsePanel.setErrorState(!response.isSuccess());
        }
    }
    
    private void saveRequest() {
        if (currentItemId <= 0) {
            System.out.println("No request selected to save");
            return;
        }

        requestPanel.getSaveButton().setEnabled(false);
        
        // Build Request object from UI
        Request request = requestPanel.buildRequestFromUI();
        
        // Update in database
        boolean success = CollectionDao.updateRequest(currentItemId, request);
        
        if (success) {
            System.out.println("Request saved successfully");
            collectionManager.updateRequestNodeMethod(currentItemId, request.getMethod());
            // Clear unsaved changes and reload from database
            requestPanel.clearUnsavedChanges();
            Request updatedRequest = CollectionDao.getRequestByItemId(currentItemId);
            if (updatedRequest != null) {
                requestPanel.populateFromRequest(updatedRequest, currentItemId);
            }
        } else {
            System.err.println("Failed to save request");
        }
        requestPanel.getSaveButton().setEnabled(true);
    }
    
    private void executeApiCall() {
        // Gather all request data from the RequestPanel
        String url = requestPanel.getUrl();
        String method = requestPanel.getMethod();
        String headersText = requestPanel.getHeaders();
        String bodyText = requestPanel.getBody();
        String paramsText = requestPanel.getParams();
        
        // Get auth data from AuthPanel
        String authType = requestPanel.getAuthPanel().getAuthType();
        String username = requestPanel.getAuthPanel().getUsername();
        String password = requestPanel.getAuthPanel().getPassword();
        String token = requestPanel.getAuthPanel().getToken();
        
        // Execute the API call through the controller, passing the current item ID
        apiController.executeApiCall(url, method, headersText, bodyText, 
                                   authType, username, password, token, paramsText, currentItemId,
                                   environmentManager.getActiveEnvironmentVariables());
    }
    
    public void show() {
        mainWindow.getFrame().setVisible(true);
    }

    private void updateActiveEnvironmentIndicator() {
        if (leftPanelComponent != null) {
            leftPanelComponent.setActiveEnvironmentName(environmentManager.getActiveEnvironmentName());
        }
    }

    private void setupEnvironmentContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                environmentPopupInteraction = false;
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                environmentPopupInteraction = false;
            }
        });
        JMenuItem activateItem = new JMenuItem("Activate environment");
        activateItem.addActionListener(e -> activateEnvironmentFromContext());
        popupMenu.add(activateItem);

        environmentManager.getList().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowEnvironmentPopup(e, popupMenu, activateItem);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowEnvironmentPopup(e, popupMenu, activateItem);
            }
        });
    }

    private void maybeShowEnvironmentPopup(MouseEvent event, JPopupMenu popupMenu, JMenuItem activateItem) {
        if (!event.isPopupTrigger()) {
            return;
        }

        int index = environmentManager.getList().locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }

        Rectangle cellBounds = environmentManager.getList().getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(event.getPoint())) {
            return;
        }

        environmentPopupInteraction = true;
        environmentContextIndex = index;
        EnvironmentDao.EnvironmentInfo info = environmentManager.getEnvironmentInfoAt(index);
        boolean isActive = info != null && info.id == environmentManager.getActiveEnvironmentId();
        activateItem.setEnabled(!isActive);
        activateItem.setText(isActive ? "Environment Active" : "Activate environment");
        popupMenu.show(environmentManager.getList(), event.getX(), event.getY());
    }

    private void activateEnvironmentFromContext() {
        if (environmentContextIndex < 0) {
            return;
        }
        environmentManager.setActiveEnvironmentByIndex(environmentContextIndex);
        updateActiveEnvironmentIndicator();
    }
}

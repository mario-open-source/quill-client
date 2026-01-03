package com.quillapiclient;

import com.quillapiclient.controller.ApiController;
import com.quillapiclient.controller.CollectionTreeManager;
import com.quillapiclient.components.MainWindow;
import com.quillapiclient.components.RequestPanel;
import com.quillapiclient.components.LeftPanel;
import com.quillapiclient.components.ResponsePanel;
import com.quillapiclient.utility.FileSelectionListener;
import com.quillapiclient.utility.OpenFileAction;
import com.quillapiclient.objects.Request;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.utility.ResponseFormatter;

public class Views {
    private MainWindow mainWindow;
    private RequestPanel requestPanel;
    private CollectionTreeManager collectionManager;
    private ApiController apiController;
    private ResponsePanel responsePanel;
    private int currentItemId = -1; // Track the currently selected item ID
    
    public Views() {
        mainWindow = new MainWindow();
        responsePanel = new ResponsePanel();
        apiController = new ApiController(responsePanel);
        collectionManager = new CollectionTreeManager();
        requestPanel = new RequestPanel();
        
        setupComponents();
        
        // Load all collections from database on startup
        collectionManager.loadAllCollections();
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
        
        OpenFileAction.FileChooserCallback importCallback =
            collectionManager::loadCollectionFile;
        
        OpenFileAction importAction = new OpenFileAction(importCallback);
        
        LeftPanel leftPanelComponent = new LeftPanel(
            collectionManager.getTree(), 
            fileSelectionListener, 
            importAction, 
            e -> System.out.println("New collection button clicked")
        );
        
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
                                   authType, username, password, token, paramsText, currentItemId);
    }
    
    public void show() {
        mainWindow.getFrame().setVisible(true);
    }
}
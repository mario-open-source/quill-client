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

public class Views {
    private MainWindow mainWindow;
    private RequestPanel requestPanel;
    private CollectionTreeManager collectionManager;
    private ApiController apiController;
    private ResponsePanel responsePanel;
    
    public Views() {
        mainWindow = new MainWindow();
        responsePanel = new ResponsePanel();
        apiController = new ApiController(responsePanel);
        collectionManager = new CollectionTreeManager();
        requestPanel = new RequestPanel();
        
        setupComponents();
    }
    
    private void setupComponents() {
        // Setup left panel with tree
        FileSelectionListener.RequestSelectionCallback requestCallback = 
            this::handleRequestSelection;
        
        FileSelectionListener fileSelectionListener = new FileSelectionListener(
            collectionManager.getTree(), 
            requestCallback
        );
        
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
        
        // Set up main window layout
        mainWindow.setLayout(
            leftPanelComponent.getPanel(),
            requestPanel.getPanel(),
            responsePanel.getPanel()
        );
    }
    
    private void handleRequestSelection(Request request) {
        requestPanel.populateFromRequest(request);
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
        
        // Execute the API call through the controller
        apiController.executeApiCall(url, method, headersText, bodyText, 
                                   authType, username, password, token, paramsText);
    }
    
    public void show() {
        mainWindow.getFrame().setVisible(true);
    }
}
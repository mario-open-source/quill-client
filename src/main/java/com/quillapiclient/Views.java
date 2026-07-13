package com.quillapiclient;

import com.quillapiclient.components.LeftPanel;
import com.quillapiclient.components.MainWindow;
import com.quillapiclient.components.RequestPanel;
import com.quillapiclient.components.ResponsePanel;
import com.quillapiclient.controller.ApiController;
import com.quillapiclient.controller.CollectionTreeManager;
import com.quillapiclient.controller.EnvironmentListManager;
import com.quillapiclient.controller.RequestController;
import com.quillapiclient.db.LiteConnection;
import com.quillapiclient.objects.ExecutionRequest;
import com.quillapiclient.objects.Request;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.utility.OpenFileAction;
import com.quillapiclient.utility.ResponseFormatter;
import com.quillapiclient.utility.TableEditUtil;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class Views {

    private MainWindow mainWindow;
    private LeftPanel leftPanelComponent;
    private RequestPanel requestPanel;
    private CollectionTreeManager collectionManager;
    private EnvironmentListManager environmentManager;
    private ApiController apiController;
    private RequestController requestController;
    private ResponsePanel responsePanel;
    private int currentItemId = -1; // Track the currently selected item ID from tree selection

    public Views() {
        mainWindow = new MainWindow();
        responsePanel = new ResponsePanel();
        requestController = new RequestController();
        apiController = new ApiController(responsePanel, requestController);
        collectionManager = new CollectionTreeManager(requestController);
        environmentManager = new EnvironmentListManager();
        requestPanel = new RequestPanel();

        setupComponents();

        // Load all collections and environments from database on a background
        // thread to avoid blocking the EDT during startup. Dedicated connection
        // so startup queries never share the EDT singleton Connection object.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                LiteConnection.withNewConnection(conn -> {
                    collectionManager.loadAllCollections();
                    environmentManager.loadAllEnvironments();
                });
                return null;
            }

            @Override
            protected void done() {
                updateActiveEnvironmentIndicator();
            }
        }.execute();
    }

    private void setupComponents() {
        collectionManager.addTreeSelectionHandler(event ->
            TableEditUtil.commitOrCancelTableEdit(
                requestPanel.getHeadersPanel().getTable()
            )
        );
        collectionManager.addRequestItemIdSelectionListener(itemId -> {
            currentItemId = itemId;
        });
        collectionManager.addRequestSelectionListener(
            this::handleRequestSelection
        );

        OpenFileAction.FileChooserCallback importCallback =
            this::handleImportFile;

        OpenFileAction importAction = new OpenFileAction(importCallback);

        leftPanelComponent = new LeftPanel(
            collectionManager.getTree(),
            environmentManager.getList(),
            importAction,
            e -> collectionManager.createCollectionAndStartEditing(),
            e -> environmentManager.createEnvironmentAndStartEditing()
        );

        // Delegate environment context menu setup to EnvironmentListManager
        environmentManager.setupContextMenu();
        environmentManager.setOnActiveEnvironmentChanged(
            this::updateActiveEnvironmentIndicator
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
            JOptionPane.showMessageDialog(
                null,
                "Import failed: filename must include \"environment\" or \"collection\".",
                "Invalid Import",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void handleRequestSelection(Request request) {
        requestPanel.populateFromRequest(request, currentItemId);
        // Load and display the response for this request
        loadAndDisplayResponse();
    }

    /** Returns the currently selected item ID from the tree selection. */
    private int currentItemId() {
        return currentItemId;
    }

    /**
     * Loads and displays the response for the currently selected request.
     * Shows "There is no response for this request" if no response exists.
     */
    private void loadAndDisplayResponse() {
        // Delegate data access to the controller
        ApiResponse response = apiController.loadResponseForItem(
            currentItemId()
        );

        if (response == null) {
            responsePanel.setResponse(ResponseFormatter.NO_RESPONSE_MESSAGE);
            responsePanel.setErrorState(false);
            responsePanel.resetStatusDurationSize();
        } else {
            String formattedResponse = ResponseFormatter.formatResponse(
                response,
                "Response"
            );
            responsePanel.setResponse(formattedResponse);

            responsePanel.setStatus(response.getStatusCode());
            responsePanel.setDuration(response.getDuration());
            responsePanel.setSize(
                ResponseFormatter.formatSize(response.getBody().length())
            );
            responsePanel.setErrorState(!response.isSuccess());
        }
    }

    private void saveRequest() {
        int itemId = currentItemId();
        if (itemId <= 0) {
            System.out.println("No request selected to save");
            return;
        }

        requestPanel.getSaveButton().setEnabled(false);

        // Build Request object from UI
        Request request = requestPanel.buildRequestFromUI();

        // Delegate persistence to the controller
        boolean success = requestController.updateRequest(itemId, request);

        if (success) {
            System.out.println("Request saved successfully");
            collectionManager.updateRequestNodeMethod(
                itemId,
                request.getMethod()
            );

            // Save scripts (item-level if set, otherwise clear item-level so collection-level shows)
            requestController.saveScriptsForItem(
                itemId,
                requestPanel.getScriptsPanel().getPreRequestScript(),
                requestPanel.getScriptsPanel().getTestScript()
            );

            // Clear unsaved changes and reload from database via controller
            requestPanel.clearUnsavedChanges();
            Request updatedRequest = requestController.getRequestByItemId(
                itemId
            );
            if (updatedRequest != null) {
                requestPanel.populateFromRequest(updatedRequest, itemId);
            }
        } else {
            System.err.println("Failed to save request");
        }
        requestPanel.getSaveButton().setEnabled(true);
    }

    private void executeApiCall() {
        // Delegate parameter extraction to RequestPanel via the ExecutionRequest DTO
        ExecutionRequest exec = requestPanel.buildExecutionRequest();
        int environmentId = environmentManager.getActiveEnvironmentId();

        apiController.executeApiCall(
            exec.url,
            exec.method,
            exec.headersText,
            exec.bodyText,
            exec.authType,
            exec.username,
            exec.password,
            exec.token,
            exec.paramsText,
            currentItemId(),
            environmentId
        );
    }

    public void show() {
        mainWindow.getFrame().setVisible(true);
    }

    private void updateActiveEnvironmentIndicator() {
        if (leftPanelComponent != null) {
            leftPanelComponent.setActiveEnvironmentName(
                environmentManager.getActiveEnvironmentName()
            );
        }
    }
}

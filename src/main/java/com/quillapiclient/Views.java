package com.quillapiclient;

import com.quillapiclient.components.EnvironmentVariablesWindow;
import com.quillapiclient.components.LeftPanel;
import com.quillapiclient.components.MainWindow;
import com.quillapiclient.components.RequestPanel;
import com.quillapiclient.components.ResponsePanel;
import com.quillapiclient.components.ScriptsPanel;
import com.quillapiclient.controller.ApiController;
import com.quillapiclient.controller.CollectionTreeManager;
import com.quillapiclient.controller.EnvironmentListManager;
import com.quillapiclient.controller.RequestController;
import com.quillapiclient.objects.Request;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.utility.OpenFileAction;
import com.quillapiclient.utility.ResponseFormatter;
import com.quillapiclient.utility.TableEditUtil;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class Views {

    private MainWindow mainWindow;
    private LeftPanel leftPanelComponent;
    private RequestPanel requestPanel;
    private CollectionTreeManager collectionManager;
    private EnvironmentListManager environmentManager;
    private ApiController apiController;
    private RequestController requestController;
    private ResponsePanel responsePanel;
    private int environmentContextIndex = -1;
    private int currentItemId = -1; // Track the currently selected item ID

    public Views() {
        mainWindow = new MainWindow();
        responsePanel = new ResponsePanel();
        apiController = new ApiController(responsePanel, requestController);
        requestController = new RequestController();
        collectionManager = new CollectionTreeManager(requestController);
        environmentManager = new EnvironmentListManager();
        requestPanel = new RequestPanel();

        setupComponents();

        // Load all collections and environments from database on a background
        // thread to avoid blocking the EDT during startup.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                collectionManager.loadAllCollections();
                environmentManager.loadAllEnvironments();
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
        collectionManager.addRequestItemIdSelectionListener(
            this::setCurrentItemId
        );
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

        setupEnvironmentContextMenu();

        // Open environment variables window on double-click
        environmentManager.getList().addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                        int index = environmentManager
                            .getList()
                            .locationToIndex(e.getPoint());
                        if (index < 0) {
                            return;
                        }
                        Rectangle cellBounds = environmentManager
                            .getList()
                            .getCellBounds(index, index);
                        if (
                            cellBounds == null ||
                            !cellBounds.contains(e.getPoint())
                        ) {
                            return;
                        }
                        EnvironmentListManager.EnvironmentInfo info =
                            environmentManager.getEnvironmentInfoAt(index);
                        if (info != null) {
                            new EnvironmentVariablesWindow(
                                info.id,
                                info.name,
                                environmentManager
                            );
                        }
                    }
                }
            }
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

    private void setCurrentItemId(int itemId) {
        this.currentItemId = itemId;
    }

    /**
     * Loads and displays the response for the currently selected request.
     * Shows "There is no response for this request" if no response exists.
     */
    private void loadAndDisplayResponse() {
        // Delegate data access to the controller
        ApiResponse response = apiController.loadResponseForItem(currentItemId);

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
        if (currentItemId <= 0) {
            System.out.println("No request selected to save");
            return;
        }

        requestPanel.getSaveButton().setEnabled(false);

        // Build Request object from UI
        Request request = requestPanel.buildRequestFromUI();

        // Delegate persistence to the controller
        boolean success = requestController.updateRequest(
            currentItemId,
            request
        );

        if (success) {
            System.out.println("Request saved successfully");
            collectionManager.updateRequestNodeMethod(
                currentItemId,
                request.getMethod()
            );

            // Save scripts (item-level if set, otherwise clear item-level so collection-level shows)
            saveScriptsForCurrentItem();

            // Clear unsaved changes and reload from database via controller
            requestPanel.clearUnsavedChanges();
            Request updatedRequest = requestController.getRequestByItemId(
                currentItemId
            );
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

        int environmentId = environmentManager.getActiveEnvironmentId();

        // Execute the API call through the controller, passing the current item ID
        apiController.executeApiCall(
            url,
            method,
            headersText,
            bodyText,
            authType,
            username,
            password,
            token,
            paramsText,
            currentItemId,
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

    private void saveScriptsForCurrentItem() {
        if (currentItemId <= 0) return;

        ScriptsPanel scriptsPanel = requestPanel.getScriptsPanel();
        if (scriptsPanel == null) return;

        requestController.saveScriptsForItem(
            currentItemId,
            scriptsPanel.getPreRequestScript(),
            scriptsPanel.getTestScript()
        );
    }

    private void setupEnvironmentContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.addPopupMenuListener(
            new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {}
            }
        );
        JMenuItem activateItem = new JMenuItem("Activate environment");
        activateItem.addActionListener(e -> activateEnvironmentFromContext());
        popupMenu.add(activateItem);

        JMenuItem deleteItem = new JMenuItem("Delete environment");
        deleteItem.addActionListener(e -> deleteEnvironmentFromContext());
        popupMenu.add(deleteItem);

        environmentManager.getList().addMouseListener(
            new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowEnvironmentPopup(e, popupMenu, activateItem);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowEnvironmentPopup(e, popupMenu, activateItem);
                }
            }
        );
    }

    private void maybeShowEnvironmentPopup(
        MouseEvent event,
        JPopupMenu popupMenu,
        JMenuItem activateItem
    ) {
        if (!event.isPopupTrigger()) {
            return;
        }

        int index = environmentManager
            .getList()
            .locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }

        Rectangle cellBounds = environmentManager
            .getList()
            .getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(event.getPoint())) {
            return;
        }

        environmentContextIndex = index;
        EnvironmentListManager.EnvironmentInfo info =
            environmentManager.getEnvironmentInfoAt(index);
        boolean isActive =
            info != null &&
            info.id == environmentManager.getActiveEnvironmentId();
        activateItem.setEnabled(!isActive);
        activateItem.setText(
            isActive ? "Environment Active" : "Activate environment"
        );
        popupMenu.show(
            environmentManager.getList(),
            event.getX(),
            event.getY()
        );
    }

    private void activateEnvironmentFromContext() {
        if (environmentContextIndex < 0) {
            return;
        }
        environmentManager.setActiveEnvironmentByIndex(environmentContextIndex);
        updateActiveEnvironmentIndicator();
    }

    private void deleteEnvironmentFromContext() {
        if (environmentContextIndex < 0) {
            return;
        }
        EnvironmentListManager.EnvironmentInfo info =
            environmentManager.getEnvironmentInfoAt(environmentContextIndex);
        if (info == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
            null,
            "Delete environment \"" + info.name + "\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        environmentManager.deleteEnvironment(environmentContextIndex);
        updateActiveEnvironmentIndicator();
    }
}

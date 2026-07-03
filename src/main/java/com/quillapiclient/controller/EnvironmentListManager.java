package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.components.EnvironmentVariablesWindow;
import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironment;
import com.quillapiclient.objects.PostmanEnvironmentValue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.DefaultListModel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class EnvironmentListManager {

    /**
     * Lightweight DTO for environment list entries. Owned by the controller layer
     * so that views never need to import the DAO package.
     */
    public static class EnvironmentInfo {

        public final int id;
        public final String name;

        public EnvironmentInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * Lightweight DTO for an environment variable row in the editor table.
     * Owned by the controller layer so that view components never import the DAO package.
     */
    public static class EnvironmentValueRecord {

        public final int id;
        public final PostmanEnvironmentValue value;

        public EnvironmentValueRecord(int id, PostmanEnvironmentValue value) {
            this.id = id;
            this.value = value;
        }
    }

    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private final java.util.List<EnvironmentInfo> environmentInfos;
    private Integer activeEnvironmentId;
    private int contextMenuIndex = -1;
    private Runnable onActiveEnvironmentChanged;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DEFAULT_NEW_ENVIRONMENT_NAME =
        "New Environment";

    public EnvironmentListManager() {
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        environmentInfos = new java.util.ArrayList<>();
        activeEnvironmentId = null;
        setupDoubleClickToOpenVariables();
    }

    /**
     * Opens the {@link EnvironmentVariablesWindow} on double-click of an environment list entry.
     */
    private void setupDoubleClickToOpenVariables() {
        list.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                        int index = list.locationToIndex(e.getPoint());
                        if (index < 0) {
                            return;
                        }
                        Rectangle cellBounds = list.getCellBounds(index, index);
                        if (
                            cellBounds == null ||
                            !cellBounds.contains(e.getPoint())
                        ) {
                            return;
                        }
                        EnvironmentInfo info = getEnvironmentInfoAt(index);
                        if (info != null) {
                            new EnvironmentVariablesWindow(
                                info.id,
                                info.name,
                                EnvironmentListManager.this
                            );
                        }
                    }
                }
            }
        );
    }

    public void loadEnvironmentFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            PostmanEnvironment environment = objectMapper.readValue(
                file,
                PostmanEnvironment.class
            );
            int environmentId = EnvironmentDao.saveEnvironment(
                environment,
                file.getName()
            );
            if (environmentId > 0) {
                loadAllEnvironments();
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                null,
                "Error loading environment: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public void loadAllEnvironments() {
        Integer previousActiveEnvironmentId = activeEnvironmentId;

        // Run the DB query on the calling thread (may be background)
        java.util.List<EnvironmentDao.EnvironmentInfo> daoInfos =
            EnvironmentDao.getAllEnvironments();

        // Map DAO type → controller type at the boundary
        java.util.List<EnvironmentInfo> mapped = new java.util.ArrayList<>();
        for (EnvironmentDao.EnvironmentInfo daoInfo : daoInfos) {
            mapped.add(new EnvironmentInfo(daoInfo.id, daoInfo.name));
        }

        // Update Swing model on the EDT
        SwingUtilities.invokeLater(() -> {
            boolean activeEnvironmentStillExists = false;
            listModel.clear();
            environmentInfos.clear();
            for (EnvironmentInfo info : mapped) {
                listModel.addElement(info.name);
                environmentInfos.add(info);
                if (
                    previousActiveEnvironmentId != null &&
                    info.id == previousActiveEnvironmentId
                ) {
                    activeEnvironmentStillExists = true;
                }
            }
            if (!activeEnvironmentStillExists) {
                activeEnvironmentId = null;
            }
        });
    }

    public JList<String> getList() {
        return list;
    }

    public EnvironmentInfo getEnvironmentInfoAt(int index) {
        if (index < 0 || index >= environmentInfos.size()) {
            return null;
        }
        return environmentInfos.get(index);
    }

    public void setActiveEnvironmentByIndex(int index) {
        EnvironmentInfo info = getEnvironmentInfoAt(index);
        activeEnvironmentId = info != null ? info.id : null;
        notifyActiveEnvironmentChanged();
    }

    /**
     * Registers a callback that fires after the active environment changes
     * (via setActiveEnvironmentByIndex, activate, or delete).
     */
    public void setOnActiveEnvironmentChanged(Runnable callback) {
        this.onActiveEnvironmentChanged = callback;
    }

    private void notifyActiveEnvironmentChanged() {
        if (onActiveEnvironmentChanged != null) {
            onActiveEnvironmentChanged.run();
        }
    }

    /**
     * Sets up the right-click context menu on the environment list.
     * Moved from Views.java to keep environment UI logic co-located with
     * the list it owns.
     */
    public void setupContextMenu() {
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

        list.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowPopup(e, popupMenu, activateItem);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowPopup(e, popupMenu, activateItem);
                }
            }
        );
    }

    private void maybeShowPopup(
        MouseEvent event,
        JPopupMenu popupMenu,
        JMenuItem activateItem
    ) {
        if (!event.isPopupTrigger()) {
            return;
        }

        int index = list.locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }

        Rectangle cellBounds = list.getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(event.getPoint())) {
            return;
        }

        contextMenuIndex = index;
        EnvironmentInfo info = getEnvironmentInfoAt(index);
        boolean isActive =
            info != null && info.id == getActiveEnvironmentIdAsInt();
        activateItem.setEnabled(!isActive);
        activateItem.setText(
            isActive ? "Environment Active" : "Activate environment"
        );
        popupMenu.show(list, event.getX(), event.getY());
    }

    private void activateEnvironmentFromContext() {
        if (contextMenuIndex < 0) {
            return;
        }
        setActiveEnvironmentByIndex(contextMenuIndex);
    }

    private void deleteEnvironmentFromContext() {
        if (contextMenuIndex < 0) {
            return;
        }
        EnvironmentInfo info = getEnvironmentInfoAt(contextMenuIndex);
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
        deleteEnvironment(contextMenuIndex);
        notifyActiveEnvironmentChanged();
    }

    private int getActiveEnvironmentIdAsInt() {
        return activeEnvironmentId != null ? activeEnvironmentId : -1;
    }

    public boolean deleteEnvironment(int index) {
        EnvironmentInfo info = getEnvironmentInfoAt(index);
        if (info == null) {
            return false;
        }

        boolean deleted = EnvironmentDao.deleteEnvironment(info.id);
        if (deleted) {
            if (activeEnvironmentId != null && activeEnvironmentId == info.id) {
                activeEnvironmentId = null;
            }
            listModel.remove(index);
            environmentInfos.remove(index);
            notifyActiveEnvironmentChanged();
        }
        return deleted;
    }

    public void createEnvironmentAndStartEditing() {
        int environmentId = EnvironmentDao.createEnvironment(
            DEFAULT_NEW_ENVIRONMENT_NAME
        );
        if (environmentId <= 0) {
            JOptionPane.showMessageDialog(
                null,
                "Failed to create new environment",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        EnvironmentInfo newInfo = new EnvironmentInfo(
            environmentId,
            DEFAULT_NEW_ENVIRONMENT_NAME
        );
        listModel.add(0, DEFAULT_NEW_ENVIRONMENT_NAME);
        environmentInfos.add(0, newInfo);
        list.ensureIndexIsVisible(0);
        startInlineEdit(0, newInfo);
    }

    public int getActiveEnvironmentId() {
        return activeEnvironmentId != null ? activeEnvironmentId : -1;
    }

    public String getActiveEnvironmentName() {
        if (activeEnvironmentId == null || activeEnvironmentId <= 0) {
            return null;
        }
        for (EnvironmentInfo info : environmentInfos) {
            if (info.id == activeEnvironmentId) {
                return info.name;
            }
        }
        return null;
    }

    public Map<String, String> getActiveEnvironmentVariables() {
        if (activeEnvironmentId == null || activeEnvironmentId <= 0) {
            return new HashMap<>();
        }

        Map<String, String> environmentVariables = new HashMap<>();
        for (PostmanEnvironmentValue value : EnvironmentDao.getEnvironmentValues(
            activeEnvironmentId
        )) {
            if (
                value == null ||
                value.getKey() == null ||
                value.getKey().trim().isEmpty()
            ) {
                continue;
            }
            // Treat null as enabled (Postman behavior for missing enabled flag).
            if (Boolean.FALSE.equals(value.getEnabled())) {
                continue;
            }
            environmentVariables.put(
                value.getKey().trim(),
                value.getValue() != null ? value.getValue() : ""
            );
        }
        return environmentVariables;
    }

    /**
     * Loads all persisted environment variable rows for the given environment.
     */
    public List<EnvironmentValueRecord> getEnvironmentValueRecords(
        int environmentId
    ) {
        List<EnvironmentDao.EnvironmentValueRecord> daoRecords =
            EnvironmentDao.getEnvironmentValueRecords(environmentId);
        List<EnvironmentValueRecord> result = new ArrayList<>();
        for (EnvironmentDao.EnvironmentValueRecord daoRecord : daoRecords) {
            result.add(
                new EnvironmentValueRecord(daoRecord.id, daoRecord.value)
            );
        }
        return result;
    }

    /**
     * Atomically replaces all environment variable values for a given environment.
     */
    public boolean replaceEnvironmentValues(
        int environmentId,
        List<PostmanEnvironmentValue> values
    ) {
        return EnvironmentDao.replaceEnvironmentValues(environmentId, values);
    }

    /**
     * Deletes specific environment variable rows by their record IDs.
     */
    public boolean deleteEnvironmentValuesByIds(
        int environmentId,
        List<Integer> valueIds
    ) {
        return EnvironmentDao.deleteEnvironmentValuesByIds(
            environmentId,
            valueIds
        );
    }

    private void startInlineEdit(int index, EnvironmentInfo info) {
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (cellBounds == null) {
            return;
        }

        JRootPane rootPane = SwingUtilities.getRootPane(list);
        if (rootPane == null) {
            return;
        }

        JTextField editor = new JTextField(info.name);
        editor.setBorder(
            javax.swing.BorderFactory.createLineBorder(
                new java.awt.Color(120, 120, 120)
            )
        );
        editor.setFocusTraversalKeysEnabled(false);

        Point location = SwingUtilities.convertPoint(
            list,
            cellBounds.x,
            cellBounds.y,
            rootPane.getLayeredPane()
        );
        editor.setBounds(
            location.x,
            location.y,
            Math.max(cellBounds.width, 180),
            cellBounds.height
        );
        rootPane.getLayeredPane().add(editor, JLayeredPane.POPUP_LAYER);
        rootPane.getLayeredPane().repaint();
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable cancelEdit = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            rootPane.getLayeredPane().remove(editor);
            rootPane.getLayeredPane().repaint();
        };

        Runnable commitEdit = () -> {
            if (finished.get()) {
                return;
            }
            String newName =
                editor.getText() != null ? editor.getText().trim() : "";
            if (newName.isEmpty()) {
                cancelEdit.run();
                return;
            }

            if (!newName.equals(info.name)) {
                boolean saved = EnvironmentDao.updateEnvironmentName(
                    info.id,
                    newName
                );
                if (saved) {
                    listModel.set(index, newName);
                    environmentInfos.set(
                        index,
                        new EnvironmentInfo(info.id, newName)
                    );
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to rename environment",
                        "Rename Failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
            cancelEdit.run();
        };

        editor.addActionListener(e -> commitEdit.run());
        editor
            .getInputMap()
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit");
        editor.getActionMap().put(
            "cancelEdit",
            new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    cancelEdit.run();
                }
            }
        );
        editor.addFocusListener(
            new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitEdit.run();
                }
            }
        );

        SwingUtilities.invokeLater(() -> {
            editor.requestFocusInWindow();
            editor.selectAll();
        });
    }
}

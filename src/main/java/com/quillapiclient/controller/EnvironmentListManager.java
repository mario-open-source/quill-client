package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironment;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnvironmentListManager {
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private final java.util.List<EnvironmentDao.EnvironmentInfo> environmentInfos;
    private Integer activeEnvironmentId;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DEFAULT_NEW_ENVIRONMENT_NAME = "New Environment";

    public EnvironmentListManager() {
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        environmentInfos = new java.util.ArrayList<>();
        activeEnvironmentId = null;
    }

    public void loadEnvironmentFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            PostmanEnvironment environment = objectMapper.readValue(file, PostmanEnvironment.class);
            int environmentId = EnvironmentDao.saveEnvironment(environment, file.getName());
            if (environmentId > 0) {
                loadAllEnvironments();
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                "Error loading environment: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadAllEnvironments() {
        Integer previousActiveEnvironmentId = activeEnvironmentId;
        boolean activeEnvironmentStillExists = false;
        listModel.clear();
        environmentInfos.clear();
        for (EnvironmentDao.EnvironmentInfo info : EnvironmentDao.getAllEnvironments()) {
            listModel.addElement(info.name);
            environmentInfos.add(info);
            if (previousActiveEnvironmentId != null && info.id == previousActiveEnvironmentId) {
                activeEnvironmentStillExists = true;
            }
        }
        if (!activeEnvironmentStillExists) {
            activeEnvironmentId = null;
        }
    }

    public JList<String> getList() {
        return list;
    }

    public EnvironmentDao.EnvironmentInfo getEnvironmentInfoAt(int index) {
        if (index < 0 || index >= environmentInfos.size()) {
            return null;
        }
        return environmentInfos.get(index);
    }

    public void setActiveEnvironmentByIndex(int index) {
        EnvironmentDao.EnvironmentInfo info = getEnvironmentInfoAt(index);
        activeEnvironmentId = (info != null) ? info.id : null;
    }

    public void createEnvironmentAndStartEditing() {
        int environmentId = EnvironmentDao.createEnvironment(DEFAULT_NEW_ENVIRONMENT_NAME);
        if (environmentId <= 0) {
            JOptionPane.showMessageDialog(
                null,
                "Failed to create new environment",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        EnvironmentDao.EnvironmentInfo newInfo = new EnvironmentDao.EnvironmentInfo(
            environmentId, DEFAULT_NEW_ENVIRONMENT_NAME
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
        for (EnvironmentDao.EnvironmentInfo info : environmentInfos) {
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
        for (com.quillapiclient.objects.PostmanEnvironmentValue value
                : EnvironmentDao.getEnvironmentValues(activeEnvironmentId)) {
            if (value == null || value.getKey() == null || value.getKey().trim().isEmpty()) {
                continue;
            }
            // Treat null as enabled (Postman behavior for missing enabled flag).
            if (Boolean.FALSE.equals(value.getEnabled())) {
                continue;
            }
            environmentVariables.put(value.getKey().trim(), value.getValue() != null ? value.getValue() : "");
        }
        return environmentVariables;
    }

    private void startInlineEdit(int index, EnvironmentDao.EnvironmentInfo info) {
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (cellBounds == null) {
            return;
        }

        JRootPane rootPane = SwingUtilities.getRootPane(list);
        if (rootPane == null) {
            return;
        }

        JTextField editor = new JTextField(info.name);
        editor.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(120, 120, 120)));
        editor.setFocusTraversalKeysEnabled(false);

        Point location = SwingUtilities.convertPoint(list, cellBounds.x, cellBounds.y, rootPane.getLayeredPane());
        editor.setBounds(location.x, location.y, Math.max(cellBounds.width, 180), cellBounds.height);
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
            String newName = editor.getText() != null ? editor.getText().trim() : "";
            if (newName.isEmpty()) {
                cancelEdit.run();
                return;
            }

            if (!newName.equals(info.name)) {
                boolean saved = EnvironmentDao.updateEnvironmentName(info.id, newName);
                if (saved) {
                    listModel.set(index, newName);
                    environmentInfos.set(index, new EnvironmentDao.EnvironmentInfo(info.id, newName));
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
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit");
        editor.getActionMap().put("cancelEdit", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelEdit.run();
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitEdit.run();
            }
        });

        SwingUtilities.invokeLater(() -> {
            editor.requestFocusInWindow();
            editor.selectAll();
        });
    }
}

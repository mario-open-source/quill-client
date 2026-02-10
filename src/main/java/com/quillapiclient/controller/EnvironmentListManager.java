package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironment;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class EnvironmentListManager {
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private final java.util.List<EnvironmentDao.EnvironmentInfo> environmentInfos;
    private Integer activeEnvironmentId;
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
}

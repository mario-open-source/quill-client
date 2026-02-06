package com.quillapiclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironment;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import java.io.File;

public class EnvironmentListManager {
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private final java.util.List<EnvironmentDao.EnvironmentInfo> environmentInfos;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public EnvironmentListManager() {
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        environmentInfos = new java.util.ArrayList<>();
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
        listModel.clear();
        environmentInfos.clear();
        for (EnvironmentDao.EnvironmentInfo info : EnvironmentDao.getAllEnvironments()) {
            listModel.addElement(info.name);
            environmentInfos.add(info);
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
}

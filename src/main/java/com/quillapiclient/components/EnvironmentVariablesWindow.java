package com.quillapiclient.components;

import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironmentValue;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentVariablesWindow {
    private final int environmentId;
    private final String environmentName;
    private final JFrame frame;
    private final EnvironmentValuesTableModel tableModel;

    public EnvironmentVariablesWindow(int environmentId, String environmentName) {
        this.environmentId = environmentId;
        this.environmentName = environmentName;
        this.frame = new JFrame("Environment Variables - " + environmentName);
        this.tableModel = new EnvironmentValuesTableModel(EnvironmentDao.getEnvironmentValues(environmentId));
        buildUi();
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel header = new JLabel("Environment: " + environmentName, SwingConstants.LEFT);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(header, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(500, 300));
        frame.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        JButton saveButton = new JButton("Save");

        addButton.addActionListener(event -> tableModel.addEmptyRow());
        saveButton.addActionListener(event -> saveValues());

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(addButton, BorderLayout.WEST);
        buttonPanel.add(saveButton, BorderLayout.EAST);
        buttonPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(buttonPanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void saveValues() {
        List<PostmanEnvironmentValue> values = tableModel.getValuesForSave();
        boolean success = EnvironmentDao.replaceEnvironmentValues(environmentId, values);
        if (success) {
            JOptionPane.showMessageDialog(frame, "Environment variables saved.", "Saved",
                JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Failed to save environment variables.", "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class EnvironmentValuesTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Key", "Value"};
        private final List<PostmanEnvironmentValue> values;

        private EnvironmentValuesTableModel(List<PostmanEnvironmentValue> initialValues) {
            if (initialValues == null) {
                this.values = new ArrayList<>();
            } else {
                this.values = new ArrayList<>(initialValues);
            }
        }

        @Override
        public int getRowCount() {
            return values.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PostmanEnvironmentValue value = values.get(rowIndex);
            return columnIndex == 0 ? value.getKey() : value.getValue();
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            PostmanEnvironmentValue value = values.get(rowIndex);
            if (columnIndex == 0) {
                value.setKey(aValue != null ? aValue.toString() : null);
            } else {
                value.setValue(aValue != null ? aValue.toString() : null);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        public void addEmptyRow() {
            values.add(new PostmanEnvironmentValue());
            int newRow = values.size() - 1;
            fireTableRowsInserted(newRow, newRow);
        }

        public List<PostmanEnvironmentValue> getValuesForSave() {
            List<PostmanEnvironmentValue> sanitized = new ArrayList<>();
            for (PostmanEnvironmentValue value : values) {
                String key = value.getKey() != null ? value.getKey().trim() : "";
                String variableValue = value.getValue();
                if (key.isEmpty()) {
                    continue;
                }
                PostmanEnvironmentValue copy = new PostmanEnvironmentValue();
                copy.setKey(key);
                copy.setValue(variableValue);
                copy.setEnabled(true);
                sanitized.add(copy);
            }
            return sanitized;
        }
    }
}

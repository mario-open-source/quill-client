package com.quillapiclient.components;

import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironmentValue;
import com.quillapiclient.utility.TableEditUtil;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnvironmentVariablesWindow {
    private final int environmentId;
    private final String environmentName;
    private final JFrame frame;
    private final EnvironmentValuesTableModel tableModel;
    private JTable table;

    public EnvironmentVariablesWindow(int environmentId, String environmentName) {
        this.environmentId = environmentId;
        this.environmentName = environmentName;
        this.frame = new JFrame("Environment Variables - " + environmentName);
        this.tableModel = new EnvironmentValuesTableModel(EnvironmentDao.getEnvironmentValueRecords(environmentId));
        buildUi();
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel header = new JLabel("Environment: " + environmentName, SwingConstants.LEFT);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(header, BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(500, 300));
        frame.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        JButton deleteButton = new JButton("Delete");
        JButton saveButton = new JButton("Save");

        addButton.addActionListener(event -> {
            int newRow = tableModel.addEmptyRow();
            if (newRow < 0) {
                return;
            }

            // Ensure the inserted row is visible and ready for immediate typing.
            table.setRowSelectionInterval(newRow, newRow);
            table.setColumnSelectionInterval(0, 0);
            table.scrollRectToVisible(table.getCellRect(newRow, 0, true));
            table.requestFocusInWindow();
            table.editCellAt(newRow, 0);
            SwingUtilities.invokeLater(() -> {
                if (table.getEditorComponent() != null) {
                    table.getEditorComponent().requestFocusInWindow();
                }
            });
        });
        deleteButton.addActionListener(event -> deleteSelectedRows());
        saveButton.addActionListener(event -> saveValues());

        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel rightActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActionsPanel.setOpaque(false);
        rightActionsPanel.add(deleteButton);
        rightActionsPanel.add(saveButton);

        buttonPanel.add(addButton, BorderLayout.WEST);
        buttonPanel.add(rightActionsPanel, BorderLayout.EAST);
        buttonPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(buttonPanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void saveValues() {
        TableEditUtil.commitOrCancelTableEdit(table);
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

    private void deleteSelectedRows() {
        TableEditUtil.commitOrCancelTableEdit(table);
        int[] selectedViewRows = table.getSelectedRows();
        if (selectedViewRows == null || selectedViewRows.length == 0) {
            return;
        }

        String message = selectedViewRows.length == 1
            ? "Delete the selected environment variable?"
            : "Delete the " + selectedViewRows.length + " selected environment variables?";
        int choice = JOptionPane.showConfirmDialog(
            frame,
            message,
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        List<Integer> selectedModelRows = new ArrayList<>(selectedViewRows.length);
        for (int viewRow : selectedViewRows) {
            selectedModelRows.add(table.convertRowIndexToModel(viewRow));
        }

        List<Integer> persistedIds = tableModel.getPersistedIdsAtRows(selectedModelRows);
        if (!persistedIds.isEmpty() && !EnvironmentDao.deleteEnvironmentValuesByIds(environmentId, persistedIds)) {
            JOptionPane.showMessageDialog(
                frame,
                "Failed to delete environment variable(s).",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        tableModel.removeRows(selectedModelRows);
    }

    private static class EnvironmentValuesTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Key", "Value"};
        private final List<EnvironmentValueRow> rows;

        private EnvironmentValuesTableModel(List<EnvironmentDao.EnvironmentValueRecord> initialRecords) {
            this.rows = new ArrayList<>();
            if (initialRecords == null) {
                return;
            }

            for (EnvironmentDao.EnvironmentValueRecord record : initialRecords) {
                if (record == null || record.value == null) {
                    continue;
                }
                rows.add(new EnvironmentValueRow(record.id, record.value));
            }
        }

        public List<Integer> getPersistedIdsAtRows(List<Integer> rowIndexes) {
            List<Integer> ids = new ArrayList<>();
            if (rowIndexes == null || rowIndexes.isEmpty()) {
                return ids;
            }

            for (Integer rowIndex : rowIndexes) {
                if (rowIndex == null || rowIndex < 0 || rowIndex >= rows.size()) {
                    continue;
                }
                Integer id = rows.get(rowIndex).id;
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
            return ids;
        }

        public void removeRows(List<Integer> rowIndexes) {
            if (rowIndexes == null || rowIndexes.isEmpty()) {
                return;
            }

            List<Integer> sortedRows = new ArrayList<>(rowIndexes);
            sortedRows.sort(Collections.reverseOrder());
            for (Integer rowIndex : sortedRows) {
                if (rowIndex == null || rowIndex < 0 || rowIndex >= rows.size()) {
                    continue;
                }
                rows.remove((int) rowIndex);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
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
            PostmanEnvironmentValue value = rows.get(rowIndex).value;
            return columnIndex == 0 ? value.getKey() : value.getValue();
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            PostmanEnvironmentValue value = rows.get(rowIndex).value;
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

        public int addEmptyRow() {
            rows.add(new EnvironmentValueRow(null, new PostmanEnvironmentValue()));
            int newRow = rows.size() - 1;
            fireTableRowsInserted(newRow, newRow);
            return newRow;
        }

        public List<PostmanEnvironmentValue> getValuesForSave() {
            List<PostmanEnvironmentValue> sanitized = new ArrayList<>();
            for (EnvironmentValueRow row : rows) {
                String key = row.value.getKey() != null ? row.value.getKey().trim() : "";
                String variableValue = row.value.getValue();
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

        private static class EnvironmentValueRow {
            private final Integer id;
            private final PostmanEnvironmentValue value;

            private EnvironmentValueRow(Integer id, PostmanEnvironmentValue value) {
                this.id = id;
                this.value = value;
            }
        }
    }
}

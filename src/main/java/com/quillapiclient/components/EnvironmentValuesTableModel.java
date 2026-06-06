package com.quillapiclient.components;

import com.quillapiclient.db.EnvironmentDao;
import com.quillapiclient.objects.PostmanEnvironmentValue;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Table model for the environment-variables editor window.
 *
 * <p>Each row wraps a {@link PostmanEnvironmentValue} and an optional
 * persistence ID for rows that already exist in the database.</p>
 */
public class EnvironmentValuesTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = { "Key", "Value" };
    private final List<EnvironmentValueRow> rows;

    public EnvironmentValuesTableModel(
        List<EnvironmentDao.EnvironmentValueRecord> initialRecords
    ) {
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

    // ---- row-level operations ----

    public int addEmptyRow() {
        rows.add(
            new EnvironmentValueRow(null, new PostmanEnvironmentValue())
        );
        int newRow = rows.size() - 1;
        fireTableRowsInserted(newRow, newRow);
        return newRow;
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

    public List<Integer> getPersistedIdsAtRows(List<Integer> rowIndexes) {
        List<Integer> ids = new ArrayList<>();
        if (rowIndexes == null || rowIndexes.isEmpty()) {
            return ids;
        }

        for (Integer rowIndex : rowIndexes) {
            if (
                rowIndex == null || rowIndex < 0 || rowIndex >= rows.size()
            ) {
                continue;
            }
            Integer id = rows.get(rowIndex).id;
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    public List<PostmanEnvironmentValue> getValuesForSave() {
        List<PostmanEnvironmentValue> sanitized = new ArrayList<>();
        for (EnvironmentValueRow row : rows) {
            String key =
                row.value.getKey() != null ? row.value.getKey().trim() : "";
            if (key.isEmpty()) {
                continue;
            }
            PostmanEnvironmentValue copy = new PostmanEnvironmentValue();
            copy.setKey(key);
            copy.setValue(row.value.getValue());
            copy.setEnabled(true);
            sanitized.add(copy);
        }
        return sanitized;
    }

    // ---- TableModel ----

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

    // ---- inner holder ----

    private static class EnvironmentValueRow {

        private final Integer id;
        private final PostmanEnvironmentValue value;

        private EnvironmentValueRow(Integer id, PostmanEnvironmentValue value) {
            this.id = id;
            this.value = value;
        }
    }
}

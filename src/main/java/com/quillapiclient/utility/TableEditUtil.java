package com.quillapiclient.utility;

import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

public final class TableEditUtil {
    private TableEditUtil() {
        // Utility class
    }

    public static void commitOrCancelTableEdit(JTable table) {
        if (table == null) {
            return;
        }

        if (table.isEditing()) {
            TableCellEditor editor = table.getCellEditor();
            if (editor != null) {
                // Try commit. If commit fails (validation), cancel to keep UI stable.
                boolean committed = editor.stopCellEditing();
                if (!committed) {
                    editor.cancelCellEditing();
                }
            }
        }
    }
}

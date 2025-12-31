package com.quillapiclient.components;

import com.quillapiclient.objects.Header;
import com.quillapiclient.objects.Request;
import com.quillapiclient.utility.AppColorTheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;

public class HeadersPanel {
    private JScrollPane scrollPane;
    private final String[] columnNames = {"Key", "Value"};
    private JTable headersTable;
    private DefaultTableModel model;

    public HeadersPanel() {
        model = new DefaultTableModel(columnNames,0);
        headersTable = new JTable(model);
        headersTable.setBackground(AppColorTheme.SCROLL_PANE_BACKGROUND);
        scrollPane = new JScrollPane(headersTable);
    }

    public void populateFromRequest(Request request){
        // Clear all existing rows
        model.setRowCount(0);
        
        // If request is null or has no headers, we're done (already cleared)
        if(request == null || request.getHeader() == null || request.getHeader().isEmpty()){
            return;
        }
        
        // Add each header as a row with [key, value]
        for(Header header : request.getHeader()){
            if(header != null && header.getKey() != null){
                model.addRow(new Object[]{
                    header.getKey(),
                    header.getValue() != null ? header.getValue() : ""
                });
            }
        }
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }
    
    /**
     * Gets all headers from the table as a list of Header objects
     */
    public List<Header> getHeaders() {
        
        List<Header> headers = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            String key = (String) model.getValueAt(i, 0);
            String value = (String) model.getValueAt(i, 1);
            if (key != null && !key.trim().isEmpty()) {
                Header header = new Header();
                header.setKey(key.trim());
                header.setValue(value != null ? value.trim() : "");
                header.setDisabled(false);
                headers.add(header);
            }
        }
        return headers;
    }
    
    /**
     * Gets the table for adding listeners
     */
    public JTable getTable() {
        return headersTable;
    }
    
    /**
     * Gets the table model for adding listeners
     */
    public DefaultTableModel getTableModel() {
        return model;
    }
}

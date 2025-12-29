package com.quillapiclient.components;

import com.quillapiclient.objects.Query;
import com.quillapiclient.objects.Request;
import com.quillapiclient.utility.AppColorTheme;

import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ParamsPanel {
    private JScrollPane scrollPane;
    private final String[] columnNames = {"Key", "Value"};
    private JTable paramsTable;
    private DefaultTableModel model;

    public ParamsPanel() {
        model = new DefaultTableModel(columnNames,0);
        paramsTable = new JTable(model);
        paramsTable.setBackground(AppColorTheme.SCROLL_PANE_BACKGROUND);
        scrollPane = new JScrollPane(paramsTable);
    }

    public void populateFromRequest(Request request){
        // Clear all existing rows
        model.setRowCount(0);
        
        // If request is null or URL is null, we're done (already cleared)
        if(request == null || request.getUrl() == null){
            return;
        }
        
        List<Query> queryParams = request.getUrl().getQuery();
        
        // If no query params, we're done (already cleared)
        if(queryParams == null || queryParams.isEmpty()){
            return;
        }
        
        // Add each query param as a row with [key, value]
        for(Query queryParam : queryParams){
            if(queryParam != null && queryParam.getKey() != null){
                model.addRow(new Object[]{
                    queryParam.getKey(),
                    queryParam.getValue() != null ? queryParam.getValue() : ""
                });
            }
        }
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }
}

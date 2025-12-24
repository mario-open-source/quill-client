package com.quillapiclient.components;

import com.quillapiclient.utility.MethodColorUtil;

import javax.swing.*;
import java.awt.*;

public class MethodComboBoxRenderer extends JLabel implements ListCellRenderer<String> {
    
    public MethodComboBoxRenderer() {
        setOpaque(true);
        setHorizontalAlignment(LEFT);
        setVerticalAlignment(CENTER);
    }
    
    @Override
    public Component getListCellRendererComponent(JList<? extends String> list,
            String value, int index, boolean isSelected, boolean cellHasFocus) {
        
        if (value == null) {
            setText("");
            return this;
        }
        
        Color methodColor = MethodColorUtil.getMethodColor(value);
        
        // Always use method color for text to match the border
        setForeground(methodColor);
        
        // Set background color based on selection
        if (isSelected) {
            setBackground(list.getSelectionBackground());
        } else {
            setBackground(list.getBackground());
        }
        
        // Set the text with the method name
        setText(value);
        setFont(list.getFont());
        
        return this;
    }
}


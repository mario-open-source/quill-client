package com.quillapiclient.components;

import com.quillapiclient.utility.MethodColorUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class MethodTreeCellRenderer extends DefaultTreeCellRenderer {
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        
        Component component = super.getTreeCellRendererComponent(
            tree, value, sel, expanded, leaf, row, hasFocus);
        
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            String text = label.getText();
            
            // Check if the text contains a method in brackets like [GET], [POST], etc.
            if (text != null && text.contains("[") && text.contains("]")) {
                int startIndex = text.indexOf("[");
                int endIndex = text.indexOf("]");
                
                if (startIndex < endIndex) {
                    String method = text.substring(startIndex + 1, endIndex).toUpperCase();
                    Color methodColor = MethodColorUtil.getMethodColor(method);
                    
                    // Create HTML formatted text with colored method
                    String name = text.substring(0, startIndex).trim();
                    String methodPart = text.substring(startIndex, endIndex + 1);
                    
                    // Set the text with HTML formatting for color
                    label.setText("<html><span style='color:rgb(" +
                        methodColor.getRed() + "," + methodColor.getGreen() + "," + 
                        methodColor.getBlue() + ")'>" + methodPart + "</span> "+name+"</html>");
                }
            }
        }
        
        return component;
    }
}


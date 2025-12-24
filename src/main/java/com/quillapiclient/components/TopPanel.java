package com.quillapiclient.components;

import com.quillapiclient.utility.MethodColorUtil;
import com.quillapiclient.utility.AppColorTheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

public class TopPanel {
    private JPanel panel;
    private JComboBox<String> methodDropdown;
    private JTextField urlField;
    private JButton sendButton;
    private JButton saveButton;
    private final String SAVE_TEXT = "Save";
    private final String SEND_TEXT = "Send";
    
    public TopPanel() {
        this.panel = createPanel();
    }
    
    private JPanel createPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        final String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"};
        methodDropdown = new JComboBox<>(methods);
        
        // Set custom renderer for colored text
        methodDropdown.setRenderer(new MethodComboBoxRenderer());
        
        // Add listener to update border color when selection changes
        methodDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMethodBorder();
            }
        });
        
        // Set initial border color
        updateMethodBorder();
        
        urlField = new JTextField("Enter URL or paste text");
        sendButton = new JButton(SEND_TEXT);
        sendButton.setBackground(new Color(13, 90, 167));
        saveButton = new JButton(SAVE_TEXT);
        JPanel buttons = new JPanel();

        buttons.add(sendButton);
        buttons.add(saveButton);

        topPanel.add(methodDropdown, BorderLayout.WEST);
        topPanel.add(urlField, BorderLayout.CENTER);
        topPanel.add(buttons, BorderLayout.EAST);
        return topPanel;
    }
    
    private void updateMethodBorder() {
        String selectedMethod = (String) methodDropdown.getSelectedItem();
        Color methodColor = MethodColorUtil.getMethodColor(selectedMethod);
        
        // Create a border with the method color (2px width for visibility)
        Border coloredBorder = new LineBorder(methodColor, 1, true);
        Border defaultBorder = new MatteBorder(1, 1, 1, 1, 
            AppColorTheme.getColor("ComboBox.borderColor", AppColorTheme.COMBO_BOX_BORDER));
        
        // Combine borders for better visibility
        methodDropdown.setBorder(new CompoundBorder(coloredBorder, defaultBorder));
        
        // Set the text color to match the border
        methodDropdown.setForeground(methodColor);
    }
    
    public JPanel getPanel() {
        return panel;
    }
    
    public JComboBox<String> getMethodDropdown() {
        return methodDropdown;
    }
    
    public JTextField getUrlField() {
        return urlField;
    }
    
    public JButton getSendButton() {
        return sendButton;
    }
    
    public JButton getSaveButton() {
        return saveButton;
    }
}


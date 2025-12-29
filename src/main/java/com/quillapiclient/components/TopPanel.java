package com.quillapiclient.components;

import com.quillapiclient.utility.MethodColorUtil;
import com.quillapiclient.utility.AppColorTheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class TopPanel {
    private JPanel panel;
    private JComboBox<String> methodDropdown;
    private JTextField urlField;
    private JButton sendButton;
    private JButton saveButton;
    private final String SAVE_TEXT = "Save";
    private final String SEND_TEXT = "Send";
    private final String URL_PLACEHOLDER = "Enter URL or paste text";
    private boolean isPlaceholderShown = true;
    
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
        
        // Set fixed height for all components (30 pixels) - only constrain height, not width
        int fixedHeight = 35;
        
        // Method dropdown
        Dimension methodSize = methodDropdown.getPreferredSize();
        methodDropdown.setPreferredSize(new Dimension(methodSize.width, fixedHeight));
        methodDropdown.setMinimumSize(new Dimension(methodSize.width, fixedHeight));
        methodDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));
        
        // URL field with placeholder text
        urlField = new JTextField();
        setupPlaceholderText();
        Dimension urlSize = urlField.getPreferredSize();
        urlField.setPreferredSize(new Dimension(urlSize.width, fixedHeight));
        urlField.setMinimumSize(new Dimension(0, fixedHeight));
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));
        
        // Send button
        sendButton = new JButton(SEND_TEXT);
        sendButton.setBackground(new Color(13, 90, 167));
        sendButton.setMargin(new Insets(0, 10, 0, 10)); // Remove vertical padding
        Dimension sendSize = sendButton.getPreferredSize();
        sendButton.setPreferredSize(new Dimension(sendSize.width, fixedHeight));
        sendButton.setMinimumSize(new Dimension(sendSize.width, fixedHeight));
        sendButton.setMaximumSize(new Dimension(sendSize.width, fixedHeight));
        
        // Save button
        saveButton = new JButton(SAVE_TEXT);
        saveButton.setMargin(new Insets(0, 10, 0, 10)); // Remove vertical padding
        Dimension saveSize = saveButton.getPreferredSize();
        saveButton.setPreferredSize(new Dimension(saveSize.width, fixedHeight));
        saveButton.setMinimumSize(new Dimension(saveSize.width, fixedHeight));
        saveButton.setMaximumSize(new Dimension(saveSize.width, fixedHeight));
        
        // Use FlowLayout with no gaps to ensure buttons align properly
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(sendButton);
        buttons.add(saveButton);
        
        // Set height constraints after adding buttons so width is calculated correctly
        Dimension buttonsSize = buttons.getPreferredSize();
        buttons.setPreferredSize(new Dimension(buttonsSize.width, fixedHeight));
        buttons.setMinimumSize(new Dimension(buttonsSize.width, fixedHeight));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));

        topPanel.add(methodDropdown, BorderLayout.WEST);
        topPanel.add(urlField, BorderLayout.CENTER);
        topPanel.add(buttons, BorderLayout.EAST);
        return topPanel;
    }
    
    private void setupPlaceholderText() {
        urlField.setText(URL_PLACEHOLDER);
        urlField.setForeground(Color.GRAY);
        isPlaceholderShown = true;
        
        urlField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isPlaceholderShown) {
                    urlField.setText("");
                    urlField.setForeground(AppColorTheme.TEXT_FIELD_FOREGROUND);
                    isPlaceholderShown = false;
                }
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                if (urlField.getText().trim().isEmpty()) {
                    urlField.setText(URL_PLACEHOLDER);
                    urlField.setForeground(Color.GRAY);
                    isPlaceholderShown = true;
                }
            }
        });
    }
    
    private void updateMethodBorder() {
        String selectedMethod = (String) methodDropdown.getSelectedItem();
        Color methodColor = MethodColorUtil.getMethodColor(selectedMethod);
        
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
    
    /**
     * Gets the URL text, returning empty string if placeholder is shown
     */
    public String getUrlText() {
        if (isPlaceholderShown) {
            return "";
        }
        return urlField.getText().trim();
    }
    
    /**
     * Sets the URL text, clearing placeholder if needed
     */
    public void setUrlText(String text) {
        if (text == null || text.trim().isEmpty()) {
            urlField.setText(URL_PLACEHOLDER);
            urlField.setForeground(Color.GRAY);
            isPlaceholderShown = true;
        } else {
            urlField.setText(text);
            urlField.setForeground(AppColorTheme.TEXT_FIELD_FOREGROUND);
            isPlaceholderShown = false;
        }
    }
    
    public JButton getSendButton() {
        return sendButton;
    }
    
    public JButton getSaveButton() {
        return saveButton;
    }
}


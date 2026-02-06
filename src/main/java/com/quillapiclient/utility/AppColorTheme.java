package com.quillapiclient.utility;

import java.awt.Color;
import javax.swing.UIManager;

/**
 * Centralized color theme management for the application.
 * All UI colors should be defined here for easy maintenance and theme switching.
 */
public class AppColorTheme {
    
    // Background Colors
    public static final Color PANEL_BACKGROUND = new Color(30, 30, 30);
    public static final Color TEXT_AREA_BACKGROUND = new Color(25, 25, 25);
    public static final Color TEXT_FIELD_BACKGROUND = new Color(35, 35, 35);
    public static final Color COMBO_BOX_BACKGROUND = new Color(35, 35, 35);
    public static final Color BUTTON_BACKGROUND = new Color(40, 40, 40);
    public static final Color TREE_BACKGROUND = new Color(28, 28, 28);
    public static final Color SCROLL_PANE_BACKGROUND = new Color(30, 30, 30);
    public static final Color TABBED_PANE_BACKGROUND = new Color(30, 30, 30);
    public static final Color TABBED_PANE_SELECTED = new Color(40, 40, 40);
    
    // Text Colors
    public static final Color TEXT_PRIMARY = new Color(220, 220, 220);
    public static final Color TEXT_SECONDARY = new Color(180, 180, 180);
    public static final Color TEXT_AREA_FOREGROUND = new Color(220, 220, 220);
    public static final Color TEXT_FIELD_FOREGROUND = new Color(220, 220, 220);
    public static final Color LABEL_FOREGROUND = new Color(220, 220, 220);
    public static final Color TREE_FOREGROUND = new Color(220, 220, 220);
    
    // Border Colors
    public static final Color BORDER_COLOR = new Color(50, 50, 50);
    public static final Color COMBO_BOX_BORDER = Color.GRAY;
    
    // Selection Colors
    public static final Color TREE_SELECTION_BACKGROUND = new Color(60, 60, 60);
    public static final Color TREE_SELECTION_FOREGROUND = new Color(255, 255, 255);
    
    // Error Colors
    public static final Color ERROR_TEXT = new Color(255, 100, 100);
    public static final Color ERROR_BACKGROUND = new Color(40, 20, 20);
    
    // Success Colors (if needed)
    public static final Color SUCCESS_TEXT = new Color(100, 255, 100);
    public static final Color SUCCESS_BACKGROUND = new Color(20, 40, 20);
    
    // Warning Colors (if needed)
    public static final Color WARNING_TEXT = new Color(255, 200, 100);
    public static final Color WARNING_BACKGROUND = new Color(40, 35, 20);

    public static final Color UNSAVED_CHANGES_COLOR = new Color(100,149,237);
    
    /**
     * Applies the dark theme colors to the UIManager.
     * Call this method after setting the Look and Feel.
     */
    public static void applyDarkTheme() {
        // Background colors
        UIManager.put("Panel.background", PANEL_BACKGROUND);
        UIManager.put("TextArea.background", TEXT_AREA_BACKGROUND);
        UIManager.put("TextField.background", TEXT_FIELD_BACKGROUND);
        UIManager.put("ComboBox.background", COMBO_BOX_BACKGROUND);
        UIManager.put("Button.background", BUTTON_BACKGROUND);
        UIManager.put("Tree.background", TREE_BACKGROUND);
        UIManager.put("Tree.textBackground", TREE_BACKGROUND);
        UIManager.put("List.background", TREE_BACKGROUND);
        UIManager.put("List.foreground", TREE_FOREGROUND);
        UIManager.put("ScrollPane.background", SCROLL_PANE_BACKGROUND);
        UIManager.put("ScrollPane.viewportBackground", SCROLL_PANE_BACKGROUND);
        UIManager.put("TabbedPane.background", TABBED_PANE_BACKGROUND);
        UIManager.put("TabbedPane.selected", TABBED_PANE_SELECTED);
        
        // Border colors
        UIManager.put("Component.borderColor", BORDER_COLOR);
        
        // Text colors
        UIManager.put("TextArea.foreground", TEXT_AREA_FOREGROUND);
        UIManager.put("TextField.foreground", TEXT_FIELD_FOREGROUND);
        UIManager.put("Label.foreground", LABEL_FOREGROUND);
        UIManager.put("Tree.foreground", TREE_FOREGROUND);
        
        // Selection colors
        UIManager.put("Tree.selectionBackground", TREE_SELECTION_BACKGROUND);
        UIManager.put("Tree.selectionForeground", TREE_SELECTION_FOREGROUND);
        UIManager.put("List.selectionBackground", TREE_SELECTION_BACKGROUND);
        UIManager.put("List.selectionForeground", TREE_SELECTION_FOREGROUND);
    }
    
    /**
     * Gets a color from UIManager with a fallback to the provided default.
     * 
     * @param key The UIManager key
     * @param defaultValue The default color if the key is not found
     * @return The color from UIManager or the default
     */
    public static Color getColor(String key, Color defaultValue) {
        Object color = UIManager.get(key);
        if (color instanceof Color) {
            return (Color) color;
        }
        return defaultValue;
    }
}


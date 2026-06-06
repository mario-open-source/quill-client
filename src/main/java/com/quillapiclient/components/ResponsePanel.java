package com.quillapiclient.components;

import com.quillapiclient.utility.AppColorTheme;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

public class ResponsePanel {

    private JPanel panel;
    private RSyntaxTextArea responseArea;
    private JLabel statusLabel;
    private JLabel durationLabel;
    private JLabel sizeLabel;
    private boolean errorState;
    private final String RESPONSE_LABEL = "Response";
    private final String STATUS_LABEL = "Status";
    private final String DURATION_LABEL = "Duration";
    private final String SIZE_LABEL = "Size";

    public ResponsePanel() {
        this.panel = createPanel();
        this.errorState = false;
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create top panel with Response label on left and Status/Duration on right
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel responseLabel = new JLabel(RESPONSE_LABEL);

        // Create panel for Status and Duration labels on the right
        JPanel rightPanel = new JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0)
        );
        statusLabel = new JLabel(STATUS_LABEL);
        durationLabel = new JLabel(DURATION_LABEL);
        sizeLabel = new JLabel(SIZE_LABEL);
        rightPanel.add(statusLabel);
        rightPanel.add(durationLabel);
        rightPanel.add(sizeLabel);
        topPanel.add(responseLabel, BorderLayout.WEST);
        topPanel.add(rightPanel, BorderLayout.EAST);

        // Response area with JSON syntax highlighting
        responseArea = new RSyntaxTextArea();
        responseArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        responseArea.setCodeFoldingEnabled(true);
        responseArea.setAntiAliasingEnabled(true);
        responseArea.setFont(
            new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14)
        );
        responseArea.setEditable(false);
        responseArea.setHighlightCurrentLine(false);
        responseArea.setTabSize(2);
        responseArea.setTabsEmulated(true);

        // Apply dark theme
        try {
            Theme theme = Theme.load(
                getClass().getResourceAsStream(
                    "/org/fifesoft/rsyntaxtextarea/themes/dark.xml"
                )
            );
            theme.apply(responseArea);
        } catch (Exception e) {
            // Fallback: manual dark styling
            responseArea.setBackground(new Color(25, 25, 25));
            responseArea.setForeground(new Color(220, 220, 220));
            responseArea.setCaretColor(new Color(220, 220, 220));
            responseArea.setCurrentLineHighlightColor(new Color(35, 35, 35));
        }

        JScrollPane responseScroll = new JScrollPane(responseArea);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(responseScroll, BorderLayout.CENTER);
        return panel;
    }

    public JPanel getPanel() {
        return panel;
    }

    public RSyntaxTextArea getResponseArea() {
        return responseArea;
    }

    public void setResponse(String response) {
        if (responseArea != null) {
            responseArea.setText(response);
            responseArea.setCaretPosition(0); // Scroll to top
        }
    }

    public void setErrorState(boolean isError) {
        this.errorState = isError;
        if (responseArea != null) {
            if (isError) {
                // Override theme for error state
                responseArea.setForeground(AppColorTheme.ERROR_TEXT);
                responseArea.setBackground(AppColorTheme.ERROR_BACKGROUND);
            } else {
                // Re-apply dark theme colors
                try {
                    Theme theme = Theme.load(
                        getClass().getResourceAsStream(
                            "/org/fifesoft/rsyntaxtextarea/themes/dark.xml"
                        )
                    );
                    theme.apply(responseArea);
                } catch (Exception e) {
                    responseArea.setForeground(
                        AppColorTheme.TEXT_AREA_FOREGROUND
                    );
                    responseArea.setBackground(
                        AppColorTheme.TEXT_AREA_BACKGROUND
                    );
                }
            }
        }
    }

    public boolean isErrorState() {
        return errorState;
    }

    /**
     * Updates the status label with the status code from the response.
     *
     * @param statusCode The HTTP status code
     */
    public void setStatus(int statusCode) {
        if (statusLabel != null) {
            statusLabel.setText(STATUS_LABEL + ": " + statusCode);
        }
    }

    /**
     * Updates the duration label with the duration from the response.
     *
     * @param duration The duration in milliseconds
     */
    public void setDuration(long duration) {
        if (durationLabel != null) {
            durationLabel.setText(DURATION_LABEL + ": " + duration + " ms");
        }
    }

    public void setSize(String size) {
        if (sizeLabel != null) {
            sizeLabel.setText(SIZE_LABEL + ": " + size);
        }
    }

    /**
     * Resets the status and duration labels to their default values.
     */
    public void resetStatusDurationSize() {
        if (statusLabel != null) {
            statusLabel.setText(STATUS_LABEL);
        }
        if (durationLabel != null) {
            durationLabel.setText(DURATION_LABEL);
        }
        if (sizeLabel != null) {
            sizeLabel.setText(SIZE_LABEL);
        }
    }
}

package com.quillapiclient.components;

import com.quillapiclient.objects.Auth;
import com.quillapiclient.objects.AuthType;
import com.quillapiclient.objects.Credential;
import com.quillapiclient.objects.Request;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Authorization panel with combo-box-driven CardLayout.
 * All auth-type sub-panels are built once and shown/hidden via the layout,
 * eliminating the fragile {@code removeAll()} + rebuild pattern.
 */
public class AuthPanel {

    private JPanel authPanel;
    private JComboBox<String> authTypeComboBox;
    private JTextField userField;
    private JTextField passField;
    private JTextField tokenField;
    private JTextField jwtTokenField;
    private CardLayout cardLayout;
    private JPanel cardsPanel;

    // Card-layout keys (must match AuthType display names)
    private static final String NO_AUTH_TEXT = AuthType.NONE.getDisplayName();
    private static final String BASIC_AUTH_TEXT =
        AuthType.BASIC.getDisplayName();
    private static final String BEARER_TOKEN_TEXT =
        AuthType.BEARER.getDisplayName();
    private static final String JWT_BEARER_TEXT =
        AuthType.JWT_BEARER.getDisplayName();

    public AuthPanel() {
        authPanel = new JPanel(new BorderLayout());

        // Initialize fields (per-card fields to avoid reparenting issues)
        userField = new JTextField();
        userField.setToolTipText("Username");
        passField = new JTextField();
        passField.setToolTipText("Password");
        tokenField = new JTextField();
        tokenField.setToolTipText("Bearer Token");
        jwtTokenField = new JTextField();
        jwtTokenField.setToolTipText("JWT Token");

        // Combo box at top
        authTypeComboBox = new JComboBox<>(AuthType.displayNames());

        // Build cards once
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setOpaque(false);
        cardsPanel.add(createNoAuthCard(), NO_AUTH_TEXT);
        cardsPanel.add(createBasicAuthCard(), BASIC_AUTH_TEXT);
        cardsPanel.add(createBearerCard(), BEARER_TOKEN_TEXT);
        cardsPanel.add(createJwtBearerCard(), JWT_BEARER_TEXT);

        // Outer wrapper with padding
        JPanel outerWrapper = new JPanel(new BorderLayout());
        outerWrapper.setOpaque(false);
        outerWrapper.setBorder(BorderFactory.createEmptyBorder(26, 26, 26, 26));

        // Stack: combo on top, cards below (same width constraint)
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 0);

        gbc.gridy = 0;
        contentPanel.add(authTypeComboBox, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(20, 0, 0, 0);
        contentPanel.add(cardsPanel, gbc);

        outerWrapper.add(contentPanel, BorderLayout.NORTH);
        authPanel.add(outerWrapper, BorderLayout.CENTER);

        // Wire combo box to switch cards on selection change
        authTypeComboBox.addActionListener(e ->
            showCardForType(currentCardKey())
        );

        // Show the initial card
        showCardForType(NO_AUTH_TEXT);
    }

    // ---- card builders ----

    private JPanel createNoAuthCard() {
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel label = new JLabel("No authentication required");
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        card.add(label);
        return card;
    }

    private JPanel createBasicAuthCard() {
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel userLabel = new JLabel("Username:");
        userLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        configureField(userField);
        card.add(userLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(userField);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        configureField(passField);
        card.add(Box.createVerticalStrut(10));
        card.add(passLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(passField);

        return card;
    }

    private JPanel createBearerCard() {
        return buildTokenCard("Token:", tokenField);
    }

    private JPanel createJwtBearerCard() {
        return buildTokenCard("JWT Token:", jwtTokenField);
    }

    private JPanel buildTokenCard(String labelText, JTextField field) {
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        configureField(field);
        card.add(label);
        card.add(Box.createVerticalStrut(5));
        card.add(field);

        return card;
    }

    private void configureField(JTextField field) {
        field.setAlignmentX(JTextField.LEFT_ALIGNMENT);
        field.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height)
        );
    }

    // ---- card switching ----

    /**
     * Shows the card for the given auth type string.
     */
    private void showCardForType(String authType) {
        cardLayout.show(cardsPanel, authType);
    }

    /**
     * Returns the card key that matches the currently selected combo item.
     */
    private String currentCardKey() {
        String selected = (String) authTypeComboBox.getSelectedItem();
        if (selected == null) return NO_AUTH_TEXT;
        return selected;
    }

    // ---- public API ----

    public void populateFromRequest(Request request) {
        if (request == null || request.getAuth() == null) {
            userField.setText("");
            passField.setText("");
            tokenField.setText("");
            jwtTokenField.setText("");
            authTypeComboBox.setSelectedItem(NO_AUTH_TEXT);
            showCardForType(NO_AUTH_TEXT);
            return;
        }

        Auth auth = request.getAuth();

        if (auth.getBasic() != null) {
            List<Credential> basic = auth.getBasic();
            if (basic.size() > 0) {
                userField.setText(basic.get(0).getValue());
            } else {
                userField.setText("");
            }
            if (basic.size() > 1) {
                passField.setText(basic.get(1).getValue());
            } else {
                passField.setText("");
            }
            tokenField.setText("");
            jwtTokenField.setText("");
            authTypeComboBox.setSelectedItem(BASIC_AUTH_TEXT);
        } else if (auth.getBearer() != null) {
            List<Credential> bearer = auth.getBearer();
            String tokenValue =
                bearer.size() > 0 ? bearer.get(0).getValue() : "";
            tokenField.setText(tokenValue);
            jwtTokenField.setText(tokenValue);
            userField.setText("");
            passField.setText("");
            authTypeComboBox.setSelectedItem(BEARER_TOKEN_TEXT);
        } else {
            userField.setText("");
            passField.setText("");
            tokenField.setText("");
            jwtTokenField.setText("");
            authTypeComboBox.setSelectedItem(NO_AUTH_TEXT);
        }

        showCardForType(currentCardKey());
    }

    public String getAuthType() {
        return (String) authTypeComboBox.getSelectedItem();
    }

    public String getUsername() {
        return userField != null ? userField.getText() : "";
    }

    public String getPassword() {
        return passField != null ? passField.getText() : "";
    }

    public String getToken() {
        if (AuthType.JWT_BEARER == AuthType.fromDisplayName(getAuthType())) {
            return jwtTokenField != null ? jwtTokenField.getText() : "";
        }
        return tokenField != null ? tokenField.getText() : "";
    }

    public JComboBox<String> getAuthTypeComboBox() {
        return authTypeComboBox;
    }

    public JPanel getPanel() {
        return authPanel;
    }

    public Auth buildAuth() {
        AuthType authType = AuthType.fromDisplayName(getAuthType());

        if (authType == AuthType.NONE) {
            return null;
        }

        Auth auth = new Auth();
        auth.setType(authType.getDbKey());

        if (authType == AuthType.BASIC) {
            List<Credential> basic = new ArrayList<>();
            Credential usernameCred = new Credential();
            usernameCred.setKey("username");
            usernameCred.setValue(getUsername());
            basic.add(usernameCred);
            Credential passwordCred = new Credential();
            passwordCred.setKey("password");
            passwordCred.setValue(getPassword());
            basic.add(passwordCred);
            auth.setBasic(basic);
        } else if (
            authType == AuthType.BEARER || authType == AuthType.JWT_BEARER
        ) {
            List<Credential> bearer = new ArrayList<>();
            Credential tokenCred = new Credential();
            tokenCred.setKey("token");
            tokenCred.setValue(getToken());
            bearer.add(tokenCred);
            auth.setBearer(bearer);
        }

        return auth;
    }

    public void addChangeListener(Runnable listener) {
        java.awt.event.KeyAdapter keyAdapter = new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                listener.run();
            }
        };
        userField.addKeyListener(keyAdapter);
        passField.addKeyListener(keyAdapter);
        tokenField.addKeyListener(keyAdapter);
        jwtTokenField.addKeyListener(keyAdapter);
        authTypeComboBox.addActionListener(e -> listener.run());
    }
}

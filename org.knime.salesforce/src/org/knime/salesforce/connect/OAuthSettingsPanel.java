/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Oct 8, 2019 (benjamin): created
 */
package org.knime.salesforce.connect;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Enumeration;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;

import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.salesforce.auth.InteractiveAuthenticator;
import org.knime.salesforce.auth.InteractiveAuthenticator.AuthenticatorState;

/**
 * A default settings panel for OAuth based authentication with a service.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author Ole Ostergaard, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 */
//TODO: Is this always OAuth or could it be something else?
//TODO: Move somewhere else where it can be reused
final class OAuthSettingsPanel extends JPanel {

    private static final String STATUS_LABEL_TEXT = "Status: ";

    private static final String INITIAL_AUTH_STATE_TEXT = "Unknown";

    private static final String AUTHENTICATE_BUTTON_TEXT = "Authenticate";

    private static final String CANCEL_BUTTON_TEXT = "Cancel";

    private static final String CLEAR_SELCETD_BUTTON_TEXT = "Clear Selected Credentials";

    private static final String CLEAR_ALL_BUTTON_TEXT = "Clear All Credentials";

    private static final String PROGRESS_BAR_TEXT = "Auth in browser...";

    private static final int DEFAULT_COLUMN_WIDTH = 300;

    private static final int DEFAULT_CELL_HEIGHT = 30;

    private static final long serialVersionUID = 1L;

    private final InteractiveAuthenticator<?> m_auth;

    private JLabel m_authStateLabel;

    private JPanel m_authButtonOrCancelPanel;

    private JButton m_authButton;

    private JProgressBar m_progressBar;

    private JButton m_cancelButton;

    private JButton m_clearSelectedButton;

    private JButton m_clearAllButton;

    private final ButtonGroup m_locationsButtonGroup = new ButtonGroup();

    // Hack, this is only used for its dialog
    private final DialogComponentFileChooser m_credentialFileLocation = new DialogComponentFileChooser(
        new SettingsModelString("PLACEHOLDER_NOT_USED", ""),
        OAuthSettingsPanel.class.getCanonicalName(), JFileChooser.SAVE_DIALOG, false);

    // Credential save location radio buttons
    private JRadioButton m_memoryLocationButton;

    private JRadioButton m_filesystemLocationButton;

    private JRadioButton m_nodeSettingsLocationButton;

    private JPanel m_filesystemFileChooserPanel;

    /**
     * Create a new OAuth authentication panel which uses the given authenticator to do the authentication.
     *
     * @param auth the authenticator which handles the authentication
     */
    OAuthSettingsPanel(final InteractiveAuthenticator<?> auth) {
        super(new GridBagLayout());

        m_auth = auth;
        final GridBagConstraints gbc = getDefaultGBC(true);

        initPanels();

        add(createAuthButtonPanel(), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 5, 0, 5);

        add(createCredetialsLocationPanel(), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(10, 5, 10, 5);

        add(createClearButtonsPanel(), gbc);

        // Action listeners
        m_auth.addListener(this::authStateChanged);
        m_authButton.addActionListener(e -> startAuthentication());
        m_cancelButton.addActionListener(e -> cancelAuthentication());
        m_clearSelectedButton.addActionListener(e -> clearAuthentication());
        m_clearAllButton.addActionListener(e -> clearAuthentication());

        // Set the current state
        authStateChanged(m_auth.getState());
    }

    private void initPanels() {
        // Auth button
        m_authButton = new JButton(AUTHENTICATE_BUTTON_TEXT);

        // Progress bar
        m_progressBar = new JProgressBar();
        m_progressBar.setIndeterminate(true);
        m_progressBar.setStringPainted(true);
        m_progressBar.setString(PROGRESS_BAR_TEXT);

        // Cancel button
        m_cancelButton = new JButton(CANCEL_BUTTON_TEXT);

        // Panel switching between auth button and progress bar/cancel button
        m_authButtonOrCancelPanel = new JPanel(new GridBagLayout());
        m_authButtonOrCancelPanel.setPreferredSize(new Dimension(DEFAULT_COLUMN_WIDTH, DEFAULT_CELL_HEIGHT));
    }

    private void startAuthentication() {
        m_auth.authenticate();
        // Note: The UI will update because of an authenticator state change
    }

    private void cancelAuthentication() {
        m_auth.cancel();
        // Note: The UI will update because of an authenticator state change
    }

    private void clearAuthentication() {
        m_auth.cancel();
        m_auth.clearAuthentication();
        // Note: The UI will update because of an authenticator state change
    }

    private synchronized void authStateChanged(final AuthenticatorState state) {
        m_authStateLabel.setText(state.toString());
        m_authStateLabel.setForeground(state.getDisplayColor());

        // Authentication in progress: Show the progress bar and cancel button
        // Else: Show the authenticate button
        showCancelButton(AuthenticatorState.AUTHENTICATION_IN_PROGRESS.equals(state));

        // If the authentication failed: Show the reason
        if (AuthenticatorState.FAILED.equals(state)) {
            showError();
        }
    }

    /** Show the cancel button (true) or the authenticate button (false) */
    private void showCancelButton(final boolean showCancel) {
        m_authButtonOrCancelPanel.removeAll();
        if (showCancel) {
            GridBagConstraints gbc = getDefaultGBC(false);
            gbc.insets = new Insets(5, 0, 5, 0);
            gbc.weightx = 0.7;

            m_authButtonOrCancelPanel.add(m_progressBar, gbc);
            gbc.gridx++;
            gbc.weightx = 0.3;
            gbc.insets = new Insets(5, 10, 5, 0);

            m_authButtonOrCancelPanel.add(m_cancelButton, gbc);
        } else {
            GridBagConstraints gbc = getDefaultGBC(false);
            m_authButtonOrCancelPanel.add(m_authButton, gbc);
        }
        m_authButtonOrCancelPanel.revalidate();
        m_authButtonOrCancelPanel.repaint();
    }

    /** Show the error of the authenticator in a dialog */
    private void showError() {
        final String error = m_auth.getErrorDescription();
        if (error != null) {
            JOptionPane.showMessageDialog(findParentFrame(), error, "Authentication failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createAuthButtonPanel() {
        GridBagConstraints gbc = getDefaultGBC(false);

        final JPanel statusLablePanel = new JPanel(new GridBagLayout());
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, 10);

        // Status label
        statusLablePanel.add(new JLabel(STATUS_LABEL_TEXT), gbc);
        gbc.gridx++;

        // Authentication state label
        m_authStateLabel = new JLabel(INITIAL_AUTH_STATE_TEXT);
        statusLablePanel.add(m_authStateLabel, gbc);
        gbc.weightx = 1;
        gbc.gridx++;

        statusLablePanel.add(new JPanel(), gbc);
        statusLablePanel.setPreferredSize(new Dimension(DEFAULT_COLUMN_WIDTH, DEFAULT_CELL_HEIGHT));

        final JPanel authButtonPanel = new JPanel(new GridBagLayout());
        gbc = getDefaultGBC(false);
        gbc.weightx = 0.5;
        gbc.insets = new Insets(0, 0, 0, 5);

        authButtonPanel.add(m_authButtonOrCancelPanel, gbc);
        gbc.gridx++;
        gbc.insets = new Insets(0, 5, 0, 0);

        authButtonPanel.add(statusLablePanel, gbc);

        return authButtonPanel;
    }

    private Frame findParentFrame() {
        Container container = this;
        while (container != null) {
            if (container instanceof Frame) {
                return (Frame)container;
            }
            container = container.getParent();
        }
        return null;
    }

    private JPanel createCredetialsLocationPanel() {
        JPanel credentialLocationPanel = new JPanel(new GridBagLayout());
        credentialLocationPanel.setBorder(BorderFactory.createTitledBorder("Credentials Storage Location"));
        final GridBagConstraints gbc = getDefaultGBC(true);

        m_memoryLocationButton = createLocationButton(CredentialsLocationType.MEMORY, m_locationsButtonGroup);
        credentialLocationPanel.add(m_memoryLocationButton, gbc);
        gbc.gridy++;

        m_filesystemLocationButton = createLocationButton(CredentialsLocationType.FILESYSTEM, m_locationsButtonGroup);
        credentialLocationPanel.add(m_filesystemLocationButton, gbc);
        gbc.gridy++;

        m_filesystemFileChooserPanel = createFileSystemFileChooserPanel();
        credentialLocationPanel.add(m_filesystemFileChooserPanel, gbc);
        gbc.gridy++;

        m_nodeSettingsLocationButton = createLocationButton(CredentialsLocationType.NODE, m_locationsButtonGroup);
        credentialLocationPanel.add(m_nodeSettingsLocationButton, gbc);

        ActionListener updateFileChooserPanelVisibility =
            a -> updateFileChooserVisibility();
        m_memoryLocationButton.addActionListener(updateFileChooserPanelVisibility);
        m_filesystemLocationButton.addActionListener(updateFileChooserPanelVisibility);
        m_nodeSettingsLocationButton.addActionListener(updateFileChooserPanelVisibility);

        // Trigger visibility update
        updateFileChooserVisibility();

        // Allow more space for radio buttons to hide scroll bar when the file chooser is shown
        credentialLocationPanel.setPreferredSize(new Dimension(getPreferredSize().width, 200));

        return credentialLocationPanel;
    }

    private void updateFileChooserVisibility() {
        m_filesystemFileChooserPanel.setVisible(m_filesystemLocationButton.isSelected());
    }

    private JPanel createFileSystemFileChooserPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = getDefaultGBC(false);
        gbc.insets = new Insets(10, 10, 10, 10);
        panel.add(m_credentialFileLocation.getComponentPanel(), gbc);
        return panel;
    }

    private JPanel createClearButtonsPanel() {
        final JPanel clearButtonsPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = getDefaultGBC(false);
        gbc.weightx = 0.5;
        gbc.insets = new Insets(0, 0, 0, 5);

        m_clearSelectedButton = new JButton(CLEAR_SELCETD_BUTTON_TEXT);
        JPanel clearButtonWrapper = new JPanel(new GridBagLayout());
        clearButtonWrapper.add(m_clearSelectedButton, getDefaultGBC(false));
        clearButtonWrapper.setPreferredSize(new Dimension(DEFAULT_COLUMN_WIDTH, DEFAULT_CELL_HEIGHT));
        clearButtonsPanel.add(clearButtonWrapper, gbc);
        gbc.gridx++;
        gbc.insets = new Insets(0, 5, 0, 0);

        m_clearAllButton = new JButton(CLEAR_ALL_BUTTON_TEXT);
        JPanel clearALLButtonWrapper = new JPanel(new GridBagLayout());
        clearALLButtonWrapper.add(m_clearAllButton, getDefaultGBC(false));
        clearALLButtonWrapper.setPreferredSize(new Dimension(DEFAULT_COLUMN_WIDTH, DEFAULT_CELL_HEIGHT));
        clearButtonsPanel.add(clearALLButtonWrapper, gbc);

        return clearButtonsPanel;
    }

    /**
     * Creates a radio button for the given credential location type, belonging to the given button group.
     *
     * @param type The credential location type
     * @param group The button group
     * @return A radio button for the given credential location type, belonging to the given button group
     */
    private static JRadioButton createLocationButton(final CredentialsLocationType type, final ButtonGroup group) {
        String buttonLabel = type.getText();
        String toolTip = type.getToolTip();

        final JRadioButton button = new JRadioButton(buttonLabel);
        button.setActionCommand(type.getActionCommand());

        if (type.getToolTip() != null) {
            button.setToolTipText(toolTip);
        }

        group.add(button);
        return button;
    }

    private static GridBagConstraints getDefaultGBC(final boolean insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        if (insets) {
            gbc.insets = new Insets(5, 5, 5, 5);
        }
        return gbc;
    }

    /**
     * Get the location of the local file credentials location.
     *
     * @return The location of the local file credentials location.
     */
    public String getFilesystemLocation() {
        return ((SettingsModelString)m_credentialFileLocation.getModel()).getStringValue();
    }

    /**
     * Set the location of the local file credentials location.
     *
     * @param location The location of the local file credentials location to set.
     */
    public void setFilesystemLocation(final String location) {
        ((SettingsModelString)m_credentialFileLocation.getModel()).setStringValue(location);
    }

    /**
     * Action listener to trigger on clear selected credentials button click.
     *
     * @param l The listener to add.
     */
    public void addClearSelectedLocationListener(final ActionListener l) {
        m_clearSelectedButton.addActionListener(l);
    }

    /**
     * Action listener to trigger on clear all credentials button click.
     *
     * @param l The listener to add.
     */
    public void addClearAllLocationListener(final ActionListener l) {
        m_clearAllButton.addActionListener(l);
    }

    /**
     * Get the selected CredentialsLocationType.
     *
     * @return The selected CredentialsLocationType.
     */
    public CredentialsLocationType getCredentialsSaveLocation() {
        return CredentialsLocationType.fromActionCommand(m_locationsButtonGroup.getSelection().getActionCommand());
    }

    /**
     * Select the radio button corresponding to the given CredentialsLocationType.
     *
     * @param location The CredentialsLocationType to select.
     */
    public void setCredentialsSaveLocation(final CredentialsLocationType location) {
        Enumeration<AbstractButton> elements = m_locationsButtonGroup.getElements();
        while (elements.hasMoreElements()) {
            final AbstractButton button = elements.nextElement();
            if (button.getActionCommand().equals(location.getActionCommand())) {
                button.doClick();
            }
        }
    }
}

/**
 * Enum giving different credential storage loytion types.
 *
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 */
enum CredentialsLocationType {
        MEMORY("Memory", "Memory (credentials kept in memory)",
            "The authentication credentials will be kept in memory; they are discarded when exiting KNIME."),
        NODE("Node", "Node (credentials saved as part of node instance)",
            "The authentication credentials will be stored in the node settings."),
        FILESYSTEM("Local File", "Local File (credentials saved in local file)",
            "The authentication credentials will be stored in the specified file.");

    private String m_toolTip;

    private String m_text;

    private String m_shortText;

    /**
     * @param shortText Short description.
     * @param text Label text to display.
     * @param toolTip Tool tip to display.
     */
    private CredentialsLocationType(final String shortText, final String text, final String toolTip) {
        m_text = text;
        m_toolTip = toolTip;
        m_shortText = shortText;
    }

    public String getText() {
        return m_text;
    }

    public String getShortText() {
        return m_shortText;
    }

    public String getActionCommand() {
        return name();
    }

    public String getToolTip() {
        return m_toolTip;
    }

    public static CredentialsLocationType fromActionCommand(final String actionCommand) {
        return Arrays.stream(CredentialsLocationType.values())//
            .filter(c -> c.name().equals(actionCommand))//
            .findFirst()//
            .orElse(MEMORY);
    }
}

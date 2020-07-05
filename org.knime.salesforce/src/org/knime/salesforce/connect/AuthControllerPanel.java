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
 *   Jun 28, 2020 (wiswedel): created
 */
package org.knime.salesforce.connect;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.UUID;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.SalesforceInteractiveAuthenticator;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.AuthType;

/**
 * The panel that allows the user to choose between OAuth interactive and username/password authentication.
 * @author Bernd Wiswedel, KNIME, Konstanz, Germany
 */
@SuppressWarnings("serial")
abstract class AuthControllerPanel extends JPanel {

    private final JRadioButton m_oauthSelectionButton;
    private final JRadioButton m_userPassSelectionButton;

    private final JPanel m_centerPanel;
    private final SalesforceInteractiveAuthenticator m_authenticator;
    private final OAuthSettingsPanel m_oauthPanel;
    private final UsernamePasswordSettingsPanel m_usernamePasswordPanel;

    AuthControllerPanel(final InstanceTypePanel instanceTypePanel) {
        super(new BorderLayout());
        ButtonGroup bg = new ButtonGroup();
        m_oauthSelectionButton = new JRadioButton("OAuth2 Interactive Authentication (via browser)");
        m_oauthSelectionButton.addItemListener(e -> onTypeChange());
        bg.add(m_oauthSelectionButton);
        m_userPassSelectionButton = new JRadioButton("Username + Password Authentication");
        bg.add(m_userPassSelectionButton);
        JPanel northPanel = new JPanel(new GridLayout(0, 1));
        northPanel.add(m_oauthSelectionButton);
        northPanel.add(m_userPassSelectionButton);
        northPanel.add(new JLabel(""));
        add(northPanel, BorderLayout.NORTH);
        m_centerPanel = new JPanel(new BorderLayout());
        add(m_centerPanel, BorderLayout.CENTER);
        add(ViewUtils.getInFlowLayout(new JLabel(" ")), BorderLayout.WEST);

        m_authenticator = new SalesforceInteractiveAuthenticator(() -> instanceTypePanel.isUseSandbox());
        m_oauthPanel = new OAuthSettingsPanel(m_authenticator);

        // Listeners to clear stored credentials
        m_oauthPanel.addClearSelectedLocationListener(
            a -> onClearedInteractiveAuthentication(m_oauthPanel.getCredentialsSaveLocation()));
        m_oauthPanel.addClearAllLocationListener(a -> onClearedAllAuthentication());

        m_usernamePasswordPanel = new UsernamePasswordSettingsPanel();

        m_oauthSelectionButton.doClick();
        m_centerPanel.add(m_oauthPanel);

    }

    void onTypeChange() {
        m_centerPanel.removeAll();
        m_centerPanel.add(m_oauthSelectionButton.isSelected() ? m_oauthPanel : m_usernamePasswordPanel);
        m_centerPanel.invalidate();
        m_centerPanel.revalidate();
        m_centerPanel.repaint();
    }

    /** Called on button click. */
    abstract void onClearedAllAuthentication();

    /** Called on button click. */
    abstract void onClearedInteractiveAuthentication(CredentialsLocationType locationType);

    void saveSettingsTo(final SalesforceConnectorNodeSettings settings) throws InvalidSettingsException {
        settings.setAuthType(m_oauthSelectionButton.isSelected() ? AuthType.Interactive : AuthType.UsernamePassword);
        SalesforceAuthentication authentication = m_authenticator.getAuthentication();
        InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().put(settings.getNodeInstanceID(), authentication);
        UUID nodeInstanceID = settings.getNodeInstanceID();
        InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().put(nodeInstanceID, authentication);
        CredentialsLocationType saveLocation = m_oauthPanel.getCredentialsSaveLocation();
        settings.setCredentialsSaveLocation(saveLocation);
        settings.setFilesystemLocation(m_oauthPanel.getFilesystemLocation());
        m_usernamePasswordPanel.saveSettingsTo(settings);
    }

    void loadSettingsFrom(final SalesforceConnectorNodeSettings settings,
        final CredentialsProvider credentialsProvider) {
        if (settings.getAuthType() == AuthType.Interactive) {
            m_oauthSelectionButton.doClick();
        } else {
            m_userPassSelectionButton.doClick();
        }
        m_oauthPanel.setCredentialsSaveLocation(settings.getCredentialsSaveLocation());
        m_oauthPanel.setFilesystemLocation(settings.getFilesystemLocation());
        m_usernamePasswordPanel.loadSettingsFrom(settings, credentialsProvider);
        m_authenticator.setAuthentication(InMemoryAuthenticationStore.getDialogToNodeExchangeInstance()
            .get(settings.getNodeInstanceID()).orElse(null));
    }


}

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
 *   Oct 4, 2019 (benjamin): created
 */
package org.knime.salesforce.connect;

import java.io.IOException;
import java.util.UUID;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.salesforce.connect.InteractiveAuthenticator.AuthenticatorState;

/**
 * Salesforce Authentication (deprecated) node dialog.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeDialog extends NodeDialogPane {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceConnectorNodeDialog.class);

    private final SalesforceConnectorNodeSettings m_settings;

    private final SalesforceInteractiveAuthenticator m_authenticator;

    private final AuthControllerPanel m_authControllerPanel;
    private final InstanceTypePanel m_instanceTypePanel;
    private final TimeoutPanel m_timeoutPanel;

    @SuppressWarnings("serial")
    public SalesforceConnectorNodeDialog() {
        m_settings = new SalesforceConnectorNodeSettings(UUID.randomUUID());
        m_authenticator = new SalesforceInteractiveAuthenticator(m_settings);

        m_instanceTypePanel = new InstanceTypePanel();
        m_authControllerPanel = new AuthControllerPanel(m_settings, m_authenticator) {
            @Override
            void onClearedAllAuthentication() {
                for (CredentialsLocationType clt : CredentialsLocationType.values()) {
                    try {
                        m_settings.clearAuthentication(clt);
                    } catch (IOException ex) {
                        String m = "Could not clear " + clt.getShortText() + " credentials. Reason: " + ex.getMessage();
                        LOGGER.error(m, ex);
                    }
                }
            }

            @Override
            void onClearedSelectedAuthentication(final CredentialsLocationType locationType) {
                try {
                    m_settings.clearAuthentication(locationType);
                } catch (IOException ex) {
                    String msg =
                        "Could not clear " + locationType.getShortText() + " credentials. Reason: " + ex.getMessage();
                    LOGGER.error(msg, ex);
                }
            }
        };
        m_timeoutPanel = new TimeoutPanel();
        addTab("Authentication", m_authControllerPanel);
        addTab("Instance Type", m_instanceTypePanel);
        addTab("Connection Settings", m_timeoutPanel);

        m_authenticator.addListener(this::onAuthenticationTriggered);
    }

    private void onAuthenticationTriggered(final AuthenticatorState state) {
        if (state == AuthenticatorState.PREPARE_AUTHENTICATION) {
            try {
                m_authControllerPanel.saveSettingsTo();
                m_instanceTypePanel.saveSettingsTo(m_settings);
                m_timeoutPanel.saveSettingsTo(m_settings);
            } catch (InvalidSettingsException ex) { // NOSONAR
                // can be ignored
            }
        }
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_authControllerPanel.saveSettingsTo();
        m_instanceTypePanel.saveSettingsTo(m_settings);
        m_timeoutPanel.saveSettingsTo(m_settings);

        m_settings.saveSettingsInDialog(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs) {
        m_settings.loadInDialog(settings);

        m_authControllerPanel.loadSettingsFrom(getCredentialsProvider());
        m_instanceTypePanel.loadSettingsFrom(m_settings);
        m_timeoutPanel.loadSettingsFrom(m_settings);
    }

    @Override
    public void onClose() {
        m_authControllerPanel.onClose();
    }

    @Override
    public void onOpen() {
        m_authControllerPanel.onOpen();
    }
}

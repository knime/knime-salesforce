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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.UUID;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.SalesforceAuthenticator;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.InstanceType;

/**
 * Dialog for the Send to Power BI node.
 *
 * @author Benjamin Wilhem, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeDialog extends NodeDialogPane {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceConnectorNodeDialog.class);

    private SalesforceConnectorNodeSettings m_settings;

    private final SalesforceAuthenticator m_authenticator;

    private OAuthSettingsPanel m_authPanel;

    private final JRadioButton m_productionInstanceChecker;

    private final JRadioButton m_testInstanceChecker;

    public SalesforceConnectorNodeDialog() {
        m_authenticator = new SalesforceAuthenticator();
        m_authPanel = new OAuthSettingsPanel(m_authenticator);
        ButtonGroup bg = new ButtonGroup();
        m_productionInstanceChecker = new JRadioButton("Use Production Instance (login.salesforce.com)");
        m_testInstanceChecker = new JRadioButton("Use Test Instance (test.salesforce.com)");
        bg.add(m_productionInstanceChecker);
        bg.add(m_testInstanceChecker);
        m_productionInstanceChecker.doClick();
        m_testInstanceChecker.setEnabled(false);
        m_productionInstanceChecker.setEnabled(false);
        addTab("Options", createOptionsPanel());
    }

    private JPanel createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // Authentication panel
        m_authPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Authentication"));
        panel.add(m_authPanel, gbc);

        // Listeners to clear stored credentials
        m_authPanel.addClearSelectedLocationListener(a -> {
            CredentialsLocationType locationType = m_authPanel.getCredentialsSaveLocation();
            try {
                m_settings.clearAuthentication(locationType);
            } catch (InvalidSettingsException ex) {
                String msg =
                    "Could not clear " + locationType.getShortText() + " credentials. Reason: " + ex.getMessage();
                LOGGER.error(msg, ex);
            }
        });

        m_authPanel.addClearAllLocationListener(a -> {
            for (CredentialsLocationType clt : CredentialsLocationType.values()) {
                try {
                    m_settings.clearAuthentication(clt);
                } catch (InvalidSettingsException ex) {
                    String msg = "Could not clear " + clt.getShortText() + " credentials. Reason: " + ex.getMessage();
                    LOGGER.error(msg, ex);
                }
            }
        });

        JPanel instanceTypePanel = new JPanel(new GridLayout(0, 1));
        instanceTypePanel
            .setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Instance Type"));
        instanceTypePanel.add(m_productionInstanceChecker);
        instanceTypePanel.add(m_testInstanceChecker);

        gbc.gridy += 1;
        gbc.weightx = 0;
        panel.add(instanceTypePanel, gbc);

        return panel;
    }


    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        SalesforceAuthentication authentication = m_authenticator.getAuthentication();
        m_settings.setAuthentication(authentication);
        UUID nodeInstanceID = m_settings.getNodeInstanceID();
        InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().put(nodeInstanceID, authentication);
        CredentialsLocationType saveLocation = m_authPanel.getCredentialsSaveLocation();
        m_settings.setCredentialsSaveLocation(saveLocation);
        m_settings.setFilesystemLocation(m_authPanel.getFilesystemLocation());
        InstanceType instanceType;
        if (m_productionInstanceChecker.isSelected()) {
            instanceType = InstanceType.ProductionInstance;
        } else {
            instanceType = InstanceType.TestInstance;
        }
        m_settings.setSalesforceInstanceType(instanceType);

        try {
            m_settings.saveSettingsTo(settings);
        } catch (IOException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs) {
        m_settings = SalesforceConnectorNodeSettings.loadInDialog(settings);
        m_authPanel.setCredentialsSaveLocation(m_settings.getCredentialsSaveLocation());
        m_authPanel.setFilesystemLocation(m_settings.getFilesystemLocation());
        m_authenticator.setAuthentication(m_settings.getAuthentication().orElse(null));
        switch (m_settings.getSalesforceInstanceType()) {
            case ProductionInstance:
                m_productionInstanceChecker.doClick();
                break;
            default:
                m_testInstanceChecker.doClick();
                break;
        }
    }

    @Override
    public void onClose() {
        if (m_settings != null) {
            InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().remove(m_settings.getNodeInstanceID());
        }
    }

}

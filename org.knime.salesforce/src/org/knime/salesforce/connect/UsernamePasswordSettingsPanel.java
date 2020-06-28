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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentAuthentication;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication.AuthenticationType;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.node.workflow.CredentialsProvider;

/**
 * Panel to enter username and password credentials.
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("serial")
final class UsernamePasswordSettingsPanel extends JPanel {

    private final DialogComponentAuthentication m_dialogComponentAuthentication;
    private final JPasswordField m_securityTokenField;

    UsernamePasswordSettingsPanel() {
        super(new BorderLayout());
        m_dialogComponentAuthentication = new DialogComponentAuthentication(
            new SettingsModelAuthentication(SalesforceConnectorNodeSettings.CFG_USERNAME_PASSWORD,
                AuthenticationType.USER_PWD),
            null, AuthenticationType.CREDENTIALS, AuthenticationType.USER_PWD);
        JPanel northPanel = new JPanel(new GridLayout(0, 1));
        northPanel.add(m_dialogComponentAuthentication.getComponentPanel());
        m_securityTokenField = new JPasswordField(30);
        northPanel.add(ViewUtils.getInFlowLayout(new JLabel("Security Token:  "), m_securityTokenField));
        add(northPanel, BorderLayout.NORTH);
    }

    void saveSettingsTo(final SalesforceConnectorNodeSettings settings) throws InvalidSettingsException {
        NodeSettings temp = new NodeSettings("temp");
        m_dialogComponentAuthentication.saveSettingsTo(temp);
        settings.getUsernamePasswortAuthenticationModel().loadSettingsFrom(temp);
        settings.setPasswordSecurityToken(new String(m_securityTokenField.getPassword()));
    }

    void loadSettingsFrom(final SalesforceConnectorNodeSettings settings,
        final CredentialsProvider credentialsProvider) {
        SettingsModelAuthentication m = settings.getUsernamePasswortAuthenticationModel();
        NodeSettings temp = new NodeSettings("temp");
        m.saveSettingsTo(temp);
        try {
            m_dialogComponentAuthentication.loadSettingsFrom(temp, new PortObjectSpec[] {}, credentialsProvider);
        } catch (NotConfigurableException ex) {
            // ignore, let the dialog open
        }
        m_securityTokenField.setText(settings.getPasswordSecurityToken());
    }

}

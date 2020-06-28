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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.AuthType;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.InstanceType;
import org.knime.salesforce.rest.SalesforceRESTUtil;

/**
 * Salesforce Connector node model.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeModel extends NodeModel {

    private SalesforceConnectorNodeSettings m_settings;
    private SalesforceAuthentication m_userNamePasswordAuthentication;

    SalesforceConnectorNodeModel() {
        super(new PortType[0], new PortType[] {SalesforceConnectionPortObject.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {configureInternal()};
    }

    private SalesforceConnectionPortObjectSpec configureInternal() throws InvalidSettingsException {
        // Check if the user is authenticated
        CheckUtils.checkSettingNotNull(m_settings, "No configuration available");
        if (m_settings.getAuthType() == AuthType.Interactive) {
            Optional<SalesforceAuthentication> auth =
                    InMemoryAuthenticationStore.getGlobalInstance().get(m_settings.getNodeInstanceID());
            CheckUtils.checkSetting(auth.isPresent(), "Not authenticated (open configuration to do so)");
            return new SalesforceConnectionPortObjectSpec(m_settings.getNodeInstanceID());
        }
        return null;
    }

    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec)
        throws Exception {
        SalesforceConnectionPortObjectSpec spec;
        if (m_settings.getAuthType() == AuthType.UsernamePassword) {
            SettingsModelAuthentication m = m_settings.getUsernamePasswortAuthenticationModel();
            m_userNamePasswordAuthentication = SalesforceRESTUtil.authenticateUsingUserAndPassword(
                m.getUserName(getCredentialsProvider()), m.getPassword(getCredentialsProvider()), m_settings
                    .getPasswordSecurityToken(),
                m_settings.getSalesforceInstanceType() == InstanceType.TestInstance);
            InMemoryAuthenticationStore.getGlobalInstance().put(m_settings.getNodeInstanceID(),
                m_userNamePasswordAuthentication);
            spec = new SalesforceConnectionPortObjectSpec(m_settings.getNodeInstanceID());
        } else {
            spec = configureInternal();
        }
        return new PortObject[] {new SalesforceConnectionPortObject(spec)};
    }


    @Override
    protected void reset() {
        if (m_settings != null && m_settings.getAuthType() == AuthType.UsernamePassword) {
            removeFromInMemoryCache();
        }
    }

    @Override
    protected void onDispose() {
        removeFromInMemoryCache();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        try {
            m_settings.saveSettingsInModel(settings);
        } catch (IOException | InvalidSettingsException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        SalesforceConnectorNodeSettings.loadInModel(settings, true);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings = SalesforceConnectorNodeSettings.loadInModel(settings, false);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        Path p = getInternalsPath(nodeInternDir);
        if (Files.isRegularFile(p)) {
            NodeSettingsRO load;
            try (InputStream in = Files.newInputStream(p)) {
                load = NodeSettings.loadFromXML(in);
                m_userNamePasswordAuthentication = SalesforceAuthentication.load(load);
                InMemoryAuthenticationStore.getGlobalInstance().put(m_settings.getNodeInstanceID(),
                    m_userNamePasswordAuthentication);
            } catch (InvalidSettingsException ex) {
                throw new IOException(ex);
            }
        }
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        if (m_userNamePasswordAuthentication != null) {
            NodeSettings save = new NodeSettings("authentication");
            m_userNamePasswordAuthentication.save(save);
            try (OutputStream out = Files.newOutputStream(getInternalsPath(nodeInternDir))) {
                save.saveToXML(out);
            }
        }
    }

    private static Path getInternalsPath(final File nodeInternDir) {
        return nodeInternDir.toPath().resolve("internals.xml");
    }

    private void removeFromInMemoryCache() {
        if (m_settings != null) {
            InMemoryAuthenticationStore.getGlobalInstance().remove(m_settings.getNodeInstanceID());
        }
    }

}

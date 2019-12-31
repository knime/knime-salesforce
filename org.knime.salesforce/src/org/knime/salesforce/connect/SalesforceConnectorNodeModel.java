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

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;

/**
 * Salesforce Authentication node
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeModel extends NodeModel {

    private SalesforceConnectorNodeSettings m_settings;

    SalesforceConnectorNodeModel() {
        super(new PortType[0], new PortType[] {SalesforceConnectionPortObject.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {configureInternal()};
    }

    private SalesforceConnectionPortObjectSpec configureInternal() throws InvalidSettingsException {
        // Check if the user is authenticated
        SalesforceAuthentication authentication = CheckUtils.checkSettingNotNull(//
            m_settings, "No configuration available").getAuthentication().orElse(null);
        CheckUtils.checkSettingNotNull(authentication, "Not authenticated. Authenticate in the Node Configuration.");
        return new SalesforceConnectionPortObjectSpec(m_settings.getNodeInstanceID());
    }

    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec)
        throws Exception {
        SalesforceConnectionPortObjectSpec spec = configureInternal();
//        pushFlowVariable("instance-url", VariableType.StringType.INSTANCE, spec.getAuthentication().getInstanceURLString());
//        pushFlowVariable("access-token", VariableType.StringType.INSTANCE, "Bearer " + spec.getAuthentication().getAccessToken());
//        pushFlowVariableString("instance-url", spec.getAuthentication().getInstanceURLString());
//        pushFlowVariableString("access-token", "Bearer " + spec.getAuthentication().getAccessToken());
        return new PortObject[] {new SalesforceConnectionPortObject(spec)};
    }


    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        try {
            m_settings.saveSettingsTo(settings);
        } catch (IOException | InvalidSettingsException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        try {
            SalesforceConnectorNodeSettings.loadInModel(settings);
        } catch (IOException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        try {
            m_settings = SalesforceConnectorNodeSettings.loadInModel(settings);
            // this will be non-null if configured from the config dialog
            SalesforceAuthentication auth = m_settings.getAuthentication() // saved in file or part of node settings
                    .orElse(InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().get(
                        m_settings.getNodeInstanceID()) // save 'in memory'
                        .orElse(null)); // saved 'in memory' and app restarted
            m_settings.setAuthentication(auth);
            InMemoryAuthenticationStore.getGlobalInstance().put(m_settings.getNodeInstanceID(), auth);
        } catch (IOException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // Nothing to do

    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // Nothing to do
    }

    @Override
    protected void reset() {
    }

    @Override
    protected void onDispose() {
        removeFromInMemoryCache();
    }

    private void removeFromInMemoryCache() {
        if (m_settings != null) {
            InMemoryAuthenticationStore.getGlobalInstance().remove(m_settings.getNodeInstanceID());
        }
    }

}

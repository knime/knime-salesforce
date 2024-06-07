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
 *   Jul 5, 2020 (wiswedel): created
 */
package org.knime.salesforce.extractor;

import java.io.File;
import java.io.IOException;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;

/**
 * Model of the Salesforce Connection Extractor node.
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceAuthExtractNodeModel extends NodeModel {

    SalesforceAuthExtractNodeModel() {
        super(new PortType[] {SalesforceConnectionPortObject.TYPE}, new PortType[] {FlowVariablePortObject.TYPE});
    }

    private final SettingsModelBoolean m_refreshTokenOnExecuteModel = createRefreshTokenOnExecuteModel();

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final var inSpec = (SalesforceConnectionPortObjectSpec)inSpecs[0];
        inSpec.getCredential(SalesforceAccessTokenCredential.class)//
            .ifPresent(this::pushDummyFlowVariables);

        return new PortObjectSpec[] {FlowVariablePortObjectSpec.INSTANCE};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec) throws Exception {
        final var inSpec = (SalesforceConnectionPortObjectSpec)inData[0].getSpec();
        final var credential = inSpec.resolveCredential(SalesforceAccessTokenCredential.class);
        pushFlowVariables(credential, m_refreshTokenOnExecuteModel.getBooleanValue());
        return new PortObject[] {FlowVariablePortObject.INSTANCE};
    }

    private void pushDummyFlowVariables(final SalesforceAccessTokenCredential salesforceAccessTokenCredential) {
        pushFlowVariable("access-token", StringType.INSTANCE, "Bearer dummy-value");
        pushFlowVariable("salesforce-instance-url", StringType.INSTANCE,
            salesforceAccessTokenCredential.getSalesforceInstanceUrl().toString());
    }

    private void pushFlowVariables(final SalesforceAccessTokenCredential salesforceAccessTokenCredential,
        final boolean forceRefresh) throws IOException {

        pushFlowVariable("access-token", StringType.INSTANCE,
            "Bearer " + salesforceAccessTokenCredential.getAccessToken(forceRefresh));
        pushFlowVariable("salesforce-instance-url", StringType.INSTANCE,
            salesforceAccessTokenCredential.getSalesforceInstanceUrl().toString());
    }

    @Override
    protected void reset() {
        // no internals
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_refreshTokenOnExecuteModel.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_refreshTokenOnExecuteModel.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_refreshTokenOnExecuteModel.loadSettingsFrom(settings);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    static SettingsModelBoolean createRefreshTokenOnExecuteModel() {
        return new SettingsModelBoolean("refreshTokenOnExecute", true);
    }
}

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
 *   Dec 28, 2019 (wiswedel): created
 */
package org.knime.salesforce.soql;

import java.io.File;
import java.io.IOException;

import org.knime.base.util.flowvariable.FlowVariableProvider;
import org.knime.core.node.BufferedDataTable;
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
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.soql.AbstractSOQLExecutor;
import org.knime.salesforce.rest.soql.RawOutputSOQLExecutor;
import org.knime.salesforce.rest.soql.RecordsOutputSOQLExecutor;

/**
 * Model of 'Salesforce SOQL' node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceSOQLNodeModel extends NodeModel implements FlowVariableProvider {

    private SalesforceSOQLNodeSettings m_settings = new SalesforceSOQLNodeSettings();

    SalesforceSOQLNodeModel() {
        super(new PortType[] {SalesforceConnectionPortObject.TYPE}, new PortType[] {BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final var inSpec = (SalesforceConnectionPortObjectSpec)inSpecs[0];
        final var outSpec = createSoqlExecutor(inSpec).createOutputSpec();
        return outSpec.map(s -> new PortObjectSpec[]{s}).orElse(null);
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final var inSpec = (SalesforceConnectionPortObjectSpec)inObjects[0].getSpec();
        final var executor = createSoqlExecutor(inSpec);
        return new PortObject[]{executor.execute(exec)};
    }

    private AbstractSOQLExecutor createSoqlExecutor(final SalesforceConnectionPortObjectSpec inSpec)
        throws InvalidSettingsException {

        try {
            final var credential = inSpec.resolveCredential(SalesforceAccessTokenCredential.class);
            final var timeouts = inSpec.getTimeouts();

            return switch (m_settings.getOutputRepresentation()) {
                case RAW -> new RawOutputSOQLExecutor(credential, timeouts, m_settings, this);
                case RECORDS -> new RecordsOutputSOQLExecutor(credential, timeouts, m_settings, this);
                default -> throw new IllegalStateException(
                    "Type not implementation: " + m_settings.getOutputRepresentation());
            };
        } catch (NoSuchCredentialException e) {
            throw new InvalidSettingsException(e.getMessage(), e);
        }
    }

    @Override
    protected void reset() {
        // no internals
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new SalesforceSOQLNodeSettings().loadSettingsInModel(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings = new SalesforceSOQLNodeSettings().loadSettingsInModel(settings);
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


}

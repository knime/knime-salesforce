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

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.knime.base.util.flowvariable.FlowVariableProvider;
import org.knime.base.util.flowvariable.FlowVariableResolver;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.rest.soql.AbstractSOQLExecutor;

/**
 * The configuration settings of the 'Salesforce SOQL' node.
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceSOQLNodeSettings {

    /** Determines how the data is represented in a KNIME table. Currently both formats are JSON with a 'table' option
     * potentially coming in the future.
     * @see AbstractSOQLExecutor
     */
    public enum SOQLOutputRepresentation {
        /** Raw JSON -- the JSON data unmodified in seperate rows (they return data in chunks if it's large). */
        RAW("Raw JSON", "The raw data returned by the Salesforce REST API"),
        /** The JSON split by 'records' (an array that is contained in the result). */
        RECORDS("Records JSON", "The records of the SOQL as JSON, split into individual rows.");
        // Table("Table", "The data returned by the Salesforce REST API, parsed and returned as individual columns");

        private final String m_label;
        private final String m_description;
        /**
         * @param label
         * @param description
         */
        SOQLOutputRepresentation(final String label, final String description) {
            m_label = label;
            m_description = description;
        }

        String getLabel() {
            return m_label;
        }

        String getDescription() {
            return m_description;
        }

        static Optional<SOQLOutputRepresentation>from(final String name) {
            return Arrays.stream(values()).filter(s -> s.name().equals(name)).findFirst();
        }

    }
    private String m_soql = "Select Id, Name from Account LIMIT 10";
    private String m_outputColumnName = "json";
    private SOQLOutputRepresentation m_outputRepresentation = SOQLOutputRepresentation.RAW;
    private boolean m_isOutputACounter;
    private boolean m_retrieveDeletedAndArchived;

    /**
     * @return the soql
     */
    public String getSOQL() {
        return m_soql;
    }

    /**
     * @param flowVarProvider
     * @return The final SOQL
     * @throws InvalidSettingsException if variables can't be found
     */
    public String getSOQLWithFlowVarsReplaced(final FlowVariableProvider flowVarProvider)
        throws InvalidSettingsException {
        try {
            return FlowVariableResolver.parse(m_soql, flowVarProvider);
        } catch (NoSuchElementException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    /**
     * @param soql the soql to set
     */
    void setSOQL(final String soql) {
        m_soql = soql;
    }

    /**
     * @return the outputColumnName
     */
    public String getOutputColumnName() {
        return m_outputColumnName;
    }

    /**
     * @param outputColumnName the outputColumnName to set
     */
    void setOutputColumnName(final String outputColumnName) {
        m_outputColumnName = outputColumnName;
    }

    /**
     * @return the isOutputACounter
     */
    public boolean isOutputAsCount() {
        return m_isOutputACounter;
    }

    /**
     * @param isOutputACounter the isOutputACounter to set
     */
    void setOutputAsCount(final boolean isOutputACounter) {
        m_isOutputACounter = isOutputACounter;
    }

    /**
     * @return the outputRepresentation
     */
    SOQLOutputRepresentation getOutputRepresentation() {
        return m_outputRepresentation;
    }

    /**
     * @param outputRepresentation the outputRepresentation to set
     * @throws InvalidSettingsException If argument is null.
     */
    void setOutputRepresentation(final SOQLOutputRepresentation outputRepresentation) throws InvalidSettingsException {
        m_outputRepresentation = CheckUtils.checkSettingNotNull(outputRepresentation, "Must not be null");
    }

    /**
     * @return the retrieveDeletedAndArchived flag
     */
    public boolean isRetrieveDeletedAndArchived() {
        return m_retrieveDeletedAndArchived;
    }

    /**
     * @param retrieveDeletedAndArchived the retrieveDeletedAndArchived to set
     */
    void setRetrieveDeletedAndArchived(final boolean retrieveDeletedAndArchived) {
        m_retrieveDeletedAndArchived = retrieveDeletedAndArchived;
    }

    void saveSettingsTo(final NodeSettingsWO settings) {
        if (StringUtils.isNotEmpty(m_soql)) {
            settings.addString("SOQL", m_soql);
            settings.addString("outputColumnName", m_outputColumnName);
            settings.addString("outputRepresentation", m_outputRepresentation.name());
            settings.addBoolean("outputAsCount", m_isOutputACounter);
            settings.addBoolean("retrieveDeletedAndArchived", m_retrieveDeletedAndArchived);
        }
    }

    SalesforceSOQLNodeSettings loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_soql = settings.getString("SOQL");
        CheckUtils.checkSetting(StringUtil.isNotBlank(m_soql), "SOQL string must not be null or blank");
        m_outputColumnName = settings.getString("outputColumnName");
        CheckUtils.checkSetting(StringUtil.isNotBlank(m_outputColumnName), "column name must not be null or blank");
        String outputRepresenationS = settings.getString("outputRepresentation");
        m_outputRepresentation = SOQLOutputRepresentation.from(outputRepresenationS)
            .orElseThrow(() -> new InvalidSettingsException("Invalid Output Represenation: " + outputRepresenationS));
        m_isOutputACounter = settings.getBoolean("outputAsCount");
        m_retrieveDeletedAndArchived = settings.getBoolean("retrieveDeletedAndArchived", false); // new in 5.7, AP-24773
        return this;
    }

    SalesforceSOQLNodeSettings loadSettingsInDialog(final NodeSettingsRO settings) {
        m_soql = settings.getString("SOQL", "");
        m_outputColumnName = settings.getString("outputColumnName", "json");
        String outputRepresenationS = settings.getString("outputRepresentation", null);
        m_outputRepresentation =
            SOQLOutputRepresentation.from(outputRepresenationS).orElse(SOQLOutputRepresentation.RAW);
        m_isOutputACounter = settings.getBoolean("outputAsCount", false);
        m_retrieveDeletedAndArchived = settings.getBoolean("retrieveDeletedAndArchived", false);
        return this;
    }
}

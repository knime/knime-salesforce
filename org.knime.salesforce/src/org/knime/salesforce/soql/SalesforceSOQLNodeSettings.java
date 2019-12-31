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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author wiswedel
 */
public final class SalesforceSOQLNodeSettings {

    private String m_soql = "Select Id, Name from Account LIMIT 10";
    private String m_outputColumnName = "json";

    /**
     * @return the soql
     */
    String getSOQL() {
        return m_soql;
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
    String getOutputColumnName() {
        return m_outputColumnName;
    }

    /**
     * @param outputColumnName the outputColumnName to set
     */
    void setOutputColumnName(final String outputColumnName) {
        m_outputColumnName = outputColumnName;
    }

    void saveSettingsTo(final NodeSettingsWO settings) {
        if (StringUtils.isNotEmpty(m_soql)) {
            settings.addString("SOQL", m_soql);
            settings.addString("outputColumnName", m_outputColumnName);
        }
    }

    SalesforceSOQLNodeSettings loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_soql = settings.getString("SOQL");
        CheckUtils.checkSetting(StringUtil.isNotBlank(m_soql), "SOQL string must not be null or blank");
        m_outputColumnName = settings.getString("outputColumnName");
        CheckUtils.checkSetting(StringUtil.isNotBlank(m_outputColumnName), "column name must not be null or blank");
        return this;
    }

    SalesforceSOQLNodeSettings loadSettingsInDialog(final NodeSettingsRO settings) {
        m_soql = settings.getString("SOQL", "");
        m_outputColumnName = settings.getString("outputColumnName", "json");
        return this;
    }

}

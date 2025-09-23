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
 *   Jul 26, 2020 (wiswedel): created
 */
package org.knime.salesforce.simplequery;

import java.util.Objects;
import java.util.Optional;

import org.knime.core.node.NodeLogger;
import org.knime.salesforce.rest.gsonbindings.fields.Field;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

final class SalesforceField {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceField.class);

    private final String m_name;
    private final String m_label;
    private final SalesforceFieldType m_type;

    @JsonCreator
    SalesforceField(@JsonProperty("name") final String name, //
        @JsonProperty("label") final String label, //
        @JsonProperty("type") final SalesforceFieldType type) {
        m_name = name;
        m_label = label;
        m_type = type;
    }

    @JsonProperty("name")
    String getName() {
        return m_name;
    }

    @JsonProperty("label")
    String getLabel() {
        return m_label;
    }

    @JsonProperty("type")
    SalesforceFieldType getType() {
        return m_type;
    }

    static Optional<SalesforceField> fromField(final Field field) {
        Optional<SalesforceFieldType> typeOpt = SalesforceFieldType.fromIdentifierInSalesforce(field.getType());
        if (typeOpt.isPresent()) {
            return Optional.of(new SalesforceField(field.getName(), field.getLabel(), typeOpt.get()));
        } else {
            LOGGER.debugWithFormat("Field \"%s\" has an unsupported type (\"%s\") - skipping", field.getName(),
                field.getType());
            return Optional.empty();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SalesforceField other = (SalesforceField)obj;
        return Objects.equals(m_name, other.m_name);
    }

    @Override
    public String toString() {
        return String.format("\"%s\" (%s)", m_name, m_type);
    }

}
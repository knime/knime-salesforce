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
 *   Jul 25, 2020 (wiswedel): created
 */
package org.knime.salesforce.simplequery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;

/**
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceSimpleQueryNodeSettings {

    /** Column names in output and labels in the UI are shown as either technical names or labels. */
    enum DisplayName {
            @Label("Labels")
            Label("Labels"),

            @Label("Technical Names")
            TechnialName("Technical Names"); // Note: can't fix the typo - part of node configuration

        private final String m_text;

        DisplayName(final String text) {
            m_text = text;
        }

        @Override
        public String toString() {
            return m_text;
        }

        Function<SalesforceField, String> nameFunction() {
            return this == Label ? SalesforceField::getLabel : SalesforceField::getName;
        }

        Function<SObject, String> sObjectNameFunction() {
            return this == TechnialName ? SObject::getName : SObject::getLabel;
        }

        static final Optional<DisplayName> of(final String s) {
            return Arrays.stream(values()).filter(d -> Objects.equals(d.name(), s)).findFirst();
        }
    }

    static final String CFG_OBJECT_NAME = "objectName";
    static final String CFG_FIELDS = "fields";
    private static final String CFG_FIELD_NAME = "fieldName";
    private static final String CFG_FIELD_LABEL = "fieldLabel";
    private static final String CFG_FIELD_TYPE = "fieldType";
    static final String CFG_WHERE_CLAUSE = "where";
    static final String CFG_LIMIT_CLAUSE = "limit";
    static final String CFG_DISPLAY_TYPE = "display";
    static final String CFG_RETRIEVE_DELETED_ARCHIVED = "retrieveDeletedArchived";

    private String m_objectName;
    private SalesforceField[] m_objectFields = new SalesforceField[0];
    private Optional<String> m_whereClause;
    private OptionalInt m_limit;
    private DisplayName m_displayName = DisplayName.Label;
    private boolean m_retrieveDeletedAndArchived;

    String getObjectName() {
        return m_objectName;
    }

    void setObjectName(final String objectName) {
        m_objectName = objectName;
    }

    SalesforceField[] getObjectFields() {
        return m_objectFields;
    }

    void setObjectFields(final SalesforceField[] objectFields) {
        m_objectFields = objectFields;
    }

    Optional<String> getWhereClause() {
        return m_whereClause;
    }

    void setWhereClause(final String whereClause) {
        m_whereClause = Optional.ofNullable(StringUtils.trimToNull(whereClause));
    }

    OptionalInt getLimit() {
        return m_limit;
    }

    void setLimit(final int limitOrNegative) {
        m_limit = limitOrNegative < 0 ? OptionalInt.empty() : OptionalInt.of(limitOrNegative);
    }

    DisplayName getDisplayName() {
        return m_displayName;
    }

    void setDisplayName(final DisplayName displayName) {
        m_displayName = Objects.requireNonNull(displayName);
    }

    boolean isRetrieveDeletedAndArchived() {
        return m_retrieveDeletedAndArchived;
    }

    void setRetrieveDeletedAndArchived(final boolean value) {
        m_retrieveDeletedAndArchived = value;
    }

    SalesforceSimpleQueryNodeSettings loadInDialog(final NodeSettingsRO settings) {
        m_objectName = settings.getString(CFG_OBJECT_NAME, null);
        NodeSettingsRO fields;
        try {
            fields = settings.getNodeSettings(CFG_FIELDS);
        } catch (InvalidSettingsException ex) {
            fields = new NodeSettings("empty");
        }
        List<SalesforceField> fieldList = new ArrayList<>();
        for (String key : fields.keySet()) {
            try {
                fieldList.add(readSalesforceFieldFromSettings(fields, key));
            } catch (InvalidSettingsException ex) {
                // ignore in dialog code
            }
        }
        m_objectFields = fieldList.toArray(new SalesforceField[0]);
        setWhereClause(settings.getString(CFG_WHERE_CLAUSE, null));
        setLimit(settings.getInt(CFG_LIMIT_CLAUSE, -1));
        setDisplayName(DisplayName.of(settings.getString(CFG_DISPLAY_TYPE, null)).orElse(DisplayName.Label));
        setRetrieveDeletedAndArchived(settings.getBoolean(CFG_RETRIEVE_DELETED_ARCHIVED, false));
        return this;
    }

    SalesforceSimpleQueryNodeSettings loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_objectName =
            CheckUtils.checkSettingNotNull(settings.getString(CFG_OBJECT_NAME), "object name must not be null");
        NodeSettingsRO fields = settings.getNodeSettings(CFG_FIELDS);
        List<SalesforceField> fieldList = new ArrayList<>();
        for (String key : fields.keySet()) {
            fieldList.add(readSalesforceFieldFromSettings(fields, key));
        }
        CheckUtils.checkSetting(!fieldList.isEmpty(), "Empty field list -- at least one needs to be selected");
        m_objectFields = fieldList.toArray(new SalesforceField[0]);
        setWhereClause(settings.getString(CFG_WHERE_CLAUSE));
        setLimit(settings.getInt(CFG_LIMIT_CLAUSE));
        setDisplayName(DisplayName.of(settings.getString(CFG_DISPLAY_TYPE, null))
            .orElseThrow(() -> new InvalidSettingsException("Na valid display option")));
        // added in 5.7
        m_retrieveDeletedAndArchived = settings.getBoolean(CFG_RETRIEVE_DELETED_ARCHIVED, false);
        return this;
    }

    void save(final NodeSettingsWO settings) {
        settings.addString(CFG_OBJECT_NAME, m_objectName);
        NodeSettingsWO fields = settings.addNodeSettings(CFG_FIELDS);
        for (int i = 0; i < m_objectFields.length; i++) {
            NodeSettingsWO field = fields.addNodeSettings("field-" + i);
            writeSalesforceFieldToSettings(field, m_objectFields[i]);
        }
        settings.addString(CFG_WHERE_CLAUSE, m_whereClause.orElse(null));
        settings.addInt(CFG_LIMIT_CLAUSE, m_limit.orElse(-1));
        settings.addString(CFG_DISPLAY_TYPE, m_displayName.name());
        settings.addBoolean(CFG_RETRIEVE_DELETED_ARCHIVED, m_retrieveDeletedAndArchived);
    }

    static SalesforceField readSalesforceFieldFromSettings(final NodeSettingsRO fields, final String key)
        throws InvalidSettingsException {
        NodeSettingsRO field = fields.getNodeSettings(key);
        String fieldName = field.getString(CFG_FIELD_NAME);
        String fieldLabel = field.getString(CFG_FIELD_LABEL);
        String type = field.getString(CFG_FIELD_TYPE);
        return new SalesforceField(fieldName, fieldLabel, SalesforceFieldType.readType(type));
    }

    static void writeSalesforceFieldToSettings(final NodeSettingsWO settings, final SalesforceField field) {
        settings.addString(CFG_FIELD_NAME, field.getName());
        settings.addString(CFG_FIELD_LABEL, field.getLabel());
        settings.addString(CFG_FIELD_TYPE, field.getType().name());
    }

    @Override
    public String toString() {
        return String.format("Object: \"%s\", Fields: [%s]", m_objectName,
            Arrays.stream(m_objectFields).map(f -> "\"" + f.getName() + "\"").collect(Collectors.joining(", ")));
    }
}

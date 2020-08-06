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

import java.awt.Component;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.knime.core.node.util.filter.NameFilterConfiguration;
import org.knime.core.node.util.filter.NameFilterConfiguration.EnforceOption;
import org.knime.core.node.util.filter.NameFilterPanel;

/**
 *
 * @author wiswedel
 */
final class SalesforceFieldFilterPanel extends NameFilterPanel<SalesforceField> {

    private static final SalesforceField INVALID_FIELD =
        new SalesforceField("invalid_field", "Invalid Field", SalesforceFieldType.STRING);

    private SalesforceField[] m_fields;

    private boolean m_isUseLabels;

    /**
     *
     */
    SalesforceFieldFilterPanel() {
        super(true);
    }

    @Deprecated
    @Override
    protected ListCellRenderer<?> getListCellRenderer() {
        return new SalesforceFieldListCellRenderer();
    }

    @Override
    protected TableCellRenderer getTableCellRenderer() {
        return new SalesforceFieldTableCellRenderer();
    }

    @Override
    protected String getEntryDescription() {
        return "fields";
    }

    @Override
    protected SalesforceField getTforName(final String name) {
        if (m_fields != null) {
            return Arrays.stream(m_fields).filter(f -> f.getName().equals(name)).findFirst().orElse(INVALID_FIELD);
        }
        return INVALID_FIELD;
    }

    @Override
    protected String getNameForT(final SalesforceField t) {
        // theoretically this is risky as #getTforName and this method are no longer in sync
        // practically it's not a big deal as this method is not used to determine the list of includes

        // switch between labels and names in order to correctly search fields in the UI
        return m_isUseLabels ? t.getLabel() : t.getName();
    }

    void loadConfiguration(final SalesforceField[] allFields, final SalesforceField[] includes) {
        m_fields = allFields;
        Set<String> includeLookup = Arrays.stream(includes).map(SalesforceField::getName).collect(Collectors.toSet());
        Set<String> trueIncludeSet = new LinkedHashSet<>();
        Set<String> trueExcludeSet = new LinkedHashSet<>();
        for (SalesforceField f : allFields) {
            if (includeLookup.contains(f.getName())) {
                trueIncludeSet.add(f.getName());
            } else {
                trueExcludeSet.add(f.getName());
            }
        }
        NameFilterConfiguration config = new NameFilterConfiguration("root", 0);
        config.loadDefaults(trueIncludeSet.toArray(new String[0]), trueExcludeSet.toArray(new String[0]),
            EnforceOption.EnforceInclusion);
        clearSearchFields();
        super.loadConfiguration(config, Arrays.stream(allFields).map(SalesforceField::getName).toArray(String[]::new));
    }

    void setUseLabelsAsDisplay(final boolean isUseLabels) {
        m_isUseLabels = isUseLabels;
        invalidate();
        revalidate();
        repaint();
    }

    @SuppressWarnings("serial")
    private final class SalesforceFieldTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        protected void setValue(final Object value) {
            Object val = value;
            Icon icon = null;
            String toolTipText = null;
            if (value instanceof SalesforceField) {
                SalesforceField sfValue = (SalesforceField)value;
                val =  m_isUseLabels ? sfValue.getLabel() : sfValue.getName();
                toolTipText = m_isUseLabels ? sfValue.getName() : sfValue.getLabel();
                icon = sfValue.getType().getKNIMEType().getIcon();
            }
            setIcon(icon);
            setToolTipText(toolTipText);
            super.setValue(val);
        }
    }

    @SuppressWarnings("serial")
    private static final class SalesforceFieldListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
            final boolean isSelected, final boolean cellHasFocus) {
            Object val = value;
            Icon icon = null;
            if (value instanceof SalesforceField) {
                val = ((SalesforceField)value).getName();
                icon = ((SalesforceField)value).getType().getKNIMEType().getIcon();
            }
            setIcon(icon);
            return super.getListCellRendererComponent(list, val, index, isSelected, cellHasFocus);
        }
    }

}

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

import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FAILED_CONTENT;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FAILED_FIELD;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FETCHING_CONTENT;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FETCHING_FIELD;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.StringHistoryPanel;
import org.knime.core.node.util.ViewUtils;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;
import org.knime.salesforce.simplequery.SalesforceSimpleQueryNodeSettings.DisplayName;
import org.knime.salesforce.soql.SalesforceObjectSchemaCache;

/**
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceSimpleQueryNodeDialogPane extends NodeDialogPane {

    private final JComboBox<SObject> m_objectCombo;
    private final SalesforceFieldFilterPanel m_salesforceFieldFilterPanel;
    private final StringHistoryPanel m_wherePanel;
    private final JCheckBox m_limitChecker;
    private final JSpinner m_limitSpinner;
    private final JRadioButton m_useTechNamesInRendererButton;
    private final JRadioButton m_useLabelsInRendererButton;

    private final SalesforceObjectSchemaCache m_cache;
    private boolean m_isCurrentlyLoading;

    private SalesforceConnectionPortObjectSpec m_portSpec;

    private final Map<String, SalesforceField[]> m_preferredSelectedFieldsPerObjectMap;

    @SuppressWarnings("serial")
    SalesforceSimpleQueryNodeDialogPane() {
        m_cache = new SalesforceObjectSchemaCache();
        m_preferredSelectedFieldsPerObjectMap = new HashMap<>();

        m_salesforceFieldFilterPanel = new SalesforceFieldFilterPanel();

        m_objectCombo = new JComboBox<>(new DefaultComboBoxModel<>());
        m_objectCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                final boolean isSelected, final boolean cellHasFocus) {
                String toolTipText = null;
                String text = Objects.toString(value);
                if (value instanceof SObject) {
                    SObject sValue = (SObject)value;
                    text = isUseLabelsToDisplay() ? sValue.getLabel() : sValue.getName();
                    toolTipText = isUseLabelsToDisplay() ? sValue.getName() : sValue.getLabel();
                }
                setToolTipText(toolTipText);
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        // Objects can have very long names and labels, so make it wide...
        String representativeWidthLabel = StringUtils.repeat('x', 150);
        m_objectCombo.setPrototypeDisplayValue(SObject.of(representativeWidthLabel, representativeWidthLabel));
        m_objectCombo.addItemListener(e -> {
            if (m_isCurrentlyLoading) {
                return;
            }
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onNewSalesforceObjectSelected((SObject)m_objectCombo.getSelectedItem());
            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                SObject item = (SObject)e.getItem();
                if (m_salesforceFieldFilterPanel.isEnabled()) {
                    m_preferredSelectedFieldsPerObjectMap.put(item.getName(),
                        m_salesforceFieldFilterPanel.getIncludeList().toArray(new SalesforceField[0]));
                }
            }
        });

        m_wherePanel = new StringHistoryPanel("salesforce_query_where");

        m_limitChecker = new JCheckBox("LIMIT result set (rows)");
        m_limitSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 10));
        m_limitChecker.addItemListener(e -> m_limitSpinner.setEnabled(m_limitChecker.isSelected()));
        m_limitChecker.doClick();

        m_useTechNamesInRendererButton = new JRadioButton("Technical Names");
        m_useLabelsInRendererButton = new JRadioButton("Labels");
        ButtonGroup bg = new ButtonGroup();
        bg.add(m_useLabelsInRendererButton);
        bg.add(m_useTechNamesInRendererButton);
        ItemListener listener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onDisplayOptionChanged();
            }
        };
        m_useTechNamesInRendererButton.addItemListener(listener);
        m_useLabelsInRendererButton.addItemListener(listener);
        m_useLabelsInRendererButton.doClick();

        addTab("Settings", createPanel());
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("  Names based on"), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        panel.add(ViewUtils.getInFlowLayout(FlowLayout.LEADING, m_useLabelsInRendererButton, m_useTechNamesInRendererButton), gbc);

        gbc.gridy += 1;
        gbc.gridx = 0;
        panel.add(new JLabel("  Salesforce Object (Table): "), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        panel.add(m_objectCombo, gbc);

        gbc.gridy += 1;
        gbc.gridx = 0;
        gbc.weighty = 1.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        m_salesforceFieldFilterPanel.setBorder(
            BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "Selected Fields (Columns)"));
        panel.add(m_salesforceFieldFilterPanel, gbc);

        gbc.gridy += 1;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("  WHERE clause (optional)"), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        panel.add(m_wherePanel, gbc);

        gbc.gridy += 1;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(m_limitChecker, gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        panel.add(m_limitSpinner, gbc);
        return panel;
    }

    private void onDisplayOptionChanged() {
        m_objectCombo.repaint();
        m_salesforceFieldFilterPanel.setUseLabelsAsDisplay(isUseLabelsToDisplay());
    }

    private boolean isUseLabelsToDisplay() {
        return m_useLabelsInRendererButton.isSelected();
    }

    /**
     * @param selected
     */
    private void onNewSalesforceObjectSelected(final SObject selected) {
        if (!Arrays.asList(FAILED_CONTENT, FETCHING_CONTENT).contains(selected)) {
            m_cache.cancelFetchFieldsSwingWorker();
            Field[] fields = m_cache.getsObjectFieldCache().get(selected);
            boolean enabled;
            if (fields != null) {
                enabled = !areFieldsFailedOrFetching(fields);
            } else {
                enabled = false;
                if (m_portSpec != null && m_portSpec.isPresent()) {
                    final var cred = m_portSpec.getCredential(SalesforceAccessTokenCredential.class).get(); // NOSONAR
                    m_cache.executeNewFieldsSwingWorker(cred, m_portSpec.getTimeouts(), selected,
                        this::onNewSalesforceObjectSelected);
                }
                fields = new Field[] {FETCHING_FIELD};
            }
            m_salesforceFieldFilterPanel.setEnabled(enabled);
            SalesforceField[] allFields = Arrays.stream(fields) //
                    .map(SalesforceField::fromField) //
                    .filter(Optional::isPresent) //
                    .map(Optional::get) //
                    .toArray(SalesforceField[]::new);
            SalesforceField[] defIncludes =
                m_preferredSelectedFieldsPerObjectMap.getOrDefault(selected.getName(), new SalesforceField[0]);
            m_salesforceFieldFilterPanel.loadConfiguration(allFields, defIncludes);
        }
    }

    private static boolean areFieldsFailedOrFetching(final Field[] fields) {
        return Arrays.stream(fields).anyMatch(f -> f.equals(FETCHING_FIELD) || f.equals(FAILED_FIELD));
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs) {
        m_wherePanel.updateHistory();
        m_portSpec = (SalesforceConnectionPortObjectSpec)specs[0];

        SalesforceSimpleQueryNodeSettings ssSetting = new SalesforceSimpleQueryNodeSettings().loadInDialog(settings);

        m_isCurrentlyLoading = true;
        try {
            DefaultComboBoxModel<SObject> model = (DefaultComboBoxModel<SObject>)m_objectCombo.getModel();
            model.removeAllElements();
            String selectedObject = ssSetting.getObjectName();
            SalesforceField[] selectedFields = ssSetting.getObjectFields();

            if (m_portSpec != null && m_portSpec.isPresent()) {
                model.addElement(FETCHING_CONTENT);
                m_objectCombo.setEnabled(false);

                m_preferredSelectedFieldsPerObjectMap.put(selectedObject, selectedFields);
                m_salesforceFieldFilterPanel.setEnabled(false);

                final var cred = m_portSpec.getCredential(SalesforceAccessTokenCredential.class).get(); // NOSONAR
                m_cache.executeNewSObjectsSwingWorker(cred, m_portSpec.getTimeouts(), () -> {
                    model.removeAllElements();
                    Set<SObject> allSObjects = m_cache.getsObjectFieldCache().keySet();
                    allSObjects.stream().filter(SObject::isQueryable).sorted().forEach(model::addElement);
                    allSObjects.stream().filter(s -> s.getName().equals(selectedObject)).findFirst()
                    .ifPresent(m_objectCombo::setSelectedItem);
                    m_objectCombo.setEnabled(!allSObjects.contains(FAILED_CONTENT));
                });
            } else {
                m_objectCombo.setEnabled(false);
                m_salesforceFieldFilterPanel.setEnabled(false);
                model.addElement(SObject.of(selectedObject, selectedObject));
                m_salesforceFieldFilterPanel.loadConfiguration(selectedFields, selectedFields);
            }
            if (ssSetting.getDisplayName() == DisplayName.Label) {
                m_useLabelsInRendererButton.doClick();
            } else {
                m_useTechNamesInRendererButton.doClick();
            }
        } finally {
            m_isCurrentlyLoading = false;
        }

        m_wherePanel.setSelectedString(ssSetting.getWhereClause().orElse(""));

        OptionalInt limit = ssSetting.getLimit();
        if (limit.isPresent() != m_limitChecker.isSelected()) {
            m_limitChecker.doClick();
        }
        m_limitSpinner.setValue(limit.orElse(100));
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        SalesforceSimpleQueryNodeSettings ssSettings = new SalesforceSimpleQueryNodeSettings();
        ssSettings.setObjectName(((SObject)m_objectCombo.getSelectedItem()).getName());
        ssSettings.setObjectFields(m_salesforceFieldFilterPanel.getIncludeList().toArray(new SalesforceField[0]));
        ssSettings.setWhereClause(m_wherePanel.getSelectedString());
        ssSettings.setLimit(m_limitChecker.isSelected() ? (int)m_limitSpinner.getValue() : -1);
        ssSettings
            .setDisplayName(m_useLabelsInRendererButton.isSelected() ? DisplayName.Label : DisplayName.TechnialName);
        ssSettings.save(settings);
        m_wherePanel.commitSelectedToHistory();
    }

    @Override
    public void onClose() {
        m_cache.onClose();
        m_portSpec = null;
        m_preferredSelectedFieldsPerObjectMap.clear();
    }

}

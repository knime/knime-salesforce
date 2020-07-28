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

import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FAILED_CONTENT;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FAILED_FIELD;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FETCHING_CONTENT;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.FETCHING_FIELD;
import static org.knime.salesforce.soql.SalesforceObjectSchemaCache.NO_AUTH_CONTENT;
import static org.knime.salesforce.soql.SalesforceSOQLNodeSettings.SOQLOutputRepresentation.RAW;
import static org.knime.salesforce.soql.SalesforceSOQLNodeSettings.SOQLOutputRepresentation.RECORDS;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.Border;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.base.util.flowvariable.FlowVariableResolver;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ConvenientComboBoxRenderer;
import org.knime.core.node.util.FlowVariableListCellRenderer;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;
import org.knime.salesforce.soql.SalesforceSOQLNodeSettings.SOQLOutputRepresentation;

/**
 * Dialog to 'Salesforce SOQL' node.
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceSOQLNodeDialogPane extends NodeDialogPane {

    private SalesforceAuthentication m_auth;

    private final JComboBox<SObject> m_sObjectsCombo;
    private final JList<Field> m_fieldList;
    private final JList<FlowVariable> m_flowVarsList;
    private final RSyntaxTextArea m_soqlTextArea;

    private final JRadioButton m_rawOutputRadio;
    private final JRadioButton m_recordOutputRadio;

    private final JCheckBox m_outputAsCount;

    private final SalesforceObjectSchemaCache m_cache;

    SalesforceSOQLNodeDialogPane() {
        m_fieldList = new JList<>(new DefaultListModel<>());
        m_flowVarsList = new JList<>(new DefaultListModel<>());
        m_soqlTextArea = new RSyntaxTextArea(0, 80);
        m_sObjectsCombo = new JComboBox<>(new DefaultComboBoxModel<>());
        m_sObjectsCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onNewSalesforceObjectSelected((SObject)m_sObjectsCombo.getSelectedItem());
            }
        });
        m_cache = new SalesforceObjectSchemaCache();

        m_outputAsCount = new JCheckBox("Only output size (for `count()` queries)");

        m_rawOutputRadio = new JRadioButton(RAW.getLabel());
        m_rawOutputRadio.setActionCommand(RAW.name());
        m_recordOutputRadio = new JRadioButton(RECORDS.getLabel());
        m_recordOutputRadio.setActionCommand(RECORDS.name());
        m_recordOutputRadio.addItemListener(e -> m_outputAsCount.setEnabled(m_recordOutputRadio.isSelected()));
        ButtonGroup bg = new ButtonGroup();
        bg.add(m_rawOutputRadio);
        bg.add(m_recordOutputRadio);
        m_recordOutputRadio.doClick();


        addTab("SOQL Editor", createPanel());
    }

    @SuppressWarnings("unchecked")
    private JPanel createPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel leftPanel = new JPanel(new GridBagLayout());
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        m_sObjectsCombo.setPrototypeDisplayValue(SObject.of("", "Account Account Account"));
        m_sObjectsCombo.setRenderer(new ConvenientComboBoxRenderer());
        m_sObjectsCombo.setBorder(createEmptyTitledBorder("Salesforce Objects"));
        leftPanel.add(m_sObjectsCombo, gbc);
        gbc.gridy += 1;
        leftPanel.add(new JPanel(), gbc);

        gbc.gridy += 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JSplitPane fieldAndFlowVarLeftPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        fieldAndFlowVarLeftPanel.setOneTouchExpandable(true);
        fieldAndFlowVarLeftPanel.setDividerLocation(0.4);
        m_fieldList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    SObject selectedObject = (SObject)m_sObjectsCombo.getSelectedItem();
                    Field selectedValue = m_fieldList.getSelectedValue();
                    if (selectedValue != null) {
                        onFieldSelected(selectedObject, selectedValue);
                    }
                }
            }
        });
        JScrollPane fieldListScroller = new JScrollPane(m_fieldList);
        fieldListScroller.setBorder(createEmptyTitledBorder("Object Fields"));
        fieldAndFlowVarLeftPanel.setTopComponent(fieldListScroller);
        JScrollPane flowVarsScroller = new JScrollPane(m_flowVarsList);
        flowVarsScroller.setBorder(createEmptyTitledBorder("Flow Variables"));
        fieldAndFlowVarLeftPanel.setBottomComponent(flowVarsScroller);
        m_flowVarsList.setCellRenderer(new FlowVariableListCellRenderer());
        m_flowVarsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FlowVariable selectedValue = m_flowVarsList.getSelectedValue();
                    if (selectedValue != null) {
                        onFlowVarSelected(selectedValue);
                    }
                }
            }
        });
        leftPanel.add(fieldAndFlowVarLeftPanel, gbc);

        JPanel rightPanel = new JPanel(new BorderLayout());
        RTextScrollPane soqlEditorScroller = new RTextScrollPane(m_soqlTextArea);
        soqlEditorScroller.setLineNumbersEnabled(true);
        soqlEditorScroller.setBorder(createEmptyTitledBorder("SOQL"));
        rightPanel.add(soqlEditorScroller, BorderLayout.CENTER);

        rightPanel.add(createControlPanelBelowMainSOQLEditorArea(), BorderLayout.SOUTH);

        JSplitPane mainSplitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPanel.setOneTouchExpandable(true);
        mainSplitPanel.setDividerLocation(0.3);
        mainSplitPanel.setResizeWeight(0.3);
        mainSplitPanel.setLeftComponent(leftPanel);
        mainSplitPanel.setRightComponent(rightPanel);

        JPanel result = new JPanel(new BorderLayout());
        result.add(mainSplitPanel, BorderLayout.CENTER);
        return result;
    }

    private JPanel createControlPanelBelowMainSOQLEditorArea() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Output Representation:"));
        panel.add(ViewUtils.getInFlowLayout(FlowLayout.LEADING, 5, 2, new JLabel("  "), m_rawOutputRadio));
        panel.add(ViewUtils.getInFlowLayout(FlowLayout.LEADING, 5, 2, new JLabel("  "), m_recordOutputRadio,
            new JLabel(" "), m_outputAsCount));
        return panel;
    }

    private static Border createEmptyTitledBorder(final String title) {
        return BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title);
    }

    private void onFlowVarSelected(final FlowVariable v) {
        String enter = FlowVariableResolver.getPlaceHolderForVariable(v);
        m_soqlTextArea.replaceSelection(enter);
        m_flowVarsList.clearSelection();
        m_soqlTextArea.requestFocus();
    }

    private void onFieldSelected(final SObject object, final Field field) {
        m_soqlTextArea.replaceSelection(object.getName() + "." + field.getName());
        m_flowVarsList.clearSelection();
        m_soqlTextArea.requestFocus();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        SalesforceSOQLNodeSettings soqlSettings = new SalesforceSOQLNodeSettings();
        soqlSettings.setSOQL(m_soqlTextArea.getText());
        String outputRepresentationName =
                Arrays.asList(m_rawOutputRadio, m_recordOutputRadio).stream()//
                .filter(AbstractButton::isSelected)//
                .map(JRadioButton::getActionCommand)//
                .findFirst()//
                .orElseThrow(() -> new InvalidSettingsException("No output type selected"));
        SOQLOutputRepresentation outputRepresentation =
            SalesforceSOQLNodeSettings.SOQLOutputRepresentation.from(outputRepresentationName)
                .orElseThrow(() -> new InvalidSettingsException("Invalid output representation"));
        soqlSettings.setOutputRepresentation(outputRepresentation);
        soqlSettings.setOutputAsCount(m_outputAsCount.isSelected());
        soqlSettings.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs) {
        SalesforceSOQLNodeSettings soqlSettings = new SalesforceSOQLNodeSettings().loadSettingsInDialog(settings);
        DefaultListModel<FlowVariable> flowVarModel = (DefaultListModel<FlowVariable>)m_flowVarsList.getModel();
        flowVarModel.removeAllElements();
        getAvailableFlowVariables(StringType.INSTANCE, DoubleType.INSTANCE, IntType.INSTANCE).values()//
            .stream()//
            .forEach(flowVarModel::addElement);

        DefaultListModel<Field> fieldModel = (DefaultListModel<Field>)m_fieldList.getModel();
        fieldModel.removeAllElements();

        DefaultComboBoxModel<SObject> sObjectsComboModel = (DefaultComboBoxModel<SObject>)m_sObjectsCombo.getModel();
        sObjectsComboModel.removeAllElements();
        SalesforceConnectionPortObjectSpec authSpec = (SalesforceConnectionPortObjectSpec)specs[0];
        if (authSpec != null && (m_auth = authSpec.getAuthentication().orElse(null)) != null) {
            sObjectsComboModel.addElement(FETCHING_CONTENT);
            fieldModel.addElement(FETCHING_FIELD);
            m_cache.executeNewSObjectsSwingWorker(m_auth, () -> {
                DefaultComboBoxModel<SObject> comboBoxModel = (DefaultComboBoxModel<SObject>)m_sObjectsCombo.getModel();
                comboBoxModel.removeAllElements();
                Set<SObject> sObjects = m_cache.getsObjectFieldCache().keySet();
                boolean success = !sObjects.contains(FAILED_CONTENT);
                sObjects.stream().sorted().forEach(comboBoxModel::addElement);
                m_sObjectsCombo.setEnabled(success);
            });
        } else {
            m_auth = null;
            sObjectsComboModel.addElement(NO_AUTH_CONTENT);
        }
        m_sObjectsCombo.setEnabled(false);
        m_sObjectsCombo.setEnabled(false);
        m_soqlTextArea.setText(soqlSettings.getSOQL());
        Arrays.asList(m_rawOutputRadio, m_recordOutputRadio).stream()//
            .filter(b -> b.getActionCommand().equals(soqlSettings.getOutputRepresentation().name()))//
            .findFirst().ifPresent(AbstractButton::doClick);
        m_outputAsCount.setSelected(soqlSettings.isOutputAsCount());
        m_soqlTextArea.requestFocus();
    }

    void onNewSalesforceObjectSelected(final SObject selected) {
        if (!Arrays.asList(FAILED_CONTENT, FETCHING_CONTENT).contains(selected)) {
            m_cache.cancelFetchFieldsSwingWorker();
            Field[] fields = m_cache.getsObjectFieldCache().get(selected);
            boolean enabled;
            if (fields != null) {
                enabled = !(fields.length == 1 && fields[0] == FAILED_FIELD);
            } else {
                enabled = false;
                m_cache.executeNewFieldsSwingWorker(m_auth, selected, s -> onNewSalesforceObjectSelected(s));
                fields = new Field[] {FETCHING_FIELD};
            }
            m_fieldList.setEnabled(enabled);
            DefaultListModel<Field> listModel = (DefaultListModel<Field>)m_fieldList.getModel();
            listModel.removeAllElements();
            Arrays.stream(fields).forEach(s -> listModel.addElement(s));
        }
    }

    @Override
    public void onClose() {
        m_cache.onClose();
        m_auth = null;
    }

}

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

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.Border;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ConvenientComboBoxRenderer;
import org.knime.core.node.util.FlowVariableListCellRenderer;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType.BooleanType;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.LongType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.bindings.fields.Field;
import org.knime.salesforce.rest.bindings.sobjects.SObject;

/**
 *
 * @author wiswedel
 */
final class SalesforceSOQLNodeDialogPane extends NodeDialogPane {

    public static final SObject NO_AUTH_CONTENT =
            SObject.of("No Authentication Object available from Node Input", "Not connected to salesforce.com");
    public static final SObject FETCHING_CONTENT =
            SObject.of("Retrieving content from salesforce.com", "Fetching Content...");
    public static final SObject FAILED_CONTENT =
            SObject.of("Error reading from salesforce.com, check log files for details", "<Failed to fetch list>");
    public static final SObject PROTOTYPE_LENGTH_CONTENT = SObject.of("", "Account Account Account");

    private static final Field FETCHING_FIELD = Field.of("do_not_use", "Fetching fields...", "void");

    private static final Field FAILED_FIELD = Field.of("do_not_use", "Failed to fetch fields", "void");

    private FetchSObjectsSwingWorker m_fetchSObjectsSwingWorker;

    private FetchFieldsSwingWorker m_fetchFieldsSwingWorker;

    private SalesforceAuthentication m_auth;

    private final Map<SObject, Field[]> m_sObjectFieldCache;

    private final JComboBox<SObject> m_sObjectsCombo;
    private final JList<Field> m_fieldList;
    private final JList<FlowVariable> m_flowVarsList;
    private final RSyntaxTextArea m_soqlTextArea;

    SalesforceSOQLNodeDialogPane() {
        m_fieldList = new JList<>(new DefaultListModel<>());
        m_flowVarsList = new JList<>(new DefaultListModel<>());
        m_soqlTextArea = new RSyntaxTextArea(0, 80);
        m_sObjectsCombo = new JComboBox<>(new DefaultComboBoxModel<>());
        m_sObjectsCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onNewSObjectSelected((SObject)m_sObjectsCombo.getSelectedItem());
            }
        });
        m_sObjectFieldCache = new HashMap<>();
        m_sObjectFieldCache.put(FAILED_CONTENT, new Field[] {FAILED_FIELD});
        m_sObjectFieldCache.put(FETCHING_CONTENT, new Field[] {});
        m_sObjectFieldCache.put(NO_AUTH_CONTENT, new Field[] {});
        addTab("SOQL Editor", createPanel());
    }

    private JPanel createPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel leftPanel = new JPanel(new GridBagLayout());
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        m_sObjectsCombo.setPrototypeDisplayValue(PROTOTYPE_LENGTH_CONTENT);
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
                    Field selectedValue = m_fieldList.getSelectedValue();
                    if (selectedValue != null) {
                        onFieldSelected(selectedValue);
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

        JSplitPane mainSplitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPanel.setOneTouchExpandable(true);
        mainSplitPanel.setDividerLocation(0.3);
        mainSplitPanel.setResizeWeight(0.3);
        mainSplitPanel.setLeftComponent(leftPanel);
        RTextScrollPane soqlEditorScroller = new RTextScrollPane(m_soqlTextArea);
        soqlEditorScroller.setLineNumbersEnabled(true);
        soqlEditorScroller.setBorder(createEmptyTitledBorder("SOQL"));
        mainSplitPanel.setRightComponent(soqlEditorScroller);

        JPanel result = new JPanel(new BorderLayout());
        result.add(mainSplitPanel, BorderLayout.CENTER);
        return result;
    }

    private Border createEmptyTitledBorder(final String title) {
        return BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title);
    }

    private void onFlowVarSelected(final FlowVariable v) {
        String enter = "${" + v.getName() + "}";
        m_soqlTextArea.replaceSelection(enter);
        m_flowVarsList.clearSelection();
        m_soqlTextArea.requestFocus();
    }

    private void onFieldSelected(final Field field) {
        m_soqlTextArea.replaceSelection(field.getName());
        m_flowVarsList.clearSelection();
        m_soqlTextArea.requestFocus();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        SalesforceSOQLNodeSettings soqlSettings = new SalesforceSOQLNodeSettings();
        soqlSettings.setSOQL(m_soqlTextArea.getText());
        soqlSettings.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs) {
        SalesforceSOQLNodeSettings soqlSettings = new SalesforceSOQLNodeSettings().loadSettingsInDialog(settings);
        DefaultListModel<FlowVariable> flowVarModel = (DefaultListModel<FlowVariable>)m_flowVarsList.getModel();
        flowVarModel.removeAllElements();
        getAvailableFlowVariables(BooleanType.INSTANCE, StringType.INSTANCE, DoubleType.INSTANCE, IntType.INSTANCE,
            LongType.INSTANCE).values().stream().forEach(fv -> flowVarModel.addElement(fv));

        DefaultListModel<Field> fieldModel = (DefaultListModel<Field>)m_fieldList.getModel();
        fieldModel.removeAllElements();

        DefaultComboBoxModel<SObject> sObjectsComboModel = (DefaultComboBoxModel<SObject>)m_sObjectsCombo.getModel();
        sObjectsComboModel.removeAllElements();
        SalesforceConnectionPortObjectSpec authSpec = (SalesforceConnectionPortObjectSpec)specs[0];
        if (authSpec != null && (m_auth = authSpec.getAuthentication().orElse(null)) != null) {
            sObjectsComboModel.addElement(FETCHING_CONTENT);
            fieldModel.addElement(FETCHING_FIELD);
            m_fetchSObjectsSwingWorker = new FetchSObjectsSwingWorker();
            m_fetchSObjectsSwingWorker.execute();
        } else {
            m_auth = null;
            sObjectsComboModel.addElement(NO_AUTH_CONTENT);
        }
        m_sObjectsCombo.setEnabled(false);
        m_sObjectsCombo.setEnabled(false);
        m_soqlTextArea.setText(soqlSettings.getSOQL());
        m_soqlTextArea.requestFocus();
    }

    void onNewSObjectSelected(final SObject selected) {
        if (!Arrays.asList(FAILED_CONTENT, FETCHING_CONTENT).contains(selected)) {
            cancelFetchFieldsSwingWorker();
            Field[] fields = m_sObjectFieldCache.get(selected);
            boolean enabled;
            if (fields != null) {
                enabled = !(fields.length == 1 && fields[0] == FAILED_FIELD);
            } else {
                enabled = false;
                m_fetchFieldsSwingWorker = new FetchFieldsSwingWorker(selected);
                m_fetchFieldsSwingWorker.execute();
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
        cancelFetchFieldsSwingWorker();
        cancelFetchSObjectsSwingWorker();
        m_auth = null;
    }

    private void cancelFetchFieldsSwingWorker() {
        if (m_fetchFieldsSwingWorker != null && !m_fetchFieldsSwingWorker.isDone()) {
            m_fetchFieldsSwingWorker.cancel(true);
        }
        m_fetchFieldsSwingWorker = null;
    }

    private void cancelFetchSObjectsSwingWorker() {
        if (m_fetchSObjectsSwingWorker != null && !m_fetchSObjectsSwingWorker.isDone()) {
            m_fetchSObjectsSwingWorker.cancel(true);
        }
        m_fetchSObjectsSwingWorker = null;
    }

    private final class FetchSObjectsSwingWorker extends SwingWorkerWithContext<SObject[], Void> {

        @Override
        protected SObject[] doInBackgroundWithContext() throws Exception {
            return SalesforceRESTUtil.getSObjects(m_auth);
        }

        @Override
        protected void doneWithContext() {
            SObject[] sObjects = new SObject[] {FAILED_CONTENT};
            boolean success = false;
            try {
                sObjects = get();
                success = true;
            } catch (InterruptedException | CancellationException ex) {
            } catch (ExecutionException ex) {
                getLogger().error(String.format("Unable to retrieve 'SObjects' from Salesforce (%s): %s",
                    m_auth.getInstanceURLString(), ex.getCause().getMessage()), ex.getCause());
            }
            DefaultComboBoxModel<SObject> comboBoxModel = (DefaultComboBoxModel<SObject>)m_sObjectsCombo.getModel();
            comboBoxModel.removeAllElements();
            Arrays.stream(sObjects).sorted().forEach(s -> comboBoxModel.addElement(s));
            m_sObjectsCombo.setEnabled(success);
        }
    }

    class FetchFieldsSwingWorker extends SwingWorkerWithContext<Field[], Void> {

        private final SObject m_sObject;

        FetchFieldsSwingWorker(final SObject sObject) {
            m_sObject = sObject;
        }

        @Override
        protected Field[] doInBackgroundWithContext() throws Exception {
            Field[] fields = SalesforceRESTUtil.getSObjectFields(m_sObject, m_auth);
            return fields;
        }

        @Override
        protected void doneWithContext() {
            Field[] fields = new Field[] {FETCHING_FIELD};
            SObject selectedObject = FAILED_CONTENT;
            try {
                fields = get();
                m_sObjectFieldCache.put(m_sObject, fields);
                selectedObject = m_sObject;
            } catch (InterruptedException | CancellationException ex) {
            } catch (ExecutionException ex) {
                getLogger().error(String.format("Unable to fetch fields for object \"%s\" from salesforce (%s): %s",
                    m_sObject.getName(), m_auth.getInstanceURLString(), ex.getCause().getMessage()), ex.getCause());
            }
            onNewSObjectSelected(selectedObject);
        }
    }

}

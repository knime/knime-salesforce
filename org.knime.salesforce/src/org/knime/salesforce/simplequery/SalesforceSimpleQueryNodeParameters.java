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
 * ------------------------------------------------------------------------
 */

package org.knime.salesforce.simplequery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.filter.TwinlistWidget;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;
import org.knime.node.parameters.widget.text.TextAreaWidget;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.SalesforceResponseException;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;
import org.knime.salesforce.simplequery.SalesforceSimpleQueryNodeSettings.DisplayName;

/**
 * Node parameters for Salesforce Simple Query.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.1
 */
final class SalesforceSimpleQueryNodeParameters implements NodeParameters {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceSimpleQueryNodeParameters.class);

    private static final class ObjectNameValueReference implements ParameterReference<String> {}
    private static final class FieldNamesValueReference implements ParameterReference<String[]> {}

    static final class DisplayNameValueReference implements ParameterReference<DisplayNameOption> {}


    /** Intermediate state provider for SObjects (tables in SFDC). */
    private static class IntermediateSObjectsStateProvider implements StateProvider<SObject[]> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public SObject[] computeState(final NodeParametersInput context) {
            final var portSpec = context.getInPortSpec(0); // ensure that the input spec is loaded
            if (portSpec.orElse(null) instanceof SalesforceConnectionPortObjectSpec salesforcePOS) {
                final var cred = salesforcePOS.getCredential(SalesforceAccessTokenCredential.class).orElse(null);
                if (cred == null) {
                    return null; // NOSONAR
                }
                try {
                    return SalesforceRESTUtil.getSObjects(cred, salesforcePOS.getTimeouts());
                } catch (SalesforceResponseException ex) { // NOSONAR error shown in text message (separate field)
                    return null; // NOSONAR
                }
            }
            return null; // NOSONAR
        }

    }

    /**
     * Choices provider for SObjects (tables in SFDC, name is either the label or technical name depending on user
     * settings).
     */
    private static class SalesforceObjectChoicesProvider implements StringChoicesProvider {

        private Supplier<DisplayNameOption> m_displayNameOptionSupplier;
        private Supplier<SObject[]> m_sObjectStateProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_sObjectStateProvider = initializer.computeFromProvidedState(IntermediateSObjectsStateProvider.class);
            m_displayNameOptionSupplier = initializer.computeFromValueSupplier(DisplayNameValueReference.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            SObject[] sObjects = Objects.requireNonNullElse(m_sObjectStateProvider.get(), new SObject[0]);
            Function<SObject, String> labelFunction = m_displayNameOptionSupplier.get().sObjectNameFunction();
            return Arrays.stream(sObjects).filter(SObject::isQueryable).sorted()
                .map(s -> new StringChoice(s.getName(), labelFunction.apply(s))).toList();
        }

    }

    /** Warning message provider if no connection could be established. */
    private static class WarningMessageProvider implements TextMessage.SimpleTextMessageProvider {

        private Supplier<SObject[]> m_sObjectSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_sObjectSupplier = initializer.computeFromProvidedState(IntermediateSObjectsStateProvider.class);
        }

        @Override
        public boolean showMessage(final NodeParametersInput context) {
            return m_sObjectSupplier.get() == null;
        }

        @Override
        public String title() {
            return "No connection to Salesforce";
        }

        @Override
        public String description() {
            return "No connection to Salesforce could be established. "
                + "Check the input connection, possibly (re-)execute the node.";
        }

        @Override
        public MessageType type() {
            return MessageType.ERROR;
        }

    }

    /** Intermediate state provider for fields of the selected SObject (column names in SDFC table). */
    private static final class IntermediateSalesforceFieldsValueProvider implements StateProvider<SalesforceField[]> {

        private Supplier<String> m_sObjectNameValueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_sObjectNameValueSupplier = initializer.computeFromValueSupplier(ObjectNameValueReference.class);
        }

        @SuppressWarnings("restriction")
        @Override
        public SalesforceField[] computeState(final NodeParametersInput context)
            throws StateComputationFailureException {
            final var portSpec = context.getInPortSpec(0); // ensure that the input spec is loaded
            if (portSpec.orElse(null) instanceof SalesforceConnectionPortObjectSpec salesforcePOS) {
                final var cred = salesforcePOS.getCredential(SalesforceAccessTokenCredential.class).orElse(null);
                if (cred == null) {
                    // this should be already handled by the warning message (separate field)
                    return new SalesforceField[0];
                }
                final String sObjectName = m_sObjectNameValueSupplier.get();
                Field[] fields;
                try {
                    // ignores technical and label name differences, as the API call needs the technical name
                    final SObject sObject = SObject.of(sObjectName, sObjectName);
                    fields = SalesforceRESTUtil.getSObjectFields(sObject, cred, salesforcePOS.getTimeouts());
                } catch (SalesforceResponseException | RuntimeException ex) { // RuntimeException for host not found etc
                    final String msg = "Error retrieving fields for object '" + sObjectName + "': " + ex.getMessage();
                    LOGGER.error(msg, ex);
                    throw new StateComputationFailureException(msg);
                }
                Arrays.sort(fields, (a, b) -> a.getLabel().compareTo(b.getLabel()));
                return Arrays.stream(fields).map(SalesforceField::fromField)
                    .flatMap(Optional::stream).toArray(SalesforceField[]::new);
            }
            return new SalesforceField[0];
        }

    }

    /** Choices provider for twin list (column names in SDFC table), depends on intermediate state above. */
    private static class SalesforceFieldNamesChoicesProvider implements StringChoicesProvider {

        private Supplier<DisplayNameOption> m_displayNameOptionSupplier;
        private Supplier<SalesforceField[]> m_salesforceFieldsValueProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog(); // in case the other two do not run (not connected)
            m_salesforceFieldsValueProvider =
                initializer.computeFromProvidedState(IntermediateSalesforceFieldsValueProvider.class);
            m_displayNameOptionSupplier = initializer.computeFromValueSupplier(DisplayNameValueReference.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            final SalesforceField[] salesforceFields =
                Objects.requireNonNullElse(m_salesforceFieldsValueProvider.get(), new SalesforceField[0]);
            Function<SalesforceField, String> labelFunction = m_displayNameOptionSupplier.get().nameFunction();
            return Arrays.stream(salesforceFields) //
                .map(s -> new StringChoice(s.getName(), labelFunction.apply(s))) //
                .toList();
        }

    }

    /** Value provider for the selected fields; maps the selected fields in the UI to the underlying
     * 'SalesforceField' when the selection changes. */
    private static class SalesforceFieldValueProvider implements StateProvider<SalesforceField[]> {

        private Supplier<String[]> m_fieldNamesSupplier;
        private Supplier<SalesforceField[]> m_salesforceFieldsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_fieldNamesSupplier = initializer.computeFromValueSupplier(FieldNamesValueReference.class);
            m_salesforceFieldsSupplier =
                initializer.computeFromProvidedState(IntermediateSalesforceFieldsValueProvider.class);
        }

        @Override
        public SalesforceField[] computeState(final NodeParametersInput context) {
            final String[] fieldNames = Objects.requireNonNullElse(m_fieldNamesSupplier.get(), new String[0]);
            final SalesforceField[] salesforceFields =
                Objects.requireNonNullElse(m_salesforceFieldsSupplier.get(), new SalesforceField[0]);
            Map<String, SalesforceField> fieldMap =
                Arrays.stream(salesforceFields).collect(Collectors.toMap(SalesforceField::getName, f -> f));
            fieldMap.keySet().retainAll(Arrays.asList(fieldNames));
            return fieldMap.values().toArray(new SalesforceField[0]);
        }

    }

    /** Value provider to reset the selected fields when the sObject changes. */
    private static class SalesforceFieldNamesValueProvider implements StateProvider<String[]> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnValueChange(ObjectNameValueReference.class);
        }

        @Override
        public String[] computeState(final NodeParametersInput context) {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }

    }

    /**
     * Enum for display name options.
     */
    enum DisplayNameOption {
        @Label("Labels")
        LABEL("Labels"),

        @Label("Technical Names")
        TECHNICAL_NAME("Technical Names");

        private final String m_text;

        DisplayNameOption(final String text) {
            m_text = text;
        }

        @Override
        public String toString() {
            return m_text;
        }

        Function<SalesforceField, String> nameFunction() {
            return this == LABEL ? SalesforceField::getLabel : SalesforceField::getName;
        }

        Function<SObject, String> sObjectNameFunction() {
            return this == LABEL ? SObject::getLabel : SObject::getName;
        }

    }

    @TextMessage(WarningMessageProvider.class)
    Void m_warningMessage;

    @Widget(title = "Names based on", description = """
            Determines whether the column names in the output table are derived from the Salesforce field \
            names or labels. Labels are human readable and also used in the Salesforce user interface, e.g. \
            AI Record Insight ID. Field names are names used in the API, e.g. AiRecordInsightId. Most \
            standard fields use the same name as the label. Custom fields will have the '__c' suffix. The \
            option also controls how fields and objects are displayed in the configuration dialog.""")
    @ValueSwitchWidget
    @Persistor(DisplayNamePersistor.class)
    @ValueReference(DisplayNameValueReference.class)
    DisplayNameOption m_displayName = DisplayNameOption.LABEL;

    @Widget(title = "Salesforce Object", description = """
            The objects as available in Salesforce. The list is queried when the dialog is opened. \
            The list only contains objects, which are queryable (a property set in Salesforce).""")
    @ChoicesProvider(SalesforceObjectChoicesProvider.class)
    @ValueReference(ObjectNameValueReference.class)
    @Persistor(SObjectNamePersistor.class)
    String m_sObjectName = "";

    @Widget(title = "Selected Fields", description = """
            The fields defined for the selected object. Move the fields that should be retrieved into the \
            'Include' list. The field's type is mapped to a native KNIME type (string, int, double, boolean, \
            date &amp; time, ...), whereby some types may not be supported (for instance Salesforce's anyType). \
            Fields with such unsupported type are hidden in the configuration dialog.""")
    @TwinlistWidget
    @ChoicesProvider(SalesforceFieldNamesChoicesProvider.class)
    @ValueReference(FieldNamesValueReference.class)
    @ValueProvider(SalesforceFieldNamesValueProvider.class)
    @Persistor(FieldNamesPersistor.class)
    String[] m_fieldNames = new String[0];

    /** Only for persistence, represents the fields in the names array. */
    @ValueProvider(SalesforceFieldValueProvider.class)
    @Persistor(SalesforceFieldArrayPersistor.class)
    SalesforceField[] m_salesforceFields = new SalesforceField[0];

    @Widget(title = "WHERE clause", description = """
            An optional WHERE clause to filter the result set. Examples are Name LIKE 'A%' CreatedDate > \
            2011-04-26T10:00:00-08:00 CALENDAR_YEAR(CreatedDate) = 2011 (find some examples in the Salesforce \
            online documentation).""")
    @TextAreaWidget
    @Persistor(WhereClausePersistor.class)
    String m_whereClause = "";

    @Widget(title = "LIMIT result set", description =
            "An optional integer to constraint the result set to a maximum number as specified.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
    @Persistor(LimitPersistor.class)
    Optional<Integer> m_limit = Optional.empty();

    @Widget(title = "Also retrieve deleted and archived records", description = """
            When selected, the node will use Salesforce's queryAll endpoint to include deleted and archived \
            records in the results.""")
    @Persistor(RetrieveDeletedArchivedPersistor.class)
    boolean m_retrieveDeletedAndArchived = false; // NOSONAR (explicit assignment)

    static final class DisplayNamePersistor implements NodeParametersPersistor<DisplayNameOption> {

        @Override
        public DisplayNameOption load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String value = settings.getString("display", DisplayName.Label.name());
            return DisplayName.valueOf(value) == DisplayName.Label ?
                    DisplayNameOption.LABEL : DisplayNameOption.TECHNICAL_NAME;
        }

        @Override
        public void save(final DisplayNameOption obj, final NodeSettingsWO settings) {
            DisplayName legacyValue = obj == DisplayNameOption.LABEL ? DisplayName.Label : DisplayName.TechnialName;
            settings.addString("display", legacyValue.name());
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{"display"}};
        }
    }

    static final class SObjectNamePersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getString("objectName", "");
        }

        @Override
        public void save(final String obj, final NodeSettingsWO settings) {
            settings.addString("objectName", obj == null ? "" : obj);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{"objectName"}};
        }
    }

    static final class WhereClausePersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String value = settings.getString("where", null);
            return value == null ? "" : value;
        }

        @Override
        public void save(final String obj, final NodeSettingsWO settings) {
            String value = (obj == null || obj.trim().isEmpty()) ? null : obj.trim();
            settings.addString("where", value);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{"where"}};
        }
    }

    static final class LimitPersistor implements NodeParametersPersistor<Optional<Integer>> {

        @Override
        public Optional<Integer> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            int value = settings.getInt("limit", -1);
            return value < 0 ? Optional.empty() : Optional.of(Integer.valueOf(value));
        }

        @Override
        public void save(final Optional<Integer> obj, final NodeSettingsWO settings) {
            settings.addInt("limit", obj.orElse(-1));
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{"limit"}};
        }
    }

    static final class RetrieveDeletedArchivedPersistor implements NodeParametersPersistor<Boolean> {

        @Override
        public Boolean load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getBoolean("retrieveDeletedArchived", false);
        }

        @Override
        public void save(final Boolean obj, final NodeSettingsWO settings) {
            settings.addBoolean("retrieveDeletedArchived", obj == null ? false : obj);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{"retrieveDeletedArchived"}};
        }
    }

    static final class SalesforceFieldArrayPersistor implements NodeParametersPersistor<SalesforceField[]> {

        @Override
        public SalesforceField[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
            try {
                NodeSettingsRO fieldsSettings = settings.getNodeSettings("fields");
                List<SalesforceField> fieldList = new ArrayList<>();
                for (String key : fieldsSettings.keySet()) {
                    try {
                        fieldList.add(
                            SalesforceSimpleQueryNodeSettings.readSalesforceFieldFromSettings(fieldsSettings, key));
                    } catch (InvalidSettingsException ex) { // NOSONAR settings loading in dialog
                        // ignore invalid fields
                    }
                }
                return fieldList.toArray(new SalesforceField[0]);
            } catch (InvalidSettingsException ex) { // NOSONAR settings loading in dialog
                return new SalesforceField[0];
            }
        }

        @Override
        public void save(final SalesforceField[] fields, final NodeSettingsWO settings) {
            NodeSettingsWO fieldsSettings = settings.addNodeSettings("fields");
            if (fields != null) {
                for (int i = 0; i < fields.length; i++) {
                    NodeSettingsWO fieldSettings = fieldsSettings.addNodeSettings("field-" + i);
                    SalesforceSimpleQueryNodeSettings.writeSalesforceFieldToSettings(fieldSettings, fields[i]);
                }
            }
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }
    }

    /** Noop persistor, the actual array is re-computed from another field. */
    private static final class FieldNamesPersistor implements NodeParametersPersistor<String[]> {

        @Override
        public String[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return Arrays.stream(new SalesforceFieldArrayPersistor().load(settings)) //
                    .map(l -> l.getName()) //
                    .toArray(String[]::new);
        }

        @Override
        public void save(final String[] obj, final NodeSettingsWO settings) {
            // no-op
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }
    }
}

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
import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.Migrate;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.filter.TwinlistWidget;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;
import org.knime.node.parameters.widget.text.TextAreaWidget;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.SalesforceResponseException;
import org.knime.salesforce.rest.Timeouts;
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

    private static final Message NOT_CONNECTED_MESSAGE =
        new TextMessage.Message("No connection to Salesforce", "No connection could be established. "
            + "Check the input connection, possibly (re-)execute the predecessor node.", MessageType.WARNING);

    /** return type of a state provider that provides a message and some data. */
    private record MessageAndData<T>(TextMessage.Message message, T data) {
    }

    private static final class ObjectNameValueReference implements ParameterReference<String> {
    }

    private static final class FieldNamesValueReference implements ParameterReference<String[]> {
    }

    private static final class DisplayNameValueReference implements ParameterReference<DisplayName> {
    }

    /** Intermediate state provider for SObjects (tables in SFDC). */
    private static class IntermediateSObjectsStateProvider implements StateProvider<MessageAndData<SObject[]>> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public MessageAndData<SObject[]> computeState(final NodeParametersInput context) {
            final var portSpec = context.getInPortSpec(0); // ensure that the input spec is loaded

            final Optional<SalesforceConnectionPortObjectSpec> salesforcePOSOpt = portSpec //
                .filter(SalesforceConnectionPortObjectSpec.class::isInstance) //
                .map(SalesforceConnectionPortObjectSpec.class::cast);
            final SalesforceAccessTokenCredential cred = salesforcePOSOpt //
                .flatMap(s -> s.getCredential(SalesforceAccessTokenCredential.class)) //
                .orElse(null);

            if (cred == null) {
                return new MessageAndData<>(NOT_CONNECTED_MESSAGE, new SObject[0]);
            }
            try {
                // TODO currently not cancelable - https://knime-com.atlassian.net/browse/UIEXT-2604
                return new MessageAndData<>(null,
                    SalesforceRESTUtil.getSObjects(cred, salesforcePOSOpt.orElseThrow().getTimeouts()));
            } catch (SalesforceResponseException | RuntimeException ex) { // NOSONAR error shown in text message (separate field)
                return new MessageAndData<>( //
                    new TextMessage.Message("Error retrieving objects from Salesforce",
                        "An error occured while reading data from Salesforce:\n"
                            + StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                        MessageType.ERROR),
                    new SObject[0]);
            }
        }

    }

    /**
     * Choices provider for SObjects (tables in SFDC, name is either the label or technical name depending on user
     * settings).
     */
    private static class SalesforceObjectChoicesProvider implements StringChoicesProvider {

        private Supplier<DisplayName> m_displayNameOptionSupplier;

        private Supplier<MessageAndData<SObject[]>> m_sObjectStateProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_sObjectStateProvider = initializer.computeFromProvidedState(IntermediateSObjectsStateProvider.class);
            m_displayNameOptionSupplier = initializer.computeFromValueSupplier(DisplayNameValueReference.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            SObject[] sObjects = m_sObjectStateProvider.get().data(); // in case of error, empty array
            Function<SObject, String> labelFunction = m_displayNameOptionSupplier.get().sObjectNameFunction();
            return Arrays.stream(sObjects).filter(SObject::isQueryable).sorted()
                .map(s -> new StringChoice(s.getName(), labelFunction.apply(s))).toList();
        }

    }

    /** Warning message provider if no connection could be established. */
    private static class WarningMessageProvider implements TextMessage.SimpleTextMessageProvider {

        private Supplier<MessageAndData<SObject[]>> m_msgAndSObjectSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_msgAndSObjectSupplier = initializer.computeFromProvidedState(IntermediateSObjectsStateProvider.class);
        }

        private Optional<Message> getMessageOptional() {
            return Optional.ofNullable(m_msgAndSObjectSupplier.get().message());
        }

        @Override
        public boolean showMessage(final NodeParametersInput context) {
            return getMessageOptional().isPresent();
        }

        @Override
        public String title() {
            return getMessageOptional().map(TextMessage.Message::title).orElse("");
        }

        @Override
        public String description() {
            return getMessageOptional().map(TextMessage.Message::description).orElse("");
        }

        @Override
        public MessageType type() {
            return getMessageOptional().map(TextMessage.Message::type).orElse(TextMessage.MessageType.INFO);
        }

    }

    /** Intermediate state provider for fields of the selected SObject (column names in SDFC table). */
    private static final class IntermediateSalesforceFieldsValueProvider
        implements StateProvider<MessageAndData<SalesforceField[]>> {

        private Supplier<String> m_sObjectNameValueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_sObjectNameValueSupplier = initializer.computeFromValueSupplier(ObjectNameValueReference.class);
        }

        @SuppressWarnings("restriction")
        @Override
        public MessageAndData<SalesforceField[]> computeState(final NodeParametersInput context)
            throws StateComputationFailureException {
            final var portSpec = context.getInPortSpec(0); // ensure that the input spec is loaded

            final Optional<SalesforceConnectionPortObjectSpec> salesforcePOSOpt = portSpec //
                .filter(SalesforceConnectionPortObjectSpec.class::isInstance) //
                .map(SalesforceConnectionPortObjectSpec.class::cast);
            final SalesforceAccessTokenCredential cred = salesforcePOSOpt //
                .flatMap(s -> s.getCredential(SalesforceAccessTokenCredential.class)) //
                .orElse(null);
            if (cred == null) {
                return new MessageAndData<>(NOT_CONNECTED_MESSAGE, new SalesforceField[0]);
            }
            final String sObjectName = m_sObjectNameValueSupplier.get();
            Field[] fields = new Field[0];
            try {
                // ignores technical and label name differences, as the API call needs the technical name
                final SObject sObject = SObject.of(sObjectName, sObjectName);
                final Timeouts timeouts = salesforcePOSOpt.orElseThrow().getTimeouts();
                // TODO currently not cancelable - https://knime-com.atlassian.net/browse/UIEXT-2604
                fields = SalesforceRESTUtil.getSObjectFields(sObject, cred, timeouts);
            } catch (SalesforceResponseException | RuntimeException ex) { // RuntimeException for host not found etc
                final String msg = "Unable to read fields for object '" + sObjectName + "': " + ex.getMessage();
                LOGGER.error(msg, ex);
                return new MessageAndData<>(new TextMessage.Message("Unable to read fields", msg, MessageType.ERROR),
                    new SalesforceField[0]);
            }
            Arrays.sort(fields, (a, b) -> a.getLabel().compareTo(b.getLabel()));
            SalesforceField[] sfFields = Arrays.stream(fields).map(SalesforceField::fromField).flatMap(Optional::stream)
                .toArray(SalesforceField[]::new);
            return new MessageAndData<>(null, sfFields);
        }

    }

    /** Choices provider for twin list (column names in SDFC table), depends on intermediate state above. */
    private static class FieldNamesChoicesProvider implements StringChoicesProvider {

        private Supplier<DisplayName> m_displayNameOptionSupplier;

        private Supplier<MessageAndData<SalesforceField[]>> m_msgAndSalesforceFieldsValueProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog(); // in case the other two do not run (not connected)
            m_msgAndSalesforceFieldsValueProvider =
                initializer.computeFromProvidedState(IntermediateSalesforceFieldsValueProvider.class);
            m_displayNameOptionSupplier = initializer.computeFromValueSupplier(DisplayNameValueReference.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            // empty in case of error
            final SalesforceField[] salesforceFields = m_msgAndSalesforceFieldsValueProvider.get().data();
            Function<SalesforceField, String> labelFunction = m_displayNameOptionSupplier.get().nameFunction();
            return Arrays.stream(salesforceFields) //
                .map(s -> new StringChoice(s.getName(), labelFunction.apply(s))) //
                .toList();
        }

    }

    /**
     * Value provider for the selected fields; maps the selected fields in the UI to the underlying 'SalesforceField'
     * when the selection changes.
     */
    private static class SalesforceFieldValueProvider implements StateProvider<SalesforceField[]> {

        private Supplier<String[]> m_fieldNamesSupplier;

        private Supplier<MessageAndData<SalesforceField[]>> m_msgAndSalesforceFieldsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_fieldNamesSupplier = initializer.computeFromValueSupplier(FieldNamesValueReference.class);
            m_msgAndSalesforceFieldsSupplier =
                initializer.computeFromProvidedState(IntermediateSalesforceFieldsValueProvider.class);
        }

        @Override
        public SalesforceField[] computeState(final NodeParametersInput context) {
            final String[] fieldNames = Objects.requireNonNullElse(m_fieldNamesSupplier.get(), new String[0]);
            // empty in case of error
            final SalesforceField[] salesforceFields = m_msgAndSalesforceFieldsSupplier.get().data();
            Map<String, SalesforceField> fieldMap =
                Arrays.stream(salesforceFields).collect(Collectors.toMap(SalesforceField::getName, f -> f));
            fieldMap.keySet().retainAll(Arrays.asList(fieldNames));
            return fieldMap.values().toArray(new SalesforceField[0]);
        }

    }

    /** Value provider to reset the selected fields when the sObject changes. */
    private static class FieldNamesValueProvider implements StateProvider<String[]> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnValueChange(ObjectNameValueReference.class);
        }

        @Override
        public String[] computeState(final NodeParametersInput context) {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }

    }

    @TextMessage(WarningMessageProvider.class)
    Void m_warningMessage;

    @Widget(title = "Names based on", description = """
            Determines whether the column names in the output table are derived from the Salesforce field \
            names or labels. Labels are human readable and also used in the Salesforce user interface, e.g. \
            <tt>AI Record Insight ID</tt>. Field names are names used in the API, e.g. <tt>AiRecordInsightId</tt>. \
            Most standard fields use the same name as the label. Custom fields will have the '__c' suffix. The \
            option also controls how fields and objects are displayed in the configuration dialog.""")
    @ValueSwitchWidget
    @ValueReference(DisplayNameValueReference.class)
    @Persist(configKey = SalesforceSimpleQueryNodeSettings.CFG_DISPLAY_TYPE)
    DisplayName m_displayName = DisplayName.Label;

    @Widget(title = "Salesforce Object", description = """
            The objects as available in Salesforce. The list is queried when the dialog is opened. \
            The list only contains objects, which are queryable (a property set in Salesforce).""")
    @ChoicesProvider(SalesforceObjectChoicesProvider.class)
    @ValueReference(ObjectNameValueReference.class)
    @Persist(configKey = SalesforceSimpleQueryNodeSettings.CFG_OBJECT_NAME)
    String m_sObjectName = "";

    @Widget(title = "Selected Fields", description = """
            The fields defined for the selected object. Move the fields that should be retrieved into the \
            'Include' list. The field's type is mapped to a native KNIME type (string, int, double, boolean, \
            date &amp; time, ...), whereby some types may not be supported (for instance Salesforce's anyType). \
            Fields with such unsupported type are hidden in the configuration dialog.""")
    @TwinlistWidget
    @ChoicesProvider(FieldNamesChoicesProvider.class)
    @ValueReference(FieldNamesValueReference.class)
    @ValueProvider(FieldNamesValueProvider.class)
    @Persistor(FieldNamesPersistor.class)
    String[] m_fieldNames = new String[0];

    /** Only for persistence, represents the fields in the names array. */
    @ValueProvider(SalesforceFieldValueProvider.class)
    @Persistor(SalesforceFieldArrayPersistor.class)
    SalesforceField[] m_salesforceFields = new SalesforceField[0];

    @Widget(title = "WHERE clause", description = """
            An optional WHERE clause to filter the result set. Examples are <tt>Name LIKE 'A%' CreatedDate > \
            2024-04-26T10:00:00-08:00 CALENDAR_YEAR(CreatedDate) = 2024</tt> (find some examples in the Salesforce \
            online documentation).""", advanced = true)
    @TextAreaWidget
    @Persistor(WhereClausePersistor.class)
    String m_whereClause = null; // NOSONAR (explicit assignment)

    @Widget(title = "LIMIT result set",
        description = "An optional integer to constraint the result set to a maximum number as specified.",
        advanced = true)
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
    @Persistor(LimitPersistor.class)
    Optional<Integer> m_limit = Optional.empty();

    @Widget(title = "Also retrieve deleted and archived records", description = """
            When selected, the node will use Salesforce's queryAll endpoint to include deleted and archived \
            records in the results.""", advanced = true)
    @Persist(configKey = SalesforceSimpleQueryNodeSettings.CFG_RETRIEVE_DELETED_ARCHIVED)
    @Migrate(loadDefaultIfAbsent = true)
    boolean m_retrieveDeletedAndArchived = false; // NOSONAR (explicit assignment)

    static final class WhereClausePersistor implements NodeParametersPersistor<String> {
        // needed because @Persist does not support empty string -> null conversion

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final String value = settings.getString(SalesforceSimpleQueryNodeSettings.CFG_WHERE_CLAUSE, null);
            return Objects.requireNonNullElse(value, "");
        }

        @Override
        public void save(final String obj, final NodeSettingsWO settings) {
            settings.addString(SalesforceSimpleQueryNodeSettings.CFG_WHERE_CLAUSE, StringUtils.trimToNull(obj));
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{SalesforceSimpleQueryNodeSettings.CFG_WHERE_CLAUSE}};
        }
    }

    static final class LimitPersistor implements NodeParametersPersistor<Optional<Integer>> {

        @Override
        public Optional<Integer> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            int value = settings.getInt(SalesforceSimpleQueryNodeSettings.CFG_LIMIT_CLAUSE, -1);
            return value < 0 ? Optional.empty() : Optional.of(Integer.valueOf(value));
        }

        @Override
        public void save(final Optional<Integer> obj, final NodeSettingsWO settings) {
            settings.addInt(SalesforceSimpleQueryNodeSettings.CFG_LIMIT_CLAUSE, obj.orElse(-1));
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{SalesforceSimpleQueryNodeSettings.CFG_LIMIT_CLAUSE}};
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
            NodeSettingsWO fieldsSettings = settings.addNodeSettings(SalesforceSimpleQueryNodeSettings.CFG_FIELDS);
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

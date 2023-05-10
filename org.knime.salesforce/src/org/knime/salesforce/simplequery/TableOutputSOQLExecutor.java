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
 *   Jul 5, 2020 (wiswedel): created
 */
package org.knime.salesforce.simplequery;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.JsonUtil;
import org.knime.core.util.UniqueNameGenerator;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.rest.SalesforceResponseException;
import org.knime.salesforce.rest.soql.AbstractSOQLExecutor;
import org.knime.salesforce.simplequery.SalesforceFieldType.CellCreator;
import org.knime.salesforce.simplequery.SalesforceSimpleQueryNodeSettings.DisplayName;

import jakarta.json.JsonPointer;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

/**
 * Runs the SOQL and reads the result using some JSon paths.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public class TableOutputSOQLExecutor extends AbstractSOQLExecutor {


    private final SalesforceSimpleQueryNodeSettings m_settings;

    /**
     * @param authentication
     * @param settings
     * @throws InvalidSettingsException
     */
    public TableOutputSOQLExecutor(final SalesforceAuthentication authentication, final SalesforceSimpleQueryNodeSettings settings)
        throws InvalidSettingsException {
        super(authentication, createSOQL(settings));
        m_settings = settings;
    }

    private static final String createSOQL(final SalesforceSimpleQueryNodeSettings settings) {
        StringBuilder soqlBuilder = new StringBuilder();
        soqlBuilder.append("SELECT ");
        soqlBuilder.append(Arrays.stream(settings.getObjectFields()) //
            .map(f -> settings.getObjectName() + "." + f.getName()) //
            .collect(Collectors.joining(", ")));
        soqlBuilder.append(" FROM ");
        soqlBuilder.append(settings.getObjectName());
        settings.getWhereClause().ifPresent(w -> soqlBuilder.append(" WHERE ").append(w));
        settings.getLimit().ifPresent(l -> soqlBuilder.append(" LIMIT ").append(l));
        return soqlBuilder.toString();
    }

    @Override
    public Optional<DataTableSpec> createOutputSpec() {
        return Optional.of(createSpec());
    }

    private DataTableSpec createSpec() {
        UniqueNameGenerator nameGen = new UniqueNameGenerator(Collections.emptySet());
        Function<SalesforceField, String> nameExtractor =
            f -> m_settings.getDisplayName() == DisplayName.Label ? f.getLabel() : f.getName();
        return new DataTableSpec( //
            Arrays.stream(m_settings.getObjectFields()) //
            .map(f -> nameGen.newColumn(nameExtractor.apply(f), f.getType().getKNIMEType())) //
            .toArray(DataColumnSpec[]::new));
    }

    @Override
    public BufferedDataTable execute(final ExecutionContext context)
        throws SalesforceResponseException, CanceledExecutionException {
        BufferedDataContainer container = context.createDataContainer(createSpec());
        FieldReader[] fieldReaders = Arrays.stream(m_settings.getObjectFields()) //
            .map(f -> new FieldReader(JsonUtil.getProvider().createPointer("/" + f.getName()), f.getType(), context))//
                .toArray(FieldReader[]::new);
        context.setMessage("Invoking Salesforce REST API");
        JsonStructure nextResults = execute();
        String sizeAsString = String.format("(%s total records)",
            getTotalSize().isPresent() ? Integer.toString(getTotalSize().getAsInt()) : "unknown number of ");
        long rowIndex = 0L;
        long chunkIndex = 0L;
        do {
            context.setMessage("Processing result set " + chunkIndex + sizeAsString);
            for (JsonStructure split : splitJsonStructureByRecords(nextResults)) {
                DataCell[] cells = new DataCell[fieldReaders.length];
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = fieldReaders[i].read(split);
                }
                context.checkCanceled();
                container.addRowToTable(new DefaultRow(RowKey.createRowKey(rowIndex), cells));
                rowIndex++;
            }
            chunkIndex++;
            context.setMessage("Invoking Salesforce REST API for next chunk (index " + chunkIndex + ")");
        } while ((nextResults = readNext().orElse(null)) != null);
        container.close();
        return container.getTable();
    }

    private static final class FieldReader {
        private final JsonPointer m_jsonPointer;
        private final CellCreator m_cellCreator;
        private final SalesforceFieldType m_salesforceFieldType;
        /**
         * @param jsonPointer
         * @param cellFactory
         */
        FieldReader(final JsonPointer jsonPointer, final SalesforceFieldType salesforceFieldType, final ExecutionContext exec) {
            m_jsonPointer = jsonPointer;
            m_salesforceFieldType = salesforceFieldType;
            m_cellCreator = salesforceFieldType.newCellCreator(exec);
        }

        DataCell read(final JsonStructure structure) throws SalesforceResponseException {
            if (m_jsonPointer.containsValue(structure)) {
                JsonValue value = m_jsonPointer.getValue(structure);
                if (value.getValueType() == ValueType.NULL) {
                    return DataType.getMissingCell();
                }
                try {
                    return m_cellCreator.toCell(value);
                } catch (Exception ex) {
                    throw new SalesforceResponseException(String.format("Can't read to json pointer \"%s\" to %s: %s",
                        m_jsonPointer, m_salesforceFieldType.getKNIMEType().toPrettyString(), ex.getMessage()), ex);
                }
            } else {
                return new MissingCell(
                    "Could not read result from response (pointer \"" + m_jsonPointer + "\" did not match");
            }
        }

    }

}

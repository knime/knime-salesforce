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
package org.knime.salesforce.rest.soql;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import org.knime.base.util.flowvariable.FlowVariableProvider;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.json.JSONCellFactory;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.rest.SalesforceResponseException;
import org.knime.salesforce.soql.SalesforceSOQLNodeSettings;

/**
 * Runs the SOQL and returns it in
 * {@linkplain org.knime.salesforce.soql.SalesforceSOQLNodeSettings.SOQLOutputRepresentation#RECORDS record} format.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public class RecordsOutputSOQLExecutor extends RawOutputSOQLExecutor {


    /**
     * @param authentication
     * @param settings
     * @param flowVarProvider
     * @throws InvalidSettingsException
     */
    public RecordsOutputSOQLExecutor(final SalesforceAuthentication authentication,
        final SalesforceSOQLNodeSettings settings, final FlowVariableProvider flowVarProvider)
        throws InvalidSettingsException {
        super(authentication, settings, flowVarProvider);
    }

    @Override
    public
        BufferedDataTable execute(final ExecutionContext context) throws SalesforceResponseException, CanceledExecutionException {
        BufferedDataContainer container = context.createDataContainer(createFixedOutputSpec());
        context.setMessage("Invoking Salesforce REST API");
        JsonStructure nextResults = execute();
        String sizeAsString = String.format("(%s total records)",
            getTotalSize().isPresent() ? Integer.toString(getTotalSize().getAsInt()) : "unknown number of ");
        if (getSettings().isOutputAsCount()) {
            JsonObject sizeObject = Json.createObjectBuilder().add("totalSize", getTotalSize().orElseThrow(
                () -> new SalesforceResponseException("No 'totalSize' key in Salesforce API response"))).build();
            container.addRowToTable(
                new DefaultRow(RowKey.createRowKey(0L), JSONCellFactory.create(sizeObject)));
        } else {
            long rowIndex = 0L;
            long chunkIndex = 0L;
            do {
                context.setMessage("Processing result set " + chunkIndex + sizeAsString);
                for (JsonStructure split : splitJsonStructureByRecords(nextResults)) {
                    context.checkCanceled();
                    container.addRowToTable(
                        new DefaultRow(RowKey.createRowKey(rowIndex), JSONCellFactory.create(split)));
                    rowIndex++;
                }
                chunkIndex++;
                context.setMessage("Invoking Salesforce REST API for next chunk (index " + chunkIndex + ")");
            } while ((nextResults = readNext().orElse(null)) != null);
        }
        container.close();
        return container.getTable();
    }

}

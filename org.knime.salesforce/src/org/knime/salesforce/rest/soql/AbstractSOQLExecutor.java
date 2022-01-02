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
 *   Jul 2, 2020 (wiswedel): created
 */
package org.knime.salesforce.rest.soql;

import java.io.StringReader;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonPointer;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.SalesforceResponseException;

/**
 * Used by 'Salesforce SOQL' to run the query. The class hierarchy is mostly due to different output type
 * representations (as per {@link org.knime.salesforce.soql.SalesforceSOQLNodeSettings.SOQLOutputRepresentation}).
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public abstract class AbstractSOQLExecutor {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(AbstractSOQLExecutor.class);

    private final SalesforceAuthentication m_authentication;
    private final String m_soql;

    /** JSON response from Salesforce starts with...
     * {
     *   "totalSize" : 2529,
     *   ...
     */
    private OptionalInt m_totalSize = OptionalInt.empty();

    /** Response may contain
     *   "nextRecordsUrl" : "/services/data/v49.0/query/01g3O000007dNAOQA2-2000",
     * Field is null if there is no more data to query or the statements hasn't been executed at all.
     */
    private Optional<String> m_nextRecordsUrlString = Optional.empty();

    /**
     * @param authentication
     * @param soql
     */
    protected AbstractSOQLExecutor(final SalesforceAuthentication authentication, final String soql) {
        m_authentication = CheckUtils.checkArgumentNotNull(authentication);
        m_soql = CheckUtils.checkArgumentNotNull(soql);
    }

    /** Determine the spec of the output table. This is possible prior the query if the output is a single (JSON) column
     * (which currently is always the case).
     * @return That spec.
     */
    public abstract Optional<DataTableSpec> createOutputSpec();

    /** Called by node itself to run the query/queries and fill the output.
     * @param context ...
     * @return a non-null table
     * @throws SalesforceResponseException all sorts of problems.
     * @throws CanceledExecutionException Cancelation.
     */
    public abstract BufferedDataTable execute(final ExecutionContext context)
        throws SalesforceResponseException, CanceledExecutionException;

    /**
     * Performs the query and returns the response.
     *
     * @return The result
     * @throws SalesforceResponseException if the result set does not comply with the schema etc
     */
    protected JsonStructure execute() throws SalesforceResponseException {
        var uri = m_authentication.uriBuilder() //
                .path(SalesforceRESTUtil.QUERY_PATH) //
                .queryParam("q", "{soql}") // need to use templates for proper encoding, see AP-17072 and
                .resolveTemplate("soql", m_soql) // https://issues.apache.org/jira/browse/CXF-8553
                .build();
        var uriAsString = uri.toString();
        LOGGER.debugWithFormat("Executing SOQL - %s",uriAsString,
            StringUtils.substring(uriAsString, 0, StringUtils.indexOf(uriAsString, "q=") + 20) + "...");
        String body;
        try (var response = SalesforceRESTUtil.doGet(uri, m_authentication, true)) {
            if (response.getStatusInfo().getFamily() != Status.Family.SUCCESSFUL) {
                Optional<String> errorOpt = SalesforceRESTUtil.readErrorFromResponseBody(response);
                String error = errorOpt.orElse(response.getStatusInfo().getReasonPhrase());
                throw new SalesforceResponseException(String.format("Status: %d -- %s", response.getStatus(), error));
            }
            body = response.readEntity(String.class);
        }

        JsonStructure jsonStructure;
        try (var reader = new StringReader(body); var jsonReader = Json.createReader(reader)) {
            jsonStructure = jsonReader.read();
        }
        m_totalSize = readTotalSize(jsonStructure);
        m_totalSize.ifPresent(i -> LOGGER.debugWithFormat("Query return %d results", i));
        m_nextRecordsUrlString = readNextRecordsUrlString(jsonStructure);
        return jsonStructure;
    }

    /**
     * @return the totalSize as read from the first request/response. An empty object if not called yet or the response
     * did not contain the field (which I think never happens).
     */
    protected OptionalInt getTotalSize() {
        return m_totalSize;
    }

    /** Runs the subsequent queries as per 'nextRecordsUrl'.
     * @return the next piece of data
     * @throws SalesforceResponseException ... */
    public Optional<JsonStructure> readNext() throws SalesforceResponseException {
        if (!m_nextRecordsUrlString.isPresent()) {
            return Optional.empty();
        }
        URI uri = m_authentication.uriBuilder().path(m_nextRecordsUrlString.get()).build();
        LOGGER.debugWithFormat("Reading next result set (%s)", m_nextRecordsUrlString.get());
        Response response = SalesforceRESTUtil.doGet(uri, m_authentication, true);
        if (response.getStatusInfo().getFamily() != Status.Family.SUCCESSFUL) {
            String errorBody  = response.readEntity(String.class);
            JsonStructure jsonError = SalesforceRESTUtil.readAsJsonStructure(errorBody);
            JsonValue message = jsonError.getValue("/0/message");
            throw new SalesforceResponseException(
                String.format("Status: %d -- %s", response.getStatus(), ((JsonString)message).getString()));
        }
        String body = response.readEntity(String.class);

        JsonStructure jsonStructure;
        try (StringReader reader = new StringReader(body); JsonReader jsonReader = Json.createReader(reader)) {
            jsonStructure = jsonReader.read();
        }
        m_nextRecordsUrlString = readNextRecordsUrlString(jsonStructure);
        return Optional.of(jsonStructure);
    }


    private static OptionalInt readTotalSize(final JsonStructure response) {
        JsonPointer sizePointer = Json.createPointer("/totalSize");
        if (sizePointer.containsValue(response)) {
            JsonValue value = sizePointer.getValue(response);
            if (value.getValueType() == ValueType.NUMBER) {
                return OptionalInt.of(((JsonNumber)value).intValue());
            }
        }
        return OptionalInt.empty();
    }

    private static Optional<String> readNextRecordsUrlString(final JsonStructure response)
        throws SalesforceResponseException {
        String nextRecordsUrlFieldName = "nextRecordsUrl";
        JsonPointer urlPointer = Json.createPointer("/" + nextRecordsUrlFieldName);
        if (urlPointer.containsValue(response)) {
            JsonValue value = urlPointer.getValue(response);
            if (value.getValueType() != ValueType.STRING) {
                throw new SalesforceResponseException(
                    String.format("SOQL Response contains a field '%s' but it's not of type %s but %s",
                        nextRecordsUrlFieldName, ValueType.STRING, value.getValueType()));
            }
            return Optional.of(((JsonString)value).getString());
        }
        return Optional.empty();
    }

    protected static JsonStructure[] splitJsonStructureByRecords(final JsonStructure response) {
        JsonPointer recordsPointer = Json.createPointer("/records");
        if (recordsPointer.containsValue(response)) {
            JsonValue value = recordsPointer.getValue(response);
            if (value.getValueType() == ValueType.ARRAY) {
                JsonArray array = (JsonArray)value;
                return array.stream().map(JsonStructure.class::cast).toArray(JsonStructure[]::new);
            }
        }
        return new JsonStructure[] {response};
    }

}

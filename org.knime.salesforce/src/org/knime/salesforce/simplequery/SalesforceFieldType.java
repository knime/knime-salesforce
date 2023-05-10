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
 *   Jul 26, 2020 (wiswedel): created
 */
package org.knime.salesforce.simplequery;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.blob.BinaryObjectCellFactory;
import org.knime.core.data.blob.BinaryObjectDataCell;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.json.JSONCellFactory;
import org.knime.core.data.time.localdate.LocalDateCellFactory;
import org.knime.core.data.time.localtime.LocalTimeCellFactory;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.rest.SalesforceResponseException;

import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

/**
 * Represents and maps possible types returned by the Salesforce API to KNIME cell implementations.
 *
 * https://developer.salesforce.com/docs/atlas.en-us.224.0.api.meta/api/primitive_data_types.htm#topic-title
 * https://developer.salesforce.com/docs/atlas.en-us.224.0.api.meta/api/field_types.htm
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
enum SalesforceFieldType {

    STRING(StringCell.TYPE, SalesforceFieldType::jsonToString,
        "string", "phone", "email", "url", "id", "picklist", "multipicklist", "reference", "textarea"),

    BOOLEAN(BooleanCell.TYPE, SalesforceFieldType::jsonToBoolean, "boolean"),

    INTEGER(IntCell.TYPE, SalesforceFieldType::jsonToInt, "int"),

    DOUBLE(DoubleCell.TYPE, SalesforceFieldType::jsonToDouble, "double", "currency", "percent"),

    DATETIME(ZonedDateTimeCellFactory.TYPE, SalesforceFieldType::jsonToZonedDateTime, "datetime"),

    LOCALTIME(LocalTimeCellFactory.TYPE, SalesforceFieldType::jsonToLocalTime, "time"),

    DATE(LocalDateCellFactory.TYPE, SalesforceFieldType::jsonToLocalDate, "date"),

    ADDRESS(JSONCellFactory.TYPE, JSONCellFactory::create, "address"),

    BASE64(BinaryObjectDataCell.TYPE, null, "base64") {

        @Override
        CellCreator newCellCreator(final ExecutionContext ctx) {
            return json -> jsonToBinary(new BinaryObjectCellFactory(ctx), json);
        }

    };

    private final DataType m_knimeType;
    private final String[] m_identifiersInSF;
    private final CellCreator m_jsonToCellFunction;

    SalesforceFieldType(final DataType knimeType, final CellCreator jsonToCellFunction,
        final String... identifiersInSF) {
        m_identifiersInSF = identifiersInSF;
        m_knimeType = knimeType;
        m_jsonToCellFunction = jsonToCellFunction;
    }

    /**
     * @return the knimeType
     */
    DataType getKNIMEType() {
        return m_knimeType;
    }

    /**
     * A creator used to create the cells for a single column.
     * @param ctx used for file store backed cell types (binary)
     * @return the cell creator.
     */
    CellCreator newCellCreator(final ExecutionContext ctx) {
        return m_jsonToCellFunction;
    }

    static SalesforceFieldType readType(final String s) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotEmpty(s), "Field type must not be null or empty");
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingsException("Invalid field type: " + s);
        }
    }

    static Optional<SalesforceFieldType> fromIdentifierInSalesforce(final String identifierInSF) {
        return Arrays.stream(values()) //
            .filter(s -> ArrayUtils.contains(s.m_identifiersInSF, identifierInSF)) //
            .findFirst();
    }

    private static DataCell jsonToBinary(final BinaryObjectCellFactory cellFactory, final JsonValue json) // NOSONAR
        throws SalesforceResponseException {
        if (json.getValueType() == ValueType.STRING) {
            String base64 = ((JsonString)json).getString();
            byte[] decode = java.util.Base64.getDecoder().decode(base64);
            try {
                return cellFactory.create(decode);
            } catch (IOException ex) {
                throw new SalesforceResponseException("Creating file store failed: " + ex.getMessage(), ex);
            }
        } else {
            throw new SalesforceResponseException(
                String.format("not a number json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToLocalDate(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.STRING) {
            return LocalDateCellFactory.create(((JsonString)json).getString());
        } else {
            throw new SalesforceResponseException(
                String.format("not a string json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToLocalTime(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.STRING) {
            return LocalTimeCellFactory.create(((JsonString)json).getString());
        } else {
            throw new SalesforceResponseException(
                String.format("not a string json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToZonedDateTime(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.STRING) {
            String dtAsString = ((JsonString)json).getString();
            return ZonedDateTimeCellFactory.create(dtAsString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"));
        } else {
            throw new SalesforceResponseException(
                String.format("not a string json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToInt(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.NUMBER) {
            return new IntCell(((JsonNumber)json).intValueExact());
        } else {
            throw new SalesforceResponseException(
                String.format("not a number json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToBoolean(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.FALSE) {
            return BooleanCell.FALSE;
        } else if (json.getValueType() == ValueType.TRUE) {
            return BooleanCell.TRUE;
        } else {
            throw new SalesforceResponseException(
                String.format("not a boolean json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToString(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.STRING) {
            return new StringCell(((JsonString)json).getString());
        } else {
            throw new SalesforceResponseException(
                String.format("not a string json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    private static DataCell jsonToDouble(final JsonValue json) throws SalesforceResponseException {
        if (json.getValueType() == ValueType.NUMBER) {
            return new DoubleCell(((JsonNumber)json).doubleValue());
        } else {
            throw new SalesforceResponseException(
                String.format("not a number json value but %s: %s", json.getValueType(), json.toString()));
        }
    }

    /** The lambda that does the actual mapping. */
    @FunctionalInterface
    interface CellCreator {

        DataCell toCell(final JsonValue value) throws SalesforceResponseException;

    }

}
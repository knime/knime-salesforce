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
 *   Oct 9, 2019 (benjamin): created
 */
package org.knime.salesforce.rest.gsonbindings;

import java.util.Arrays;

/**
 * An error reported by the Power BI REST API.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 */
public final class ErrorResponse {

    private final Error error;

    private ErrorResponse(final Error e) {
        error = e;
    }

    @Override
    public String toString() {
        return getError().toString();
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    /**
     * An error message of Salesforce
     *
     * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
     */
    public static class Error {
        private final String code;

        private final String message;

        private final Detail[] details;

        private Error(final String c, final String m, final Detail[] d) {
            code = c;
            message = m;
            details = d;
        }

        @Override
        public String toString() {
            return "Error: " + getMessage() + ", Code: " + getCode() + //
                (getDetails() != null ? (", Details: " + Arrays.toString(getDetails())) : "");
        }

        /**
         * Returns the error code.
         *
         * @return the error code
         */
        public String getCode() {
            return code;
        }

        /**
         * Returns the message
         *
         * @return the message
         */
        public String getMessage() {
            // Replace html tags
            return message.replaceAll("\\<.*?\\>", ""); // NOSONAR
        }

        /**
         * Returns the details.
         *
         * @return the details
         */
        public Detail[] getDetails() {
            return details;
        }
    }

    /**
     * Details of an error message of Salesforce
     *
     * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
     */
    public static class Detail {
        private final String message;

        private final String target;

        private Detail(final String m, final String t) {
            message = m;
            target = t;
        }

        @Override
        public String toString() {
            return getMessage() + " (Target: " + getTarget() + ")";
        }

        /**
         * Returns the message.
         *
         * @return the message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns the target.
         *
         * @return the target
         */
        public String getTarget() {
            return target;
        }
    }
}

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
 *   Dec 9, 2023 (wiswedel): created
 */
package org.knime.salesforce.rest;

import org.knime.core.node.config.base.ConfigBaseRO;
import org.knime.core.node.config.base.ConfigBaseWO;

/**
 * Connect and read timeout in a record, incl backward compatible read/write operations.
 *
 * @author Bernd Wiswedel, KNIME
 */
public final class Timeouts {

    static final int DEFAULT_CONNECTION_TIMEOUT = 30;

    static final int DEFAULT_READ_TIMEOUT = 60;

    private static final String CFG_READ_TIMEOUT = "read_timeout";

    private static final String CFG_CONNECT_TIMEOUT = "connect_timeout";


    private final int m_connectionTimeoutS;

    private final int m_readTimeoutS;

    /**
     * @param connectionTimeoutS Connection timeout in seconds
     * @param readTimeoutS Read timeout in seconds
     */
    public Timeouts(final int connectionTimeoutS, final int readTimeoutS) {
        m_connectionTimeoutS = connectionTimeoutS;
        m_readTimeoutS = readTimeoutS;
    }

    /**
     * @return connection timeout in seconds
     */
    public long getConnectionTimeoutS() {
        return m_connectionTimeoutS;
    }

    /**
     * @return read timeout in seconds
     */
    public long getReadTimeoutS() {
        return m_readTimeoutS;
    }

    /**
     * Save fields into the root of the argument.
     * @param settings to save to
     */
    public void save(final ConfigBaseWO settings) {
        settings.addInt(CFG_CONNECT_TIMEOUT, m_connectionTimeoutS);
        settings.addInt(CFG_READ_TIMEOUT, m_readTimeoutS);
    }

    /**
     * Load instance, counterpart to {@link #save(ConfigBaseWO)}.
     *
     * @param settings to load from.
     * @return A new instance, returning a default if settings are invalid (guarantees backward compatibility).
     */
    public static Timeouts read(final ConfigBaseRO settings) {
        final var connectTimeout = settings.getInt(CFG_CONNECT_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT); // added in 5.2.1
        final var readTimeout = settings.getInt(CFG_READ_TIMEOUT, DEFAULT_READ_TIMEOUT); // added in 5.2.1
        return new Timeouts(connectTimeout, readTimeout);
    }
}
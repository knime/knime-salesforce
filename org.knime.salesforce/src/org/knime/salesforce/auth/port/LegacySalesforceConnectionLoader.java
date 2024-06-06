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
 *   Jun 5, 2024 (bjoern): created
 */
package org.knime.salesforce.auth.port;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.knime.core.node.ModelContentRO;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;

/**
 * Utility class to transfer credentials restored during loadInternals() of the deprecated
 * Salesforce Authentication node to the saved port objects. Shouldn't be used for anything else,
 * especially not by new nodes.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class LegacySalesforceConnectionLoader {

    // lazy init
    private static Map<UUID, SalesforceAccessTokenCredential> legacyCache;

    private static final String KEY_SOURCE_NODE_INSTANCE_ID = "sourceNodeInstanceID";

    private LegacySalesforceConnectionLoader() {
    }

    /**
     * Adds the given credential to the legacy credential cache.
     *
     * @param cacheId
     * @param credential
     */
    public static synchronized void addToLegacyCache(final UUID cacheId,
        final SalesforceAccessTokenCredential credential) {

        ensureLegacyCache();
        legacyCache.put(cacheId, credential);
    }

    private static synchronized void ensureLegacyCache() {
        if (legacyCache == null) {
            legacyCache = new HashMap<>();
        }
    }

    static Optional<UUID> restoreLegacyCacheId(final ModelContentRO config) {
        return Optional.ofNullable(config.getString(KEY_SOURCE_NODE_INSTANCE_ID, null))//
                .map(UUID::fromString);
    }

    static synchronized Optional<SalesforceAccessTokenCredential>
        tryRestoreCredentialWithLegacyCacheId(final UUID legacyCacheId) {

        ensureLegacyCache();
        return Optional.ofNullable(legacyCache.remove(legacyCacheId));
    }
}

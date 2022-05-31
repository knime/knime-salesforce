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
 *   11.11.2019 (David Kolb, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.salesforce.connect;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.util.Pair;
import org.knime.salesforce.auth.SalesforceAuthentication;

/**
 * Singleton HashMap to store {@link SalesforceAuthentication} objects in memory.
 *
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class InMemoryAuthenticationStore {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(InMemoryAuthenticationStore.class);

    private static InMemoryAuthenticationStore globalInstance;
    private static InMemoryAuthenticationStore dialogToNodeExchangeInstance;

    private Map<Pair<UUID, NodeID>, SalesforceAuthentication> m_dataStore = Collections.synchronizedMap(new HashMap<>());

    private InMemoryAuthenticationStore() {
    }

    /**
     * Get the InMemoryCredentialStore instance used to carry auths objects from node to node.
     *
     * @return That instance
     */
    public synchronized static InMemoryAuthenticationStore getGlobalInstance() {
        if (globalInstance == null) {
            globalInstance = new InMemoryAuthenticationStore();
        }
        return globalInstance;
    }

    /**
     * @return the m_dialogToNodeExchangeInstance
     */
    public synchronized static InMemoryAuthenticationStore getDialogToNodeExchangeInstance() {
        if (dialogToNodeExchangeInstance == null) {
            dialogToNodeExchangeInstance = new InMemoryAuthenticationStore();
        }
        return dialogToNodeExchangeInstance;
    }

    /**
     * Store the specified auth object under a given key.
     *
     * @param key The key to use.
     * @param value the auth object to cache
     */
    public void put(final UUID key, final SalesforceAuthentication value) {
        var id = NodeContext.getContext().getNodeContainer().getID();
        if (value == null) {
            remove(key);
        } else if (m_dataStore.put(Pair.create(key, id), value) == null) {
            LOGGER.debugWithFormat("Added authentication object with ID %s to %s instance (total count: %d)",
                key.toString(), getTypeForLogging(), m_dataStore.size());
        }
    }

    /**
     * Get the auth object associated with the specified key.
     *
     * @param key The key to get the value of.
     * @return The auth for the specified key
     */
    public Optional<SalesforceAuthentication> get(final UUID key) {
        var id = NodeContext.getContext().getNodeContainer().getID();
        return Optional.ofNullable(m_dataStore.get(Pair.create(key, id)));
    }

    /**
     * Remove the auth with specified key.
     *
     * @param key The key to remove the value of.
     */
    public void remove(final UUID key) {
        var id = NodeContext.getContext().getNodeContainer().getID();
        if (m_dataStore.remove(Pair.create(key, id)) != null) {
            LOGGER.debugWithFormat("Removed authentication object with ID %s from %s instance (total count: %d)",
                key.toString(), getTypeForLogging(), m_dataStore.size());
        }
    }

    private String getTypeForLogging() {
        if (this == dialogToNodeExchangeInstance) {
            return "dialog exchange";
        } else if (this == globalInstance) {
            return "global";
        } else {
            return "unknown";
        }
    }
}

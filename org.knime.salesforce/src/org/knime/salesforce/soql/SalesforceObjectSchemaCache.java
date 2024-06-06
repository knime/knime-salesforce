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
 *   Jul 25, 2020 (wiswedel): created
 */
package org.knime.salesforce.soql;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.knime.core.node.NodeLogger;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.Timeouts;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;

/**
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceObjectSchemaCache {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceObjectSchemaCache.class);

    public static final SObject NO_AUTH_CONTENT =
            SObject.of("No Authentication Object available from Node Input", "Not connected to salesforce.com");
    public static final SObject FETCHING_CONTENT =
            SObject.of("Retrieving content from salesforce.com", "Fetching Content...");
    public static final SObject FAILED_CONTENT =
            SObject.of("Error reading from salesforce.com, check log files for details", "<Failed to fetch list>");
    public static final SObject PROTOTYPE_LENGTH_CONTENT = SObject.of("", "Account Account Account");

    public static final Field FETCHING_FIELD = Field.of("do_not_use", "Fetching fields...", "string");

    public static final Field FAILED_FIELD = Field.of("do_not_use", "Failed to fetch fields", "string");

    private final Map<SObject, Field[]> m_sObjectFieldCache;

    private FetchSObjectsSwingWorker m_fetchSObjectsSwingWorker;

    private FetchFieldsSwingWorker m_fetchFieldsSwingWorker;

    /**
     *
     */
    public SalesforceObjectSchemaCache() {
        m_sObjectFieldCache = new HashMap<>();
        m_sObjectFieldCache.put(FAILED_CONTENT, new Field[] {FAILED_FIELD});
        m_sObjectFieldCache.put(FETCHING_CONTENT, new Field[] {});
        m_sObjectFieldCache.put(NO_AUTH_CONTENT, new Field[] {});

    }

    /**
     * @param cred TODO
     * @param timeouts TODO
     * @param afterCompletionRunnable TODO
     *
     */
    public void executeNewSObjectsSwingWorker(final SalesforceAccessTokenCredential cred,
        final Timeouts timeouts, final Runnable afterCompletionRunnable) {
        m_fetchSObjectsSwingWorker = new FetchSObjectsSwingWorker(cred, timeouts, afterCompletionRunnable);
        m_fetchSObjectsSwingWorker.execute();
    }

    /**
     * @param cred TODO
     * @param timeouts TODO
     * @param afterCompletionConsumer TODO
     *
     */
    public void executeNewFieldsSwingWorker(final SalesforceAccessTokenCredential cred, final Timeouts timeouts,
        final SObject sobject, final Consumer<SObject> afterCompletionConsumer) {
        m_fetchFieldsSwingWorker = new FetchFieldsSwingWorker(cred, timeouts, sobject, afterCompletionConsumer);
        m_fetchFieldsSwingWorker.execute();
    }

    public void cancelFetchFieldsSwingWorker() {
        if (m_fetchFieldsSwingWorker != null && !m_fetchFieldsSwingWorker.isDone()) {
            m_fetchFieldsSwingWorker.cancel(true);
        }
        m_fetchFieldsSwingWorker = null;
    }

    public void cancelFetchSObjectsSwingWorker() {
        if (m_fetchSObjectsSwingWorker != null && !m_fetchSObjectsSwingWorker.isDone()) {
            m_fetchSObjectsSwingWorker.cancel(true);
        }
        m_fetchSObjectsSwingWorker = null;
    }

    public void onClose() {
        cancelFetchFieldsSwingWorker();
        cancelFetchSObjectsSwingWorker();
    }

    /**
     * @return the sObjectFieldCache
     */
    public Map<SObject, Field[]> getsObjectFieldCache() {
        assert SwingUtilities.isEventDispatchThread() : "not called in EDT";
        return Collections.unmodifiableMap(m_sObjectFieldCache);
    }

    class FetchFieldsSwingWorker extends SwingWorkerWithContext<Field[], Void> {

        private final SalesforceAccessTokenCredential m_credential;
        private final SObject m_sObject;
        private final Consumer<SObject> m_afterCompletionConsumer;
        private final Timeouts m_timeouts;

        FetchFieldsSwingWorker(final SalesforceAccessTokenCredential cred, final Timeouts timeouts,
            final SObject sObject, final Consumer<SObject> afterCompletionConsumer) {
            m_credential = cred;
            m_timeouts = timeouts;
            m_sObject = sObject;
            m_afterCompletionConsumer = afterCompletionConsumer;
        }

        @Override
        protected Field[] doInBackgroundWithContext() throws Exception {
            Thread.sleep(500);
            return SalesforceRESTUtil.getSObjectFields(m_sObject, m_credential, m_timeouts);
        }

        @Override
        protected void doneWithContext() {
            Field[] fields = new Field[] {FETCHING_FIELD};
            SObject selectedObject = FAILED_CONTENT;
            try {
                fields = get();
                Arrays.sort(fields, (a, b) -> a.getLabel().compareTo(b.getLabel()));
                selectedObject = m_sObject;
                m_sObjectFieldCache.put(m_sObject, fields);
            } catch (InterruptedException | CancellationException ex) {
            } catch (ExecutionException ex) {
                LOGGER.error(String.format("Unable to fetch fields for object \"%s\" from salesforce (%s): %s",//
                    m_sObject.getName(), //
                    m_credential.getSalesforceInstanceUrl().toString(),//
                    ex.getCause().getMessage()),//
                    ex.getCause());
            }
            m_afterCompletionConsumer.accept(selectedObject);
        }
    }

    private final class FetchSObjectsSwingWorker extends SwingWorkerWithContext<SObject[], Void> {

        private final SalesforceAccessTokenCredential m_credential;
        private final Timeouts m_timeouts;
        private final Runnable m_afterCompletionRunnable;

        FetchSObjectsSwingWorker(final SalesforceAccessTokenCredential cred, final Timeouts timeouts,
            final Runnable afterCompletionRunnable) {
            m_credential = cred;
            m_timeouts = timeouts;
            m_afterCompletionRunnable = afterCompletionRunnable;
        }

        @Override
        protected SObject[] doInBackgroundWithContext() throws Exception {
            Thread.sleep(500);
            return SalesforceRESTUtil.getSObjects(m_credential, m_timeouts);
        }

        @Override
        protected void doneWithContext() {
            SObject[] sObjects = new SObject[] {FAILED_CONTENT};
            try {
                sObjects = get();
            } catch (InterruptedException | CancellationException ex) {
            } catch (ExecutionException ex) {
                LOGGER.error(String.format("Unable to retrieve 'SObjects' from Salesforce (%s): %s",
                    m_credential.getSalesforceInstanceUrl().toString(),//
                    ex.getCause().getMessage()),//
                    ex.getCause());
            }
            m_sObjectFieldCache.clear();
            Arrays.stream(sObjects).forEach(s -> m_sObjectFieldCache.put(s, null));
            m_afterCompletionRunnable.run();
        }
    }
}

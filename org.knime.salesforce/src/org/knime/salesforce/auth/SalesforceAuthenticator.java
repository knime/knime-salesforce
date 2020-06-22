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
 *   Oct 8, 2019 (benjamin): created
 */
package org.knime.salesforce.auth;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.knime.core.node.NodeLogger;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.salesforce.auth.SalesforceAuthenticationUtils.AuthenticationException;

/**
 * Microsoft Active Directory authenticator implementation.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 */
// TODO: Make abstract version (or default with generic)
public class SalesforceAuthenticator implements Authenticator<SalesforceAuthentication> {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceAuthentication.class);

    private final Set<AuthenticatorListener> m_listeners;

    private SwingWorkerWithContext<SalesforceAuthentication, Void> m_swingWorker;

    private SalesforceAuthentication m_auth;

    private AuthenticatorState m_state;

    private String m_error;

    /**
     * Create a new authenticator.
     */
    public SalesforceAuthenticator() {
        m_listeners = new HashSet<>();
        m_state = AuthenticatorState.NOT_AUTHENTICATED;
        m_auth = null;
    }

    @Override
    public void authenticate() {
        m_swingWorker = new SwingWorkerWithContext<SalesforceAuthentication, Void>() {

            private Future<SalesforceAuthentication> m_futureAuth;

            @Override
            protected SalesforceAuthentication doInBackgroundWithContext() throws Exception {
                m_futureAuth = SalesforceAuthenticationUtils.authenticate();
                return m_futureAuth.get();
            }

            @Override
            protected void doneWithContext() {
                try {
                    if (isCancelled()) {
                        m_futureAuth.cancel(true);
                        updateState(AuthenticatorState.CANCELED);
                        return;
                    }
                    // Get the resulting authentication
                    m_auth = get();
                    updateState(AuthenticatorState.AUTHENTICATED);
                } catch (final InterruptedException e) {
                    final Throwable rootCause = getRootException(e);
                    LOGGER.warn(rootCause.getMessage(), e);
                    m_error = "Authentication interrupted.";
                    updateState(AuthenticatorState.FAILED);
                } catch (final ExecutionException e) {
                    final Throwable rootCause = getRootException(e);
                    LOGGER.warn(rootCause.getMessage(), e);
                    m_error = rootCause.getMessage();
                    updateState(AuthenticatorState.FAILED);
                } finally {
                    m_swingWorker = null;
                }
            }
        };
        m_auth = null;
        m_swingWorker.execute();
        updateState(AuthenticatorState.AUTHENTICATION_IN_PROGRESS);
    }

    @Override
    public void cancel() {
        if (m_swingWorker != null) {
            m_swingWorker.cancel(true);
        }
    }

    @Override
    public AuthenticatorState getState() {
        return m_state;
    }

    @Override
    public void clearAuthentication() {
        m_auth = null;
        updateState(AuthenticatorState.NOT_AUTHENTICATED);
    }

    @Override
    public String getErrorDescription() {
        return m_error;
    }

    @Override
    public synchronized void addListener(final AuthenticatorListener listener) {
        m_listeners.add(listener);
    }

    @Override
    public SalesforceAuthentication getAuthentication() {
        return m_auth;
    }

    /**
     * Set the authentication and update the state. Can be used if the authentication is loaded from the node settings.
     *
     * @param auth the authentication. Can be <code>null</code>
     */
    public void setAuthentication(final SalesforceAuthentication auth) {
        m_auth = auth;
        if (auth != null) {
            updateState(AuthenticatorState.AUTHENTICATED);
        } else {
            updateState(AuthenticatorState.NOT_AUTHENTICATED);
        }
    }

    private synchronized void updateState(final AuthenticatorState state) {
        m_state = state;

        // Notify the listeners
        for (final AuthenticatorListener l : m_listeners) {
            l.stateChanged(state);
        }
    }

    /** Return the root cause of the throwable t or the first {@link AuthenticationException}. */
    private static Throwable getRootException(final Throwable t) {
        Throwable e = t;
        while (!(e instanceof AuthenticationException) //
            && e.getCause() != null //
            && e.getCause() != e) {
            e = e.getCause();
        }
        return e;
    }
}

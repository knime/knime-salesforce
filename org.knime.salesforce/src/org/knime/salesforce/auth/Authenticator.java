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

import java.awt.Color;
import java.util.EventListener;

/**
 * An authenticator to be used in a user interface. The user interface can start and cancel the authentication flow and
 * get the state.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 * @param <T> The type of the authentication object managed by this Authenticator.
 */
public interface Authenticator<T> {

    /**
     * Start the authentication flow.
     */
    void authenticate();

    /**
     * Cancel a running authentication flow.
     */
    void cancel();

    /**
     * @return the state of the authentication. To be shown to the user.
     */
    AuthenticatorState getState();

    /**
     * Clear the current authentication
     */
    void clearAuthentication();

    /**
     * @return a user-friendly description of what went wrong it the state is {@link AuthenticatorState#FAILED}.
     */
    String getErrorDescription();

    /**
     * Add a listener which gets notified on state changes.
     *
     * @param listener the listener
     */
    void addListener(AuthenticatorListener listener);

    /**
     * A listener for authentication state changes.
     */
    @FunctionalInterface
    public interface AuthenticatorListener extends EventListener {

        /**
         * React to a authentication state change. The listeners should not do heavy work here.
         *
         * @param state the new state
         */
        void stateChanged(final AuthenticatorState state);
    }

    /**
     * Get the authentication if it succeeded.
     *
     * @return the authentication or <code>null</code>
     */
    T getAuthentication();

    /**
     * A state of an {@link Authenticator}. Use {@link #toString()} to get a user-friendly description of the state.
     */
    public enum AuthenticatorState {
            /** User is not authenticated */
            NOT_AUTHENTICATED("Not Authenticated", Color.BLACK),

            /** User is authenticated */
            AUTHENTICATED("Authenticated", Color.GREEN),

            /** Authentication is currently in progress */
            AUTHENTICATION_IN_PROGRESS("Authenticating...", Color.BLACK),

            /** Authentication was canceled */
            CANCELED("Canceled", Color.ORANGE),

            /** Authentication failed because of any reason */
            FAILED("Authentication Failed", Color.RED);

        private final String m_text;

        private final Color m_displayColor;

        private AuthenticatorState(final String text, final Color displayColor) {
            m_text = text;
            m_displayColor = displayColor;
        }

        @Override
        public String toString() {
            return m_text;
        }

        /**
         * Get the color this authenticator state should be displayed as.
         *
         * @return Color for displaying.
         */
        public Color getDisplayColor() {
            return m_displayColor;
        }
    }
}

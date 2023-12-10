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
 *   Dec 26, 2019 (wiswedel): created
 */
package org.knime.salesforce.auth.port;

import static org.knime.core.node.util.CheckUtils.checkSettingNotNull;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.swing.JComponent;
import javax.swing.JEditorPane;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.port.AbstractSimplePortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.connect.InMemoryAuthenticationStore;
import org.knime.salesforce.rest.Timeouts;

/**
 * Spec wrapping and identifier to a {@link SalesforceAuthentication}. Keeps only and identifier as the object itself
 * is kept in {@link InMemoryAuthenticationStore}.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceConnectionPortObjectSpec extends AbstractSimplePortObjectSpec {

    private UUID m_nodeInstanceID;

    /** Timeouts, added as part of AP-21579. */
    private Timeouts m_timeouts;

    /** Serializer as required by extension point. */
    public static final class Serializer extends
    AbstractSimplePortObjectSpecSerializer<SalesforceConnectionPortObjectSpec> { }

    /** @param nodeInstanceID Non-null identifier. */
    public SalesforceConnectionPortObjectSpec(final UUID nodeInstanceID, final Timeouts timeouts) {
        m_nodeInstanceID = CheckUtils.checkArgumentNotNull(nodeInstanceID);
        m_timeouts = CheckUtils.checkArgumentNotNull(timeouts);
    }

    /**
     * API method, do not use.
     */
    public SalesforceConnectionPortObjectSpec() {
    }

    @Override
    protected void save(final ModelContentWO model) {
        model.addString("sourceNodeInstanceID", m_nodeInstanceID.toString());
        m_timeouts.save(model);
    }

    @Override
    protected void load(final ModelContentRO model) throws InvalidSettingsException {
        m_nodeInstanceID = UUID.fromString(checkSettingNotNull(model.getString("sourceNodeInstanceID"),
            "Instance ID can't be null"));
        m_timeouts = Timeouts.read(model); // added in 5.2.1 (and other), see field comment
    }

    /**
     * @return the authentication, if (still) in memory or an empty optional if no longer available.
     */
    public Optional<SalesforceAuthentication> getAuthentication() {
        return InMemoryAuthenticationStore.getGlobalInstance().get(m_nodeInstanceID);
    }

    /** Similiar to {@link #getAuthentication()} but throws an exception if not present.
     * @return ...
     * @throws InvalidSettingsException if not present
     */
    public SalesforceAuthentication getAuthenticationNoNull() throws InvalidSettingsException {
        return InMemoryAuthenticationStore.getGlobalInstance().get(m_nodeInstanceID).orElseThrow(
            () -> new InvalidSettingsException(SalesforceAuthentication.AUTH_NOT_CACHE_ERROR));
    }

    /**
     * @return the timeouts configured during construction.
     */
    public Timeouts getTimeouts() {
        return m_timeouts;
    }

    @Override
    public JComponent[] getViews() {
        Optional<SalesforceAuthentication> authentication = getAuthentication();
        JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        StringBuilder b = new StringBuilder();
        b.append("<html>\n");
        b.append("<head>\n");
        b.append("<style type=\"text/css\">\n");
        b.append("body {color:#333333;}");
        b.append("table {width: 100%;margin: 7px 0 7px 0;}");
        b.append("th {font-weight: bold;background-color: #aaccff;}");
        b.append("td,th {padding: 4px 5px; }");
        b.append(".numeric {text-align: right}");
        b.append(".odd {background-color:#ddeeff;}");
        b.append(".even {background-color:#ffffff;}");
        b.append("</style>\n");
        b.append("</head>\n");
        b.append("<body>\n");
        b.append("<h2>Salesforce OAuth2 token summary</h2>");
        if (authentication.isPresent()) {
            SalesforceAuthentication auth = authentication.get();
            b.append("<table>\n");
            b.append("<tr>");
            b.append("<th>Key</th>");
            b.append("<th>Value</th>");
            b.append("</tr>");

            b.append("<tr class=\"odd\">\n");
            b.append("<td>Access Token</td><td>").append(auth.getAccessToken().substring(0, 5)).append("...</td>\n");
            b.append("</tr>\n");
            b.append("<tr class=\"even\">\n");
            b.append("<td>Access Token Created</td><td>").append(auth.getAccessTokenCreatedWhen()
                .format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("</td>\n");
            b.append("</tr>\n");
            b.append("<tr class=\"odd\">\n");
            b.append("<td>Refresh Token</td><td>")
                .append(auth.getRefreshToken().map(r -> r.substring(0, 5) + "...").orElse("&lt;none&gt;"))
                .append("</td>\n");
            b.append("</tr>\n");
            b.append("<tr class=\"even\">\n");
            b.append("<td>Refresh Token Created</td><td>").append(auth.getRefreshTokenCreatedWhen()//
                .map(r -> r.format(DateTimeFormatter.RFC_1123_DATE_TIME))//
                .orElse("-")).append("</td>\n");
            b.append("</tr>\n");
            b.append("<tr class=\"odd\">\n");
            b.append("<td>Instance URL</td><td>").append(auth.getInstanceURLString()).append("</td>\n");
            b.append("</tr>\n");
            b.append("</table>\n");
        } else {
            b.append(SalesforceAuthentication.AUTH_NOT_CACHE_ERROR);
        }

        b.append("<h2>Connection Properties</h2>");
        b.append("<table>\n");
        b.append("<tr>");
        b.append("<th>Key</th>");
        b.append("<th>Value</th>");
        b.append("</tr>");

        b.append("<tr class=\"odd\">\n");
        b.append("<td>Connection Timeout[s]</td><td>").append(m_timeouts.connectionTimeoutS()).append("</td>\n");
        b.append("</tr>\n");
        b.append("<tr class=\"even\">\n");
        b.append("<td>Read Timeout[s]</td><td>").append(m_timeouts.readTimeoutS()).append("</td>\n");
        b.append("</tr>\n");
        b.append("</table>\n");

        b.append("</body>\n");
        b.append("</html>\n");
        pane.setText(b.toString());
        pane.revalidate();
        pane.setName("Salesforce Authentification");
        return new JComponent[]{pane};
    }

    @Override
    public String toString() {
        return Objects.toString(getAuthentication().orElse(null), "<No Auth object set>");
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_nodeInstanceID, m_timeouts);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SalesforceConnectionPortObjectSpec other = (SalesforceConnectionPortObjectSpec)obj;
        return Objects.equals(m_nodeInstanceID, other.m_nodeInstanceID) && Objects.equals(m_timeouts, other.m_timeouts);
    }

}
